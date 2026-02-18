package com.example.squintboyadvance.presentation.screens.settings

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition

@Composable
fun AudioSettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ListHeader {
                    Text("Audio", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
                }
            }
            item {
                ToggleChip(
                    checked = settings.audioEnabled,
                    onCheckedChange = { viewModel.setAudioEnabled(it) },
                    label = { Text("Audio Enabled") },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(checked = settings.audioEnabled),
                            contentDescription = null,
                            modifier = Modifier.size(ToggleChipDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = "Volume: ${(settings.audioVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    InlineSlider(
                        value = settings.audioVolume,
                        onValueChange = { viewModel.setAudioVolume(it) },
                        valueRange = 0f..1f,
                        steps = 9,
                        decreaseIcon = {
                            Icon(
                                imageVector = InlineSliderDefaults.Decrease,
                                contentDescription = "Decrease"
                            )
                        },
                        increaseIcon = {
                            Icon(
                                imageVector = InlineSliderDefaults.Increase,
                                contentDescription = "Increase"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Drag overlay — padded to only cover the track, leaving +/- buttons exposed
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 52.dp)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    val fraction = (change.position.x / size.width)
                                        .coerceIn(0f, 1f)
                                    // Snap to 10% increments (9 steps = 10 segments)
                                    val snapped = (fraction * 10).toInt() / 10f
                                    viewModel.setAudioVolume(snapped)
                                }
                            }
                    )
                }
            }
        }
    }
}
