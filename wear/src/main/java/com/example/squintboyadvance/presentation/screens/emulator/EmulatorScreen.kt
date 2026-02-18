package com.example.squintboyadvance.presentation.screens.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.squintboyadvance.presentation.SettingsRepository
import com.example.squintboyadvance.shared.emulator.EmulatorState
import com.example.squintboyadvance.shared.model.ScaleMode
import com.example.squintboyadvance.shared.model.SystemType

@Composable
fun EmulatorScreen(
    romId: String,
    romTitle: String,
    onExit: () -> Unit,
    viewModel: EmulatorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val frame by viewModel.frame.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val systemType by viewModel.systemType.collectAsState()
    val fps by viewModel.fps.collectAsState()

    val settingsRepo = SettingsRepository.getInstance(
        viewModel.getApplication()
    )
    val settings by settingsRepo.settings.collectAsState()

    // Pick scale settings based on current system type
    val isGba = systemType == SystemType.GBA
    val scaleMode = if (isGba) settings.gbaScaleMode else settings.gbScaleMode
    val customScale = if (isGba) settings.gbaCustomScale else settings.gbCustomScale
    val filterEnabled = if (isGba) settings.gbaFilterEnabled else settings.gbFilterEnabled

    LaunchedEffect(romId) {
        viewModel.loadRom(romId, romTitle)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
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
                    filterEnabled = filterEnabled
                )
                TouchOverlay(
                    systemType = systemType,
                    onButtonPress = viewModel::pressButton,
                    onButtonRelease = viewModel::releaseButton,
                    onPause = viewModel::pause
                )
                if (settings.showFps) {
                    Text(
                        text = "$fps",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 4.dp)
                    )
                }
            }

            EmulatorState.PAUSED -> {
                GameDisplay(
                    frame = frame,
                    scaleMode = scaleMode,
                    customScale = customScale,
                    filterEnabled = filterEnabled
                )
                PauseOverlay(
                    onResume = viewModel::resume,
                    onExit = {
                        viewModel.stop()
                        onExit()
                    }
                )
            }

            EmulatorState.ERROR -> {
                Text(
                    text = errorMessage ?: "Unknown error",
                    color = Color.Red,
                    style = MaterialTheme.typography.body2
                )
            }

            EmulatorState.IDLE -> {
                // Nothing to show
            }
        }
    }
}
