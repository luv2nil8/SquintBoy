package com.anaglych.squintboyadvance.presentation.screens.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
import com.anaglych.squintboyadvance.presentation.components.WearSlideToConfirm
import com.anaglych.squintboyadvance.shared.model.InputDevice

@Composable
fun ControllerSettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent {
                    coroutineScope.launch { listState.scrollBy(it.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            item {
                ListHeader {
                    Text("Controls", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)
                }
            }
            item {
                ToggleChip(
                    checked = settings.controllerLayout.visible,
                    onCheckedChange = { viewModel.setOverlayVisible(it) },
                    label = { Text("Visibility") },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(
                                checked = settings.controllerLayout.visible
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(ToggleChipDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = "Outline Opacity: ${(settings.controllerLayout.buttonOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            item {
                InlineSlider(
                    value = settings.controllerLayout.buttonOpacity,
                    onValueChange = { viewModel.setButtonOpacity(it) },
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
            }
            item {
                Text(
                    text = "Pressed Opacity: ${(settings.controllerLayout.pressedOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            item {
                InlineSlider(
                    value = settings.controllerLayout.pressedOpacity,
                    onValueChange = { viewModel.setPressedOpacity(it) },
                    valueRange = 0f..0.5f,
                    steps = 4,
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
            }
            item {
                Text(
                    text = "Label Opacity: ${(settings.controllerLayout.labelOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            item {
                InlineSlider(
                    value = settings.controllerLayout.labelOpacity,
                    onValueChange = { viewModel.setLabelOpacity(it) },
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
            }
            item {
                val labelSizeName = when (settings.controllerLayout.labelSize.toInt()) {
                    9 -> "Tiny"
                    11 -> "Small"
                    13 -> "Normal"
                    15 -> "Large"
                    17 -> "Huge"
                    else -> "${settings.controllerLayout.labelSize.toInt()}sp"
                }
                Text(
                    text = "Label Size: $labelSizeName",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            item {
                InlineSlider(
                    value = settings.controllerLayout.labelSize,
                    onValueChange = { viewModel.setLabelSize(it) },
                    valueRange = 9f..17f,
                    steps = 3,
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
            item {
                val resetRed = Color(0xFFEC1358)
                Chip(
                    onClick = { showResetConfirm = true },
                    label = { Text("Reset to Defaults", color = resetRed) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, resetRed, RoundedCornerShape(50)),
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Color.Transparent
                    )
                )
            }
        }

        if (showResetConfirm) {
            WearSlideToConfirm(
                slideText = "Slide to reset",
                onConfirmed = {
                    viewModel.resetControls()
                    showResetConfirm = false
                },
                onDismiss = { showResetConfirm = false }
            )
        }
    }
}
