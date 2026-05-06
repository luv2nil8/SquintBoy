package com.anaglych.squintboyadvance.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INITIAL     = 0.12f
private const val PULSE_AMP   = 0.07f
private const val PULSE_DELAY = 500L
private const val THRESHOLD   = 1.0f

/**
 * Compact swipe-to-confirm bar. Absolute position tracking — the end is always
 * reachable regardless of where the drag begins. Single-breath idle pulse after a
 * short delay. Fires at full swipe with haptic feedback.
 * Cancel X on the right end, tappable only when not dragging.
 */
@Composable
fun CompactSwipeBar(
    slideText: String,
    color: Color,
    onConfirmed: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope  = rememberCoroutineScope()

    var progress   by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var confirmed  by remember { mutableStateOf(false) }

    val pulseAnim = remember { Animatable(0f) }
    LaunchedEffect(isDragging, confirmed) {
        if (!isDragging && !confirmed) {
            delay(PULSE_DELAY)
            pulseAnim.animateTo(PULSE_AMP, tween(850, easing = FastOutSlowInEasing))
            pulseAnim.animateTo(0f,        tween(850, easing = FastOutSlowInEasing))
        } else {
            pulseAnim.stop()
            pulseAnim.snapTo(0f)
        }
    }

    val displayProgress = if (isDragging) progress else INITIAL + pulseAnim.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.18f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val drag = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                    if (drag != null) {
                        isDragging = true
                        progress   = (drag.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        var fired  = false
                        horizontalDrag(drag.id) { change ->
                            change.consume()
                            progress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            if (!fired && progress >= THRESHOLD) {
                                fired     = true
                                confirmed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirmed()
                            }
                        }
                        isDragging = false
                        if (!fired) {
                            val from = progress
                            scope.launch {
                                animate(
                                    initialValue  = from,
                                    targetValue   = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness    = Spring.StiffnessMedium,
                                    ),
                                ) { v, _ -> progress = v }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                .background(
                    color.copy(alpha = (0.45f + displayProgress * 0.55f).coerceIn(0f, 1f)),
                    RoundedCornerShape(18.dp),
                ),
        )
        Text(
            text      = slideText,
            style     = MaterialTheme.typography.caption2,
            color     = Color.White.copy(alpha = (0.80f - displayProgress * 1.6f).coerceIn(0f, 0.80f)),
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .padding(end = 36.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(36.dp)
                .then(if (!isDragging) Modifier.clickable(onClick = onCancel) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.Close,
                contentDescription = "Cancel",
                tint               = Color.White.copy(alpha = if (isDragging) 0.20f else 0.60f),
                modifier           = Modifier.size(13.dp),
            )
        }
    }
}
