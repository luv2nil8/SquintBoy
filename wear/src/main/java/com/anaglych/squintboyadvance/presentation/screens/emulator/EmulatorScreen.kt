package com.anaglych.squintboyadvance.presentation.screens.emulator

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
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.wear.compose.material.Icon
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.presentation.SettingsRepository
import com.anaglych.squintboyadvance.presentation.components.WearSlideToConfirm
import com.anaglych.squintboyadvance.shared.emulator.EmulatorState
import com.anaglych.squintboyadvance.shared.model.ScaleMode
import com.anaglych.squintboyadvance.shared.model.SystemType

private enum class PauseUiState { MENU, CONFIRM_RESET }

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

    val settingsRepo = SettingsRepository.getInstance(viewModel.getApplication())
    val settings by settingsRepo.settings.collectAsState()

    val isGba = systemType == SystemType.GBA
    val scaleMode = if (isGba) settings.gbaScaleMode else settings.gbScaleMode
    val customScale = if (isGba) settings.gbaCustomScale else settings.gbCustomScale
    val filterEnabled = if (isGba) settings.gbaFilterEnabled else settings.gbFilterEnabled

    // Pause sub-screen state — resets to MENU whenever the emulator resumes
    var pauseUiState by rememberSaveable { mutableStateOf(PauseUiState.MENU) }
    var pauseGhostProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state) {
        if (state == EmulatorState.RUNNING) pauseUiState = PauseUiState.MENU
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
                        // Scale
                        customScale = customScale,
                        filterEnabled = filterEnabled,
                        onSetCustomScale = { scale ->
                            settingsRepo.update {
                                if (isGba) it.copy(gbaScaleMode = ScaleMode.CUSTOM, gbaCustomScale = scale)
                                else it.copy(gbScaleMode = ScaleMode.CUSTOM, gbCustomScale = scale)
                            }
                        },
                        onToggleFilter = {
                            settingsRepo.update {
                                if (isGba) it.copy(gbaFilterEnabled = !it.gbaFilterEnabled)
                                else it.copy(gbFilterEnabled = !it.gbFilterEnabled)
                            }
                        },
                        gbaFrameskip = settings.gbaFrameskip,
                        gbFrameskip = settings.gbFrameskip,
                        onSetFrameskip = { skip ->
                            settingsRepo.update {
                                if (isGba) it.copy(gbaFrameskip = skip)
                                else it.copy(gbFrameskip = skip)
                            }
                        },
                        // Controls (OSC)
                        oscVisible = settings.controllerLayout.visible,
                        buttonOpacity = settings.controllerLayout.buttonOpacity,
                        pressedOpacity = settings.controllerLayout.pressedOpacity,
                        labelOpacity = settings.controllerLayout.labelOpacity,
                        labelSize = settings.controllerLayout.labelSize,
                        hapticEnabled = settings.controllerLayout.hapticFeedback,
                        onToggleOscVisible = {
                            settingsRepo.update {
                                it.copy(controllerLayout = it.controllerLayout.copy(
                                    visible = !it.controllerLayout.visible
                                ))
                            }
                        },
                        onSetButtonOpacity = { v ->
                            settingsRepo.update {
                                it.copy(controllerLayout = it.controllerLayout.copy(buttonOpacity = v))
                            }
                        },
                        onSetPressedOpacity = { v ->
                            settingsRepo.update {
                                it.copy(controllerLayout = it.controllerLayout.copy(pressedOpacity = v))
                            }
                        },
                        onSetLabelOpacity = { v ->
                            settingsRepo.update {
                                it.copy(controllerLayout = it.controllerLayout.copy(labelOpacity = v))
                            }
                        },
                        onSetLabelSize = { v ->
                            settingsRepo.update {
                                it.copy(controllerLayout = it.controllerLayout.copy(labelSize = v))
                            }
                        },
                        onToggleHaptic = {
                            settingsRepo.update {
                                it.copy(controllerLayout = it.controllerLayout.copy(
                                    hapticFeedback = !it.controllerLayout.hapticFeedback
                                ))
                            }
                        },
                        onSave = { viewModel.saveState(); viewModel.resume() },
                        onLoad = { viewModel.loadState(); viewModel.resume() },
                        onUndoSave = { viewModel.undoSave(); viewModel.resume() },
                        onUndoLoad = { viewModel.undoLoad(); viewModel.resume() },
                        onFastForward = viewModel::toggleFastForward,
                        onSetFfSpeed = viewModel::setFfSpeed,
                        onLinkCable = { /* TODO */ },
                        onReset = { pauseUiState = PauseUiState.CONFIRM_RESET },
                        selectedPaletteIndex = settings.gbPaletteIndex,
                        onPaletteSelected = { viewModel.setGbPalette(it); viewModel.resume() },
                        onExit = {
                            viewModel.stop()
                            onExit()
                        },
                        onGhostProgressChange = { pauseGhostProgress = it },
                    )

                    PauseUiState.CONFIRM_RESET -> WearSlideToConfirm(
                        slideText = "Slide to reset",
                        warningText = "The game will restart from the beginning. Your save file is preserved.",
                        confirmColor = Color(0xFFEC1358),
                        onConfirmed = { viewModel.resetRom() },
                        onDismiss = { pauseUiState = PauseUiState.MENU },
                    )
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
