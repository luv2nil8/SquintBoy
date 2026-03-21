package com.anaglych.squintboyadvance.presentation.screens.emulator

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.wear.compose.material.Icon
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.presentation.EntitlementRepository
import com.anaglych.squintboyadvance.presentation.SettingsRepository
import com.anaglych.squintboyadvance.presentation.components.WearSlideToConfirm
import com.anaglych.squintboyadvance.shared.emulator.EmulatorState
import com.anaglych.squintboyadvance.shared.model.EmulatorSettings
import com.anaglych.squintboyadvance.shared.model.RomOverrides
import com.anaglych.squintboyadvance.shared.model.ScaleMode
import com.anaglych.squintboyadvance.shared.model.SystemType

private enum class PauseUiState { MENU, CONFIRM_RESET, SESSION_EXPIRED }

@Composable
fun EmulatorScreen(
    romId: String,
    romTitle: String,
    onExit: () -> Unit,
    viewModel: EmulatorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val frame by viewModel.frame.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val systemType by viewModel.systemType.collectAsState()
    val ffSpeed by viewModel.ffSpeed.collectAsState()
    val ffSelectedSpeed by viewModel.ffSelectedSpeed.collectAsState()
    val hasSaveState by viewModel.hasSaveState.collectAsState()
    val canUndoSave by viewModel.canUndoSave.collectAsState()
    val canUndoLoad by viewModel.canUndoLoad.collectAsState()
    val isRomMode by viewModel.isRomMode.collectAsState()
    val currentRomId by viewModel.currentRomId.collectAsState()
    val sessionExpired by viewModel.sessionExpired.collectAsState()
    val sessionRemainingMs by viewModel.sessionRemainingMs.collectAsState()
    val isPro by viewModel.isPro.collectAsState()

    val context = LocalContext.current
    val settingsRepo = SettingsRepository.getInstance(viewModel.getApplication())
    val settings by settingsRepo.settings.collectAsState()

    // Effective settings: ROM overrides layer on top of global when isRomMode is active
    val effectiveSettings = if (isRomMode && currentRomId != null) {
        val ov = settings.romOverrides[currentRomId] ?: RomOverrides()
        settings.copy(
            gbaScaleMode     = ov.gbaScaleMode     ?: settings.gbaScaleMode,
            gbaCustomScale   = ov.gbaCustomScale   ?: settings.gbaCustomScale,
            gbScaleMode      = ov.gbScaleMode      ?: settings.gbScaleMode,
            gbCustomScale    = ov.gbCustomScale    ?: settings.gbCustomScale,
            gbaFilterEnabled = ov.gbaFilterEnabled ?: settings.gbaFilterEnabled,
            gbFilterEnabled  = ov.gbFilterEnabled  ?: settings.gbFilterEnabled,
            gbaFrameskip     = ov.gbaFrameskip     ?: settings.gbaFrameskip,
            gbFrameskip      = ov.gbFrameskip      ?: settings.gbFrameskip,
            gbPaletteIndex   = ov.gbPaletteIndex   ?: settings.gbPaletteIndex,
        )
    } else settings

    val isGba = systemType == SystemType.GBA
    val scaleMode = if (isGba) effectiveSettings.gbaScaleMode else effectiveSettings.gbScaleMode
    val customScale = if (isGba) effectiveSettings.gbaCustomScale else effectiveSettings.gbCustomScale
    val filterEnabled = if (isGba) effectiveSettings.gbaFilterEnabled else effectiveSettings.gbFilterEnabled

    // Whether ROM overrides differ from global — drives Save/Reset button enabled state
    val hasRomDifferences = if (currentRomId != null) {
        val ov = settings.romOverrides[currentRomId]
        ov != null && ov.differsFrom(settings, isGba)
    } else false

    // Helper: write a display setting to ROM override or global depending on mode
    fun updateDisplaySetting(update: (RomOverrides) -> RomOverrides, globalUpdate: (EmulatorSettings) -> EmulatorSettings) {
        if (isRomMode && currentRomId != null) {
            settingsRepo.update { s ->
                val ov = s.romOverrides[currentRomId!!] ?: RomOverrides()
                s.copy(romOverrides = s.romOverrides + (currentRomId!! to update(ov)))
            }
        } else {
            settingsRepo.update(globalUpdate)
        }
    }

    // Pause sub-screen state — resets to MENU whenever the emulator resumes
    var pauseUiState by rememberSaveable { mutableStateOf(PauseUiState.MENU) }
    var pauseGhostProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state) {
        if (state == EmulatorState.RUNNING) pauseUiState = PauseUiState.MENU
    }
    // Redirect to session expired overlay when timer runs out
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) pauseUiState = PauseUiState.SESSION_EXPIRED
    }

    LaunchedEffect(romId) {
        viewModel.loadRom(romId, romTitle)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            EmulatorState.LOADING -> {
                CircularProgressIndicator()
            }

            EmulatorState.RUNNING -> {
                GameDisplay(
                    frame = frame,
                    scaleMode = scaleMode,
                    customScale = customScale,
                    filterEnabled = filterEnabled,
                )
                TouchOverlay(
                    systemType = systemType,
                    onButtonPress = viewModel::pressButton,
                    onButtonRelease = viewModel::releaseButton,
                    onPause = viewModel::pause,
                    visible = settings.controllerLayout.visible,
                    buttonOpacity = settings.controllerLayout.buttonOpacity,
                    pressedOpacity = settings.controllerLayout.pressedOpacity,
                    labelOpacity = settings.controllerLayout.labelOpacity,
                    labelSize = settings.controllerLayout.labelSize,
                    hapticEnabled = settings.controllerLayout.hapticFeedback,
                )
                if (ffSpeed >= 2) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Fast forward active",
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            EmulatorState.PAUSED -> {
                GameDisplay(
                    frame = frame,
                    scaleMode = scaleMode,
                    customScale = customScale,
                    filterEnabled = filterEnabled,
                )

                // OSC preview — visible during ghost mode so user sees live changes
                if (pauseGhostProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = pauseGhostProgress },
                    ) {
                        TouchOverlay(
                            systemType = systemType,
                            onButtonPress = {},
                            onButtonRelease = {},
                            onPause = {},
                            visible = true,
                            buttonOpacity = settings.controllerLayout.buttonOpacity,
                            pressedOpacity = settings.controllerLayout.pressedOpacity,
                            labelOpacity = settings.controllerLayout.labelOpacity,
                            labelSize = settings.controllerLayout.labelSize,
                            hapticEnabled = false,
                        )
                    }
                }

                when (pauseUiState) {
                    PauseUiState.MENU -> PauseOverlay(
                        isMuted = !settings.audioEnabled,
                        ffSpeed = ffSpeed,
                        ffSelectedSpeed = ffSelectedSpeed,
                        isGb = systemType == SystemType.GB,
                        isGba = isGba,
                        volume = settings.audioVolume,
                        hasSaveState = hasSaveState,
                        canUndoSave = canUndoSave,
                        canUndoLoad = canUndoLoad,
                        onToggleMute = viewModel::toggleMute,
                        onVolumeChange = { viewModel.setVolume(it) },
                        onResume = viewModel::resume,
                        // Scale (routed to ROM override or global)
                        customScale = customScale,
                        filterEnabled = filterEnabled,
                        onSetCustomScale = { scale ->
                            if (isPro) {
                                updateDisplaySetting(
                                    { ov -> if (isGba) ov.copy(gbaScaleMode = ScaleMode.CUSTOM, gbaCustomScale = scale) else ov.copy(gbScaleMode = ScaleMode.CUSTOM, gbCustomScale = scale) },
                                    { it -> if (isGba) it.copy(gbaScaleMode = ScaleMode.CUSTOM, gbaCustomScale = scale) else it.copy(gbScaleMode = ScaleMode.CUSTOM, gbCustomScale = scale) },
                                )
                            }
                        },
                        onToggleFilter = {
                            updateDisplaySetting(
                                { ov -> if (isGba) ov.copy(gbaFilterEnabled = !(ov.gbaFilterEnabled ?: effectiveSettings.gbaFilterEnabled)) else ov.copy(gbFilterEnabled = !(ov.gbFilterEnabled ?: effectiveSettings.gbFilterEnabled)) },
                                { it -> if (isGba) it.copy(gbaFilterEnabled = !it.gbaFilterEnabled) else it.copy(gbFilterEnabled = !it.gbFilterEnabled) },
                            )
                        },
                        gbaFrameskip = effectiveSettings.gbaFrameskip,
                        gbFrameskip = effectiveSettings.gbFrameskip,
                        onSetFrameskip = { skip ->
                            updateDisplaySetting(
                                { ov -> if (isGba) ov.copy(gbaFrameskip = skip) else ov.copy(gbFrameskip = skip) },
                                { it -> if (isGba) it.copy(gbaFrameskip = skip) else it.copy(gbFrameskip = skip) },
                            )
                        },
                        // Controls (OSC) — always global
                        oscVisible = settings.controllerLayout.visible,
                        buttonOpacity = settings.controllerLayout.buttonOpacity,
                        pressedOpacity = settings.controllerLayout.pressedOpacity,
                        labelOpacity = settings.controllerLayout.labelOpacity,
                        labelSize = settings.controllerLayout.labelSize,
                        hapticEnabled = settings.controllerLayout.hapticFeedback,
                        onToggleOscVisible = {
                            settingsRepo.update {
                                it.copy(controllerLayout = it.controllerLayout.copy(visible = !it.controllerLayout.visible))
                            }
                        },
                        onSetButtonOpacity = { v ->
                            settingsRepo.update { it.copy(controllerLayout = it.controllerLayout.copy(buttonOpacity = v)) }
                        },
                        onSetPressedOpacity = { v ->
                            settingsRepo.update { it.copy(controllerLayout = it.controllerLayout.copy(pressedOpacity = v)) }
                        },
                        onSetLabelOpacity = { v ->
                            settingsRepo.update { it.copy(controllerLayout = it.controllerLayout.copy(labelOpacity = v)) }
                        },
                        onSetLabelSize = { v ->
                            settingsRepo.update { it.copy(controllerLayout = it.controllerLayout.copy(labelSize = v)) }
                        },
                        onToggleHaptic = {
                            settingsRepo.update {
                                it.copy(controllerLayout = it.controllerLayout.copy(hapticFeedback = !it.controllerLayout.hapticFeedback))
                            }
                        },
                        onSave = { viewModel.saveState(); viewModel.resume() },
                        onLoad = { viewModel.loadState(); viewModel.resume() },
                        onUndoSave = { viewModel.undoSave(); viewModel.resume() },
                        onUndoLoad = { viewModel.undoLoad(); viewModel.resume() },
                        onFastForward = viewModel::toggleFastForward,
                        onSetFfSpeed = viewModel::setFfSpeed,
                        // Per-ROM settings
                        isRomMode = isRomMode,
                        hasRomDifferences = hasRomDifferences,
                        onSetRomMode = viewModel::setRomMode,
                        onSaveRomToGlobal = viewModel::saveRomToGlobal,
                        onResetRomToGlobal = viewModel::resetRomToGlobal,
                        onReset = { pauseUiState = PauseUiState.CONFIRM_RESET },
                        selectedPaletteIndex = effectiveSettings.gbPaletteIndex,
                        onPaletteSelected = { idx ->
                            updateDisplaySetting(
                                { ov -> ov.copy(gbPaletteIndex = idx) },
                                { it -> it.copy(gbPaletteIndex = idx) },
                            )
                            // Apply palette immediately to the running emulator
                            val palette = com.anaglych.squintboyadvance.shared.model.GbColorPalette.ALL.getOrNull(idx)
                            if (palette != null) viewModel.applyGbPalette(palette)
                            viewModel.resume()
                        },
                        onExit = {
                            viewModel.stop()
                            onExit()
                        },
                        isDemo = !isPro,
                        onUpgrade = {
                            val activity = (context as? Activity)
                            if (activity != null) {
                                EntitlementRepository.getInstance(activity).launchPurchase(activity)
                            }
                        },
                        sessionRemainingMs = sessionRemainingMs,
                        onGhostProgressChange = { pauseGhostProgress = it },
                    )

                    PauseUiState.CONFIRM_RESET -> WearSlideToConfirm(
                        slideText = "Slide to reset",
                        warningText = "The game will restart from the beginning. Your save file is preserved.",
                        confirmColor = Color(0xFFEC1358),
                        onConfirmed = { viewModel.resetRom() },
                        onDismiss = { pauseUiState = PauseUiState.MENU },
                    )

                    PauseUiState.SESSION_EXPIRED -> {
                        val activity = context as? Activity
                        SessionExpiredOverlay(
                            onUpgrade = {
                                if (activity != null) {
                                    EntitlementRepository.getInstance(activity).launchPurchase(activity)
                                }
                            },
                            onExit = {
                                viewModel.stop()
                                onExit()
                            },
                        )
                    }
                }
            }

            EmulatorState.ERROR -> {
                Text(
                    text = errorMessage ?: "Unknown error",
                    color = Color(0xFFEC1358),
                    style = MaterialTheme.typography.body2,
                )
            }

            EmulatorState.IDLE -> {
                // Nothing to show
            }
        }
    }
}
