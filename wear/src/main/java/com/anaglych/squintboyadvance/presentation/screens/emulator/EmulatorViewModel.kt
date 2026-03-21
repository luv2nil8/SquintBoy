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
import com.anaglych.squintboyadvance.presentation.EntitlementRepository
import com.anaglych.squintboyadvance.presentation.RomMetadataStore
import com.anaglych.squintboyadvance.presentation.SettingsRepository
import com.anaglych.squintboyadvance.shared.emulator.EmulatorState
import com.anaglych.squintboyadvance.shared.model.DemoLimits
import com.anaglych.squintboyadvance.shared.model.ButtonId
import com.anaglych.squintboyadvance.shared.model.GbColorPalette
import com.anaglych.squintboyadvance.shared.model.RomOverrides
import com.anaglych.squintboyadvance.shared.model.ScaleMode
import com.anaglych.squintboyadvance.shared.model.SystemType
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val OUTPUT_SAMPLE_RATE = 48000
    }

    private val settingsRepo = SettingsRepository.getInstance(application)
    private val entitlementRepo = EntitlementRepository.getInstance(application)
    private val metadataStore = RomMetadataStore.getInstance(application)
    private val screenshotManager = ScreenshotManager(
        File(application.filesDir, "screenshots"), metadataStore
    )
    private val playTimeTracker = PlayTimeTracker(metadataStore)

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

    private val _ffSpeed = MutableStateFlow(0)
    val ffSpeed: StateFlow<Int> = _ffSpeed.asStateFlow()

    private val _hasSaveState = MutableStateFlow(false)
    val hasSaveState: StateFlow<Boolean> = _hasSaveState.asStateFlow()

    private val _canUndoSave = MutableStateFlow(false)
    val canUndoSave: StateFlow<Boolean> = _canUndoSave.asStateFlow()

    private val _canUndoLoad = MutableStateFlow(false)
    val canUndoLoad: StateFlow<Boolean> = _canUndoLoad.asStateFlow()

    private var emulator: MgbaEmulator? = null
    private var emulatorThread: EmulatorThread? = null
    private var audioPlayer: AudioPlayer? = null
    private var saveStateManager: SaveStateManager? = null
    private var audioEnabled = false
    private val _currentRomId = MutableStateFlow<String?>(null)
    val currentRomId: StateFlow<String?> = _currentRomId.asStateFlow()

    // ── Demo session timer ────────────────────────────────────────────
    val isPro: StateFlow<Boolean> = entitlementRepo.isPro

    private val _sessionElapsedMs = MutableStateFlow(0L)
    val sessionElapsedMs: StateFlow<Long> = _sessionElapsedMs.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    /** Remaining session time in ms; negative means expired. */
    val sessionRemainingMs: StateFlow<Long> = _sessionElapsedMs.map {
        DemoLimits.SESSION_TIME_MS - it
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DemoLimits.SESSION_TIME_MS)

    // Reusable bitmap to avoid GC pressure
    private var renderBitmap: Bitmap? = null
    private var pixelBuffer: IntArray? = null

    /** Effective palette index: ROM override if active, else global. */
    private fun effectivePaletteIndex(s: com.anaglych.squintboyadvance.shared.model.EmulatorSettings): Int {
        val romId = _currentRomId.value ?: return s.gbPaletteIndex
        val ov = s.romOverrides[romId] ?: return s.gbPaletteIndex
        if (!ov.active) return s.gbPaletteIndex
        return ov.gbPaletteIndex ?: s.gbPaletteIndex
    }

    /** Effective frameskip: ROM override if active, else global. */
    private fun effectiveFrameskip(s: com.anaglych.squintboyadvance.shared.model.EmulatorSettings): Int {
        val isGba = _systemType.value == SystemType.GBA
        val romId = _currentRomId.value
        if (romId != null) {
            val ov = s.romOverrides[romId]
            if (ov != null && ov.active) {
                val skip = if (isGba) ov.gbaFrameskip else ov.gbFrameskip
                if (skip != null) return skip
            }
        }
        return if (isGba) s.gbaFrameskip else s.gbFrameskip
    }

    init {
        // Apply GB palette changes live — uses effective (ROM override or global).
        viewModelScope.launch {
            combine(settingsRepo.settings, _currentRomId) { s, _ -> effectivePaletteIndex(s) }
                .distinctUntilChanged()
                .collect { index ->
                    if (_systemType.value == SystemType.GBA) return@collect
                    val palette = GbColorPalette.ALL.getOrNull(index) ?: return@collect
                    emulator?.setGbPalette(palette.mgbaOrder())
                }
        }
        // Demo session timer — counts running time, soft-locks when expired.
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (_state.value == EmulatorState.RUNNING && !isPro.value) {
                    _sessionElapsedMs.value += 1000
                    if (_sessionElapsedMs.value >= DemoLimits.SESSION_TIME_MS) {
                        _sessionExpired.value = true
                        pause()
                    }
                }
            }
        }
        // Clear session expiry if user upgrades mid-session.
        viewModelScope.launch {
            isPro.collect { pro ->
                if (pro) _sessionExpired.value = false
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

    fun loadRom(romId: String, romTitle: String) {
        if (_state.value == EmulatorState.RUNNING || _state.value == EmulatorState.LOADING) return

        // Reset demo session timer for new session
        _sessionElapsedMs.value = 0
        _sessionExpired.value = false

        val settings = settingsRepo.settings.value
        this.audioEnabled = settings.audioEnabled

        _currentRomId.value = romId
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
        refreshSaveStateAvailability()

        // Set up audio with resampler
        if (audioEnabled) {
            val player = AudioPlayer(OUTPUT_SAMPLE_RATE)
            player.setVolume(settings.audioVolume)
            audioPlayer = player
            emu.initAudio(OUTPUT_SAMPLE_RATE)
            player.start()
        }

        // Apply GB color palette (GB/GBC only; no-op if GBA) — uses effective (ROM or global)
        if (_systemType.value != SystemType.GBA) {
            val paletteIdx = effectivePaletteIndex(settings)
            val palette = GbColorPalette.ALL.getOrNull(paletteIdx)
            if (palette != null) emu.setGbPalette(palette.mgbaOrder())
        }

        playTimeTracker.start(romId)

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
            frameskip = effectiveFrameskip(settings),
        ).also { it.ffSpeed = _ffSpeed.value }
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

    /** Applies a GB palette to the emulator without writing settings. GB/GBC only. */
    fun applyGbPalette(palette: GbColorPalette) {
        if (_systemType.value == SystemType.GBA) return
        emulator?.setGbPalette(palette.mgbaOrder())
    }

    private fun refreshSaveStateAvailability() {
        _hasSaveState.value = saveStateManager?.hasSaveState() ?: false
    }

    fun saveState() {
        if (_state.value != EmulatorState.PAUSED) return
        val success = saveStateManager?.saveExplicit() ?: false
        if (success) {
            _canUndoSave.value = true
            _canUndoLoad.value = false
            refreshSaveStateAvailability()
        }
    }

    fun loadState() {
        if (_state.value != EmulatorState.PAUSED) return
        val success = saveStateManager?.loadExplicit() ?: false
        if (success) {
            _canUndoLoad.value = true
            _canUndoSave.value = false
            onFrameReady()
        }
    }

    fun undoSave() {
        if (_state.value != EmulatorState.PAUSED || !_canUndoSave.value) return
        saveStateManager?.undoSave()
        _canUndoSave.value = false
        refreshSaveStateAvailability() // .usersave may now be gone or reverted
    }

    fun undoLoad() {
        if (_state.value != EmulatorState.PAUSED || !_canUndoLoad.value) return
        val success = saveStateManager?.undoLoad() ?: false
        if (success) {
            _canUndoLoad.value = false
            onFrameReady()
        }
    }

    /** Toggles fast-forward on/off. Uses last selected speed (default 2×). */
    fun toggleFastForward() {
        val next = if (_ffSpeed.value == 0) _ffSelectedSpeed.value else 0
        setFfSpeed(next)
    }

    /** Sets a specific FF speed (0 = off, 2/3/4). */
    fun setFfSpeed(speed: Int) {
        if (speed >= 2) _ffSelectedSpeed.value = speed
        _ffSpeed.value = speed
        emulatorThread?.ffSpeed = speed
    }

    private val _ffSelectedSpeed = MutableStateFlow(2)
    val ffSelectedSpeed: StateFlow<Int> = _ffSelectedSpeed.asStateFlow()

    /** Derived from the `active` flag in the ROM's override entry. */
    val isRomMode: StateFlow<Boolean> = combine(settingsRepo.settings, _currentRomId) { s, romId ->
        romId != null && (s.romOverrides[romId]?.active ?: false)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Toggles the `active` flag on the ROM's override entry. Non-destructive: override values are preserved. */
    fun setRomMode(enabled: Boolean) {
        val romId = _currentRomId.value ?: return
        settingsRepo.update { s ->
            val ov = s.romOverrides[romId] ?: RomOverrides()
            s.copy(romOverrides = s.romOverrides + (romId to ov.copy(active = enabled)))
        }
    }

    /**
     * Copies the current ROM's effective display settings into global settings.
     * After this, global settings match what was customized for this ROM.
     */
    fun saveRomToGlobal() {
        _currentRomId.value ?: return
        // Use the current effective settings (ROM override merged with global) as the new global.
        // _isRomMode is true here, so settingsRepo.settings already has overrides applied via
        // the EmulatorScreen effectiveSettings layer — but we recompute directly to stay pure.
        val s = settingsRepo.settings.value
        val ov = s.romOverrides[_currentRomId.value] ?: RomOverrides()
        settingsRepo.update { base ->
            base.copy(
                gbaScaleMode     = ov.gbaScaleMode     ?: base.gbaScaleMode,
                gbaCustomScale   = ov.gbaCustomScale   ?: base.gbaCustomScale,
                gbScaleMode      = ov.gbScaleMode      ?: base.gbScaleMode,
                gbCustomScale    = ov.gbCustomScale    ?: base.gbCustomScale,
                gbaFilterEnabled = ov.gbaFilterEnabled ?: base.gbaFilterEnabled,
                gbFilterEnabled  = ov.gbFilterEnabled  ?: base.gbFilterEnabled,
                gbaFrameskip     = ov.gbaFrameskip     ?: base.gbaFrameskip,
                gbFrameskip      = ov.gbFrameskip      ?: base.gbFrameskip,
                gbPaletteIndex   = ov.gbPaletteIndex   ?: base.gbPaletteIndex,
            )
        }
    }

    /** Removes all ROM-specific overrides, reverting this ROM to global settings. */
    fun resetRomToGlobal() {
        val romId = _currentRomId.value ?: return
        settingsRepo.update { s ->
            s.copy(romOverrides = s.romOverrides - romId)
        }
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

    /** Sets volume to an absolute value (0.0–1.0). Applied live via the settings collector. */
    fun setVolume(volume: Float) {
        settingsRepo.update { it.copy(audioVolume = volume.coerceIn(0f, 1f)) }
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
            emulator?.let { emu ->
                _currentRomId.value?.let { screenshotManager.capture(emu, it) }
            }
            playTimeTracker.flush()
            saveStateManager?.onFocusLost()
        }
    }

    fun resume() {
        if (_state.value == EmulatorState.PAUSED) {
            val settings = settingsRepo.settings.value
            val wasAudioEnabled = audioEnabled
            audioEnabled = settings.audioEnabled

            playTimeTracker.start(_currentRomId.value ?: return)
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
            emulator?.let { emu ->
                _currentRomId.value?.let { screenshotManager.capture(emu, it) }
            }
            playTimeTracker.stop()
            saveStateManager?.onFocusLost()
        }
        _ffSpeed.value = 0
        _hasSaveState.value = false
        _canUndoSave.value = false
        _canUndoLoad.value = false
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
        _currentRomId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
