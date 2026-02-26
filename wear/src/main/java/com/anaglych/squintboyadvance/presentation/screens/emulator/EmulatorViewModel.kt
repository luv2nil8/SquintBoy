package com.anaglych.squintboyadvance.presentation.screens.emulator

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import com.anaglych.squintboyadvance.core.AudioPlayer
import com.anaglych.squintboyadvance.core.EmulatorThread
import com.anaglych.squintboyadvance.core.MgbaEmulator
import com.anaglych.squintboyadvance.core.SaveStateManager
import com.anaglych.squintboyadvance.presentation.RomMetadataStore
import com.anaglych.squintboyadvance.presentation.SettingsRepository
import com.anaglych.squintboyadvance.shared.emulator.EmulatorState
import com.anaglych.squintboyadvance.shared.model.ButtonId
import com.anaglych.squintboyadvance.shared.model.GbColorPalette
import com.anaglych.squintboyadvance.shared.model.SystemType
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository.getInstance(application)
    private val metadataStore = RomMetadataStore.getInstance(application)

    private val _state = MutableStateFlow(EmulatorState.IDLE)
    val state: StateFlow<EmulatorState> = _state.asStateFlow()

    private val _romTitle = MutableStateFlow("")
    val romTitle: StateFlow<String> = _romTitle.asStateFlow()

    private val _frame = MutableStateFlow<ImageBitmap?>(null)
    val frame: StateFlow<ImageBitmap?> = _frame.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _systemType = MutableStateFlow<SystemType?>(null)
    val systemType: StateFlow<SystemType?> = _systemType.asStateFlow()

    private var emulator: MgbaEmulator? = null
    private var emulatorThread: EmulatorThread? = null
    private var audioPlayer: AudioPlayer? = null
    private var saveStateManager: SaveStateManager? = null
    private var audioEnabled = false

    // Reusable bitmap to avoid GC pressure
    private var renderBitmap: Bitmap? = null
    private var pixelBuffer: IntArray? = null

    // Play time tracking
    private var currentRomId: String? = null
    private var sessionStartTime: Long = 0L

    init {
        // Apply GB palette changes from the companion app immediately while a game is running.
        viewModelScope.launch {
            settingsRepo.settings
                .map { it.gbPaletteIndex }
                .distinctUntilChanged()
                .collect { index ->
                    if (_systemType.value == SystemType.GBA) return@collect
                    val palette = GbColorPalette.ALL.getOrNull(index) ?: return@collect
                    emulator?.setGbPalette(palette.mgbaOrder())
                }
        }
        // Apply audio volume changes live (e.g. from companion app or volume keys).
        viewModelScope.launch {
            settingsRepo.settings
                .map { it.audioVolume }
                .distinctUntilChanged()
                .collect { volume ->
                    audioPlayer?.setVolume(volume)
                }
        }
    }

    companion object {
        private const val OUTPUT_SAMPLE_RATE = 48000
    }

    fun loadRom(romId: String, romTitle: String) {
        if (_state.value == EmulatorState.RUNNING || _state.value == EmulatorState.LOADING) return

        val settings = settingsRepo.settings.value
        this.audioEnabled = settings.audioEnabled

        currentRomId = romId
        _romTitle.value = romTitle
        _systemType.value = SystemType.fromExtension(romId.substringAfterLast('.', ""))
        _state.value = EmulatorState.LOADING

        val context = getApplication<Application>()
        val romsDir = File(context.filesDir, "roms")
        val romFile = File(romsDir, romId)

        if (!romFile.exists()) {
            _errorMessage.value = "ROM file not found: $romId"
            _state.value = EmulatorState.ERROR
            return
        }

        val emu = MgbaEmulator()
        emulator = emu

        val saveDir = File(context.filesDir, "saves")
        saveDir.mkdirs()

        if (!emu.loadRom(romFile.absolutePath)) {
            _errorMessage.value = "Failed to load ROM"
            _state.value = EmulatorState.ERROR
            emu.destroy()
            emulator = null
            return
        }

        // Set save directory AFTER loadRom (core must exist)
        emu.setSaveDir(saveDir.absolutePath)

        // Initialize rendering resources
        val w = emu.width
        val h = emu.height
        renderBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        pixelBuffer = IntArray(w * h)

        // Initialize save manager (save states + SRAM backups)
        val stateDir = File(context.filesDir, "states")
        val romBaseName = romId.substringBeforeLast('.')

        // Explicitly load/create the SRAM save file — mGBA won't do this automatically
        // without ENABLE_DIRECTORIES. This opens a .sav VFile so SRAM writes persist.
        val savFile = File(saveDir, "$romBaseName.sav")
        emu.loadSaveFile(savFile.absolutePath)
        saveStateManager = SaveStateManager(stateDir, saveDir, romBaseName, emu)

        // Restore SRAM backup (if live .sav missing) then load newest valid save state
        saveStateManager?.restoreAll()

        // Set up audio with resampler
        if (audioEnabled) {
            val player = AudioPlayer(OUTPUT_SAMPLE_RATE)
            player.setVolume(settings.audioVolume)
            audioPlayer = player
            emu.initAudio(OUTPUT_SAMPLE_RATE)
            player.start()
        }

        // Apply GB color palette (GB/GBC only; no-op if GBA)
        if (_systemType.value != SystemType.GBA) {
            val palette = GbColorPalette.ALL.getOrNull(settings.gbPaletteIndex)
            if (palette != null) emu.setGbPalette(palette.mgbaOrder())
        }

        // Start tracking play time
        sessionStartTime = System.currentTimeMillis()

        // Start emulation thread
        _state.value = EmulatorState.RUNNING
        startEmulatorThread(emu)
    }

    private fun startEmulatorThread(emu: MgbaEmulator) {
        val settings = settingsRepo.settings.value
        emulatorThread = EmulatorThread(
            emulator = emu,
            onFrame = ::onFrameReady,
            audioPlayer = if (audioEnabled) audioPlayer else null,
            isRunning = { _state.value == EmulatorState.RUNNING },
            frameskip = if (_systemType.value == SystemType.GBA) settings.gbaFrameskip else settings.gbFrameskip
        )
        emulatorThread?.start()
    }

    private fun onFrameReady() {
        val emu = emulator ?: return
        val bitmap = renderBitmap ?: return
        val pixels = pixelBuffer ?: return

        emu.getVideoBufferInto(pixels)
        bitmap.setPixels(pixels, 0, emu.width, 0, 0, emu.width, emu.height)
        _frame.value = bitmap.asImageBitmap()
    }

    private fun saveScreenshot() {
        val emu = emulator ?: return
        val romId = currentRomId ?: return
        val pixels = emu.captureScreenshot() ?: return
        val w = emu.width
        val h = emu.height
        if (w <= 0 || h <= 0) return

        try {
            val context = getApplication<Application>()
            val screenshotDir = File(context.filesDir, "screenshots")
            screenshotDir.mkdirs()
            val screenshotFile = File(screenshotDir, "${romId.substringBeforeLast('.')}.png")

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            screenshotFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, out)
            }
            bitmap.recycle()

            // Update metadata with screenshot path
            metadataStore.update(romId) { meta ->
                meta.copy(thumbnailPath = screenshotFile.absolutePath)
            }
        } catch (_: Exception) {
            // Screenshot is non-critical
        }
    }

    private fun updatePlayTime() {
        val romId = currentRomId ?: return
        if (sessionStartTime == 0L) return

        val sessionMs = System.currentTimeMillis() - sessionStartTime
        metadataStore.update(romId) { meta ->
            meta.copy(
                lastPlayed = System.currentTimeMillis(),
                totalPlayTimeMs = meta.totalPlayTimeMs + sessionMs
            )
        }
        sessionStartTime = System.currentTimeMillis() // Reset for next session segment
    }

    /** Applies a GB palette immediately and persists the index. GB/GBC only. */
    fun setGbPalette(index: Int) {
        if (_systemType.value == SystemType.GBA) return
        settingsRepo.update { it.copy(gbPaletteIndex = index) }
        val palette = GbColorPalette.ALL.getOrNull(index) ?: return
        emulator?.setGbPalette(palette.mgbaOrder())
    }

    /** Toggles audio on/off and persists the setting. Takes effect on next resume(). */
    fun toggleMute() {
        settingsRepo.update { it.copy(audioEnabled = !it.audioEnabled) }
    }

    /** Adjusts volume by [delta] (±0.1 for 10% steps). Applied live via the settings collector. */
    fun adjustVolume(delta: Float) {
        settingsRepo.update {
            val raw = it.audioVolume + delta
            val snapped = (raw * 10).roundToInt() / 10f
            it.copy(audioVolume = snapped.coerceIn(0f, 1f))
        }
    }

    /**
     * Soft-resets the emulator (must be called while PAUSED).
     * Calls mCoreReset() to restart the game, then resumes.
     * SRAM is preserved (the VFile handle stays open).
     */
    fun resetRom() {
        if (_state.value != EmulatorState.PAUSED) return
        emulator?.reset()
        resume()
    }

    fun pressButton(button: ButtonId) {
        emulator?.pressButton(button)
    }

    fun releaseButton(button: ButtonId) {
        emulator?.releaseButton(button)
    }

    fun pause() {
        if (_state.value == EmulatorState.RUNNING) {
            _state.value = EmulatorState.PAUSED
            emulatorThread?.stop()
            audioPlayer?.pause()
            saveScreenshot()
            updatePlayTime()
            saveStateManager?.onFocusLost()
        }
    }

    fun resume() {
        if (_state.value == EmulatorState.PAUSED) {
            val settings = settingsRepo.settings.value
            val wasAudioEnabled = audioEnabled
            audioEnabled = settings.audioEnabled

            // Reset session timer for the resumed segment
            sessionStartTime = System.currentTimeMillis()
            _state.value = EmulatorState.RUNNING

            if (audioEnabled && !wasAudioEnabled) {
                val emu = emulator ?: return
                val player = AudioPlayer(OUTPUT_SAMPLE_RATE)
                player.setVolume(settings.audioVolume)
                audioPlayer = player
                emu.initAudio(OUTPUT_SAMPLE_RATE)
                player.start()
            } else if (!audioEnabled && wasAudioEnabled) {
                audioPlayer?.stop()
                audioPlayer?.release()
                audioPlayer = null
            } else if (audioEnabled) {
                audioPlayer?.setVolume(settings.audioVolume)
                audioPlayer?.resume()
            }

            startEmulatorThread(emulator!!)
        }
    }

    fun stop() {
        val wasRunning = _state.value == EmulatorState.RUNNING ||
                _state.value == EmulatorState.PAUSED
        _state.value = EmulatorState.IDLE
        emulatorThread?.stop()
        if (wasRunning && emulator != null) {
            saveScreenshot()
            updatePlayTime()
            saveStateManager?.onFocusLost()
        }
        emulatorThread = null
        audioPlayer?.stop()
        audioPlayer?.release()
        audioPlayer = null
        emulator?.destroy()
        emulator = null
        renderBitmap?.recycle()
        renderBitmap = null
        pixelBuffer = null
        saveStateManager = null
        currentRomId = null
        sessionStartTime = 0L
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
