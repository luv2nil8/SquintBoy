package com.anaglych.squintboyadvance.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anaglych.squintboyadvance.ui.theme.Crimson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Reusable horizontal slide-to-confirm component (Material3 / mobile).
 *
 * Drag the handle right to ≥ 80 % of the track → [onConfirmed] fires.
 * Releasing early springs the handle back.
 *
 * @param text       Label shown inside the track.
 * @param accentColor Handle / active-fill colour (defaults to error red).
 * @param enabled    When false the handle is non-interactive.
 * @param onConfirmed Invoked once after a full drag.
 */
@Composable
fun SlideToConfirm(
    text: String = "Slide to confirm",
    accentColor: Color = Crimson,
    enabled: Boolean = true,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val trackHeight = 52.dp
    val handleSize = 44.dp
    val innerPadding = 4.dp
    val density = LocalDensity.current

    val offsetX = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val handlePx = with(density) { handleSize.toPx() }
        val paddingPx = with(density) { innerPadding.toPx() }
        val maxOffsetPx = trackWidthPx - handlePx - paddingPx * 2f

        // Progress fill
        val fillFraction = ((offsetX.value + handlePx + paddingPx) / trackWidthPx).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .height(trackHeight)
                .width(with(density) { (trackWidthPx * fillFraction).toDp() })
                .clip(RoundedCornerShape(50))
                .background(accentColor.copy(alpha = 0.22f)),
        )

        // Instruction label (fades as handle moves right)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(start = handleSize + innerPadding * 2 + 4.dp, end = 16.dp)
                .graphicsLayer {
                    alpha = (1f - offsetX.value / (maxOffsetPx * 0.5f)).coerceIn(0f, 1f)
                },
        )

        // Draggable handle
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(handleSize)
                .clip(CircleShape)
                .background(
                    if (enabled) accentColor else accentColor.copy(alpha = 0.38f),
                )
                .then(
                    if (enabled) Modifier.pointerInput(maxOffsetPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offsetX.value >= maxOffsetPx * 0.80f) {
                                        offsetX.animateTo(maxOffsetPx)
                                        onConfirmed()
                                        delay(600)
                                        offsetX.animateTo(0f)
                                    } else {
                                        offsetX.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { offsetX.animateTo(0f) }
                            },
                        ) { change, delta ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(0f, maxOffsetPx))
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
