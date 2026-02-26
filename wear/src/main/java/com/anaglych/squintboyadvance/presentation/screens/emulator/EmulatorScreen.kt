package com.anaglych.squintboyadvance.presentation.screens.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.presentation.SettingsRepository
import com.anaglych.squintboyadvance.presentation.components.WearSlideToConfirm
import com.anaglych.squintboyadvance.presentation.screens.settings.PalettePickerOverlay
import com.anaglych.squintboyadvance.presentation.screens.settings.ScaleEditorScreen
import com.anaglych.squintboyadvance.shared.emulator.EmulatorState
import com.anaglych.squintboyadvance.shared.model.ScaleMode
import com.anaglych.squintboyadvance.shared.model.SystemType

private enum class PauseUiState { MENU, SCALE_EDITOR, CONFIRM_RESET, PALETTE_PICKER }

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

    val settingsRepo = SettingsRepository.getInstance(viewModel.getApplication())
    val settings by settingsRepo.settings.collectAsState()

    val isGba = systemType == SystemType.GBA
    val scaleMode = if (isGba) settings.gbaScaleMode else settings.gbScaleMode
    val customScale = if (isGba) settings.gbaCustomScale else settings.gbCustomScale
    val filterEnabled = if (isGba) settings.gbaFilterEnabled else settings.gbFilterEnabled

    // Pause sub-screen state — resets to MENU whenever the emulator resumes
    var pauseUiState by rememberSaveable { mutableStateOf(PauseUiState.MENU) }
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
                )
            }

            EmulatorState.PAUSED -> {
                val activeScaleMode =
                    if (pauseUiState == PauseUiState.SCALE_EDITOR) ScaleMode.CUSTOM else scaleMode
                GameDisplay(
                    frame = frame,
                    scaleMode = activeScaleMode,
                    customScale = customScale,
                    filterEnabled = filterEnabled,
                )

                when (pauseUiState) {
                    PauseUiState.MENU -> PauseOverlay(
                        isMuted = !settings.audioEnabled,
                        isGb = !isGba,
                        onToggleMute = viewModel::toggleMute,
                        onResume = viewModel::resume,
                        onInterface = { pauseUiState = PauseUiState.SCALE_EDITOR },
                        onReset = { pauseUiState = PauseUiState.CONFIRM_RESET },
                        onPalette = { pauseUiState = PauseUiState.PALETTE_PICKER },
                        onExit = {
                            viewModel.stop()
                            onExit()
                        },
                    )

                    PauseUiState.SCALE_EDITOR -> ScaleEditorScreen(
                        isGba = isGba,
                        liveFrame = frame,
                        isOverlay = true,
                        onDismiss = { pauseUiState = PauseUiState.MENU },
                    )

                    PauseUiState.CONFIRM_RESET -> WearSlideToConfirm(
                        slideText = "Slide to reset",
                        warningText = "The game will restart from the beginning. Your save file is preserved.",
                        confirmColor = Color(0xFFD32F2F),
                        onConfirmed = { viewModel.resetRom() },
                        onDismiss = { pauseUiState = PauseUiState.MENU },
                    )

                    PauseUiState.PALETTE_PICKER -> PalettePickerOverlay(
                        selectedIndex = settings.gbPaletteIndex,
                        onSelected = { index ->
                            viewModel.setGbPalette(index)
                            pauseUiState = PauseUiState.MENU
                        },
                        onDismiss = { pauseUiState = PauseUiState.MENU },
                    )
                }
            }

            EmulatorState.ERROR -> {
                Text(
                    text = errorMessage ?: "Unknown error",
                    color = Color.Red,
                    style = MaterialTheme.typography.body2,
                )
            }

            EmulatorState.IDLE -> {
                // Nothing to show
            }
        }
    }
}
