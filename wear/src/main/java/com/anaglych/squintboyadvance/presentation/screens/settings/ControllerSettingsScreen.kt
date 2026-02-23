package com.anaglych.squintboyadvance.presentation.screens.settings

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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
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
import com.anaglych.squintboyadvance.shared.model.InputDevice

@Composable
fun ControllerSettingsScreen(
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
                    Text("Controller", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
                }
            }
            item {
                ListHeader {
                    Text("Input Mode", style = MaterialTheme.typography.body1, color = MaterialTheme.colors.onSurface)
                }
            }
            item {
                Chip(
                    onClick = { viewModel.setPreferredInput(InputDevice.TOUCH) },
                    label = { Text("Touchscreen") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (settings.preferredInput == InputDevice.TOUCH) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }
            item {
                Chip(
                    onClick = { viewModel.setPreferredInput(InputDevice.BLUETOOTH_GAMEPAD) },
                    label = { Text("Bluetooth Gamepad") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (settings.preferredInput == InputDevice.BLUETOOTH_GAMEPAD) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    }
                )
            }
            item {
                ListHeader {
                    Text("Touch Overlay", style = MaterialTheme.typography.body1, color = MaterialTheme.colors.onSurface)
                }
            }
            item {
                Text(
                    text = "Opacity: ${(settings.controllerLayout.overlayAlpha * 100).toInt()}%",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    InlineSlider(
                        value = settings.controllerLayout.overlayAlpha,
                        onValueChange = { viewModel.setOverlayAlpha(it) },
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
                                    val snapped = (fraction * 10).toInt() / 10f
                                    viewModel.setOverlayAlpha(snapped)
                                }
                            }
                    )
                }
            }
            item {
                ToggleChip(
                    checked = settings.controllerLayout.hapticFeedback,
                    onCheckedChange = { viewModel.setHapticFeedback(it) },
                    label = { Text("Haptic Feedback") },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(
                                checked = settings.controllerLayout.hapticFeedback
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(ToggleChipDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
