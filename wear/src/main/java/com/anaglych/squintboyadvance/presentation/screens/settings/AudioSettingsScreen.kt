package com.anaglych.squintboyadvance.presentation.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun AudioSettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
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
                VolumeSlider(
                    volume = settings.audioVolume,
                    onVolumeChange = { viewModel.setAudioVolume(it) }
                )
            }
        }
    }
}

@Composable
private fun VolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {
    val primaryColor = MaterialTheme.colors.primary
    val trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
    val tickColor = MaterialTheme.colors.onSurface.copy(alpha = 0.25f)
    val volumeState = rememberUpdatedState(volume)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Decrease button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable {
                    val stepped = ((volumeState.value * 10).roundToInt() - 1)
                        .coerceIn(0, 10) / 10f
                    onVolumeChange(stepped)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = InlineSliderDefaults.Decrease,
                contentDescription = "Decrease",
                tint = primaryColor
            )
        }

        // Canvas slider with tap + drag
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .pointerInput(Unit) {
                    val thumbR = 8.dp.toPx()
                    val usable = size.width - thumbR * 2

                    fun fractionToVolume(x: Float): Float {
                        val f = ((x - thumbR) / usable).coerceIn(0f, 1f)
                        return (f * 10).roundToInt() / 10f
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val drag = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        }
                        if (drag != null) {
                            onVolumeChange(fractionToVolume(drag.position.x))
                            horizontalDrag(drag.id) { change ->
                                change.consume()
                                onVolumeChange(fractionToVolume(change.position.x))
                            }
                        } else {
                            onVolumeChange(fractionToVolume(down.position.x))
                        }
                    }
                }
        ) {
            val trackH = 4.dp.toPx()
            val trackY = (size.height - trackH) / 2
            val thumbRadius = 8.dp.toPx()
            val trackLeft = thumbRadius
            val trackRight = size.width - thumbRadius
            val trackW = trackRight - trackLeft
            val fraction = volume.coerceIn(0f, 1f)
            val thumbX = trackLeft + fraction * trackW

            // Inactive track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackLeft, trackY),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(trackH / 2)
            )
            // Active track
            if (fraction > 0f) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(trackLeft, trackY),
                    size = Size(thumbX - trackLeft, trackH),
                    cornerRadius = CornerRadius(trackH / 2)
                )
            }
            // Tick marks at each 10%
            val tickRadius = 2.dp.toPx() / 2
            for (i in 0..10) {
                val tickFrac = i / 10f
                val tickX = trackLeft + tickFrac * trackW
                drawCircle(
                    color = if (tickFrac <= fraction) primaryColor.copy(alpha = 0.5f) else tickColor,
                    radius = tickRadius,
                    center = Offset(tickX, size.height / 2)
                )
            }
            // Thumb
            drawCircle(
                color = primaryColor,
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2)
            )
        }

        // Increase button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable {
                    val stepped = ((volumeState.value * 10).roundToInt() + 1)
                        .coerceIn(0, 10) / 10f
                    onVolumeChange(stepped)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = InlineSliderDefaults.Increase,
                contentDescription = "Increase",
                tint = primaryColor
            )
        }
    }
}
