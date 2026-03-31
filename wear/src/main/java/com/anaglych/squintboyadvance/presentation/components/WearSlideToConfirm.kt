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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.presentation.theme.DangerCrimson
import com.anaglych.squintboyadvance.presentation.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SLIDE_INITIAL       = 0.12f   // resting fill — signals interactivity
private const val SLIDE_PULSE_AMP     = 0.07f   // single-breath amplitude
private const val SLIDE_PULSE_DELAY   = 500L    // ms before pulse begins
private const val SLIDE_THRESHOLD     = 1.0f    // absolute position to fire

/**
 * Full-screen slide-to-confirm overlay for Wear OS.
 *
 * The track fills from the left. After a short idle pause it breathes once to
 * invite interaction. Drag anywhere in the track — position is tracked absolutely
 * so the end is always reachable. Reaching [SLIDE_THRESHOLD] fires [onConfirmed]
 * with haptic feedback and a cancel X sits on the right end of the track.
 */
@Composable
fun WearSlideToConfirm(
    slideText: String = "Slide to confirm",
    warningText: String? = null,
    confirmColor: Color = DangerCrimson,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope  = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var confirmed  by remember { mutableStateOf(false) }
    var progress   by remember { mutableFloatStateOf(0f) }
    var trackWidth by remember { mutableIntStateOf(1) }

    // Single-breath pulse after idle delay; cancels on drag or confirm
    val pulseAnim = remember { Animatable(0f) }
    LaunchedEffect(isDragging, confirmed) {
        if (!isDragging && !confirmed) {
            delay(SLIDE_PULSE_DELAY)
            pulseAnim.animateTo(SLIDE_PULSE_AMP, tween(850, easing = FastOutSlowInEasing))
            pulseAnim.animateTo(0f,              tween(850, easing = FastOutSlowInEasing))
        } else {
            pulseAnim.stop()
            pulseAnim.snapTo(0f)
        }
    }

    val displayProgress = if (isDragging) progress else SLIDE_INITIAL + pulseAnim.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (!warningText.isNullOrBlank()) {
                Text(
                    text       = warningText,
                    fontSize   = 11.sp,
                    color      = WarningAmber,
                    textAlign  = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier   = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            // ── Slide track ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .onSizeChanged { trackWidth = it.width }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val drag = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                            if (drag != null) {
                                isDragging = true
                                progress   = (drag.position.x / trackWidth.toFloat()).coerceIn(0f, 1f)
                                var fired  = false
                                horizontalDrag(drag.id) { change ->
                                    change.consume()
                                    progress = (change.position.x / trackWidth.toFloat()).coerceIn(0f, 1f)
                                    if (!fired && progress >= SLIDE_THRESHOLD) {
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
                // Filled bar
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                        .background(
                            confirmColor.copy(alpha = (0.50f + displayProgress * 0.50f).coerceIn(0f, 1f)),
                            RoundedCornerShape(22.dp),
                        ),
                )
                // Label — fades as bar fills past halfway
                Text(
                    text      = slideText,
                    fontSize  = 11.sp,
                    color     = Color.White.copy(alpha = (0.80f - displayProgress * 1.6f).coerceIn(0f, 0.80f)),
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(end = 36.dp),  // leave room for cancel icon
                )
                // Cancel X — tappable when not dragging
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(44.dp)
                        .then(if (!isDragging) Modifier.clickable(onClick = onDismiss) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint               = Color.White.copy(alpha = if (isDragging) 0.20f else 0.60f),
                        modifier           = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Cancel button — always visible below the track
            Button(
                onClick  = onDismiss,
                colors   = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(40.dp),
            ) {
                Text("✕", fontSize = 14.sp)
            }
        }
    }
}
