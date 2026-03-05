package com.anaglych.squintboyadvance.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full-screen slide-to-confirm overlay for Wear OS.
 *
 * Shows optional [warningText] above the track, then a horizontal drag-to-confirm
 * rail.  Drag the handle all the way right (≥80 % of track width) to trigger
 * [onConfirmed]; releasing early springs the handle back.  [onDismiss] is called
 * when the user taps Cancel.
 */
@Composable
fun WearSlideToConfirm(
    slideText: String = "Slide to confirm",
    warningText: String? = null,
    confirmColor: Color = Color(0xFFEC1358),
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val trackWidthDp: Dp = 200.dp
    val handleDiameter: Dp = 40.dp
    val density = LocalDensity.current
    val trackWidthPx = with(density) { trackWidthDp.toPx() }
    val handlePx = with(density) { handleDiameter.toPx() }
    val maxOffsetPx = trackWidthPx - handlePx
    val threshold = maxOffsetPx * 0.80f

    val offsetX = remember { Animatable(0f) }

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
            // Warning text
            if (!warningText.isNullOrBlank()) {
                Text(
                    text = warningText,
                    fontSize = 11.sp,
                    color = Color(0xFFFFB74D),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            // Slide track
            Box(
                modifier = Modifier
                    .size(trackWidthDp, handleDiameter)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Track fill (grows with drag)
                Box(
                    modifier = Modifier
                        .height(handleDiameter)
                        .fillMaxWidth(fraction = ((offsetX.value + handlePx) / trackWidthPx).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(confirmColor.copy(alpha = 0.35f)),
                )

                // Instruction text (fades as handle moves right)
                val textAlpha = (1f - offsetX.value / (maxOffsetPx * 0.5f)).coerceIn(0f, 1f)
                Text(
                    text = slideText,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = textAlpha * 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = handleDiameter + 4.dp),
                )

                // Draggable handle
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .size(handleDiameter)
                        .clip(CircleShape)
                        .background(confirmColor)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (offsetX.value >= threshold) {
                                            offsetX.animateTo(maxOffsetPx)
                                            onConfirmed()
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
                                    offsetX.snapTo(
                                        (offsetX.value + delta).coerceIn(0f, maxOffsetPx)
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Chevron arrows
                    Text(
                        text = "›",
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier.graphicsLayer {
                            alpha = (1f - offsetX.value / maxOffsetPx * 0.6f).coerceIn(0f, 1f)
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Cancel button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(40.dp),
            ) {
                Text("✕", fontSize = 14.sp)
            }
        }
    }
}
