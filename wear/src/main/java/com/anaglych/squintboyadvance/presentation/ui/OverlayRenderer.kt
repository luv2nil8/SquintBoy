package com.anaglych.squintboyadvance.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.anaglych.squintboyadvance.shared.model.ButtonId

const val OUTLINE_ALPHA = 0.5f
const val OUTLINE_WIDTH = 2f
const val CORNER_RADIUS_PX = 16f

val GB_GRID = arrayOf(
    ButtonId.SELECT,   ButtonId.DPAD_UP,   ButtonId.START,
    ButtonId.DPAD_LEFT, null,              ButtonId.DPAD_RIGHT,
    ButtonId.B,        ButtonId.DPAD_DOWN, ButtonId.A,
)

val GBA_GRID = arrayOf(
    ButtonId.L,         ButtonId.DPAD_UP,   ButtonId.R,
    ButtonId.DPAD_LEFT, null,               ButtonId.DPAD_RIGHT,
    ButtonId.B,        ButtonId.DPAD_DOWN,  ButtonId.A,
)

fun labelFor(id: ButtonId): String = when (id) {
    ButtonId.A -> "A"
    ButtonId.B -> "B"
    ButtonId.START -> "ST"
    ButtonId.SELECT -> "SE"
    ButtonId.DPAD_UP -> "\u25B2"
    ButtonId.DPAD_DOWN -> "\u25BC"
    ButtonId.DPAD_LEFT -> "\u25C0"
    ButtonId.DPAD_RIGHT -> "\u25B6"
    ButtonId.L -> "L"
    ButtonId.R -> "R"
}

/**
 * Draws grid cell outlines, clipping out [clipPath] (typically the center circle/pause button).
 *
 * @param alpha overall multiplier (e.g. flash animation alpha)
 * @param buttonOpacity base opacity for unpressed buttons
 * @param pressedButtons set of currently pressed buttons (for highlight)
 * @param pressedOpacity opacity for pressed buttons
 */
fun DrawScope.drawOverlayGrid(
    grid: Array<ButtonId?>,
    cellSize: Float,
    alpha: Float,
    buttonOpacity: Float,
    clipPath: Path,
    pressedButtons: Set<ButtonId> = emptySet(),
    pressedOpacity: Float = buttonOpacity,
    outlineColor: Color = Color.Black
) {
    for (row in 0..2) {
        for (col in 0..2) {
            val idx = row * 3 + col
            val btnId = grid[idx] ?: continue

            val cellX = col * cellSize
            val cellY = row * cellSize
            val cellRect = Size(cellSize, cellSize)
            val isPressed = btnId in pressedButtons
            val outlineAlpha = if (isPressed) pressedOpacity else buttonOpacity

            clipPath(clipPath, clipOp = ClipOp.Difference) {
                if (isPressed) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = alpha * pressedOpacity),
                        topLeft = Offset(cellX, cellY),
                        size = cellRect,
                        cornerRadius = CornerRadius(CORNER_RADIUS_PX)
                    )
                }
                drawRoundRect(
                    color = outlineColor.copy(alpha = alpha * outlineAlpha * OUTLINE_ALPHA),
                    topLeft = Offset(cellX, cellY),
                    size = cellRect,
                    cornerRadius = CornerRadius(CORNER_RADIUS_PX),
                    style = Stroke(width = OUTLINE_WIDTH)
                )
            }
        }
    }
}

/**
 * Draws the GBA SE/ST split circle outline with divider, clipping out [pausePath].
 *
 * @param seAlpha opacity for the SE (left) half
 * @param stAlpha opacity for the ST (right) half
 */
fun DrawScope.drawGbaCircle(
    circleCenter: Float,
    circleRadius: Float,
    screenPx: Float,
    alpha: Float,
    pausePath: Path,
    seAlpha: Float,
    stAlpha: Float,
    sePressed: Boolean = false,
    stPressed: Boolean = false,
    outlineColor: Color = Color.Black
) {
    val circleRect = Rect(
        circleCenter - circleRadius,
        circleCenter - circleRadius,
        circleCenter + circleRadius,
        circleCenter + circleRadius
    )

    // Left half (SE)
    val leftClip = Path().apply {
        addRect(Rect(0f, 0f, circleCenter, screenPx))
    }
    clipPath(pausePath, clipOp = ClipOp.Difference) {
        clipPath(leftClip) {
            if (sePressed) {
                drawArc(
                    color = Color.White.copy(alpha = alpha * seAlpha),
                    startAngle = 90f, sweepAngle = 180f, useCenter = true,
                    topLeft = Offset(circleRect.left, circleRect.top),
                    size = Size(circleRect.width, circleRect.height)
                )
            }
            drawArc(
                color = outlineColor.copy(alpha = alpha * seAlpha * OUTLINE_ALPHA),
                startAngle = 90f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(circleRect.left, circleRect.top),
                size = Size(circleRect.width, circleRect.height),
                style = Stroke(width = OUTLINE_WIDTH)
            )
        }
    }

    // Right half (ST)
    val rightClip = Path().apply {
        addRect(Rect(circleCenter, 0f, screenPx, screenPx))
    }
    clipPath(pausePath, clipOp = ClipOp.Difference) {
        clipPath(rightClip) {
            if (stPressed) {
                drawArc(
                    color = Color.White.copy(alpha = alpha * stAlpha),
                    startAngle = 270f, sweepAngle = 180f, useCenter = true,
                    topLeft = Offset(circleRect.left, circleRect.top),
                    size = Size(circleRect.width, circleRect.height)
                )
            }
            drawArc(
                color = outlineColor.copy(alpha = alpha * stAlpha * OUTLINE_ALPHA),
                startAngle = 270f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(circleRect.left, circleRect.top),
                size = Size(circleRect.width, circleRect.height),
                style = Stroke(width = OUTLINE_WIDTH)
            )
        }
    }

    // Divider line between SE/ST
    val divAlpha = (seAlpha + stAlpha) / 2f
    clipPath(pausePath, clipOp = ClipOp.Difference) {
        drawLine(
            color = outlineColor.copy(alpha = alpha * divAlpha * OUTLINE_ALPHA),
            start = Offset(circleCenter, circleCenter - circleRadius),
            end = Offset(circleCenter, circleCenter + circleRadius),
            strokeWidth = OUTLINE_WIDTH
        )
    }
}

/**
 * Renders button labels in an octagonal equidistant pattern from center.
 * Black fill with a 1px primary-color outline stroke for visibility on any background.
 */
@Composable
fun OverlayLabels(
    grid: Array<ButtonId?>,
    screenPx: Float,
    alpha: Float,
    labelOpacity: Float,
    labelSize: Float
) {
    val density = LocalDensity.current
    val cellSize = screenPx / 3f
    val center = screenPx / 2f
    val labelRadius = cellSize
    val diag = labelRadius * 0.707f // cos(45deg) for corner positions

    for (row in 0..2) {
        for (col in 0..2) {
            val idx = row * 3 + col
            val buttonId = grid[idx] ?: continue

            val dx = (col - 1).toFloat()
            val dy = (row - 1).toFloat()
            val isCorner = dx != 0f && dy != 0f

            val labelCx = if (isCorner) center + dx * diag else center + dx * labelRadius
            val labelCy = if (isCorner) center + dy * diag else center + dy * labelRadius

            val labelSizeDp = with(density) { cellSize.toDp() }
            val labelXDp = with(density) { (labelCx - cellSize / 2f).toDp() }
            val labelYDp = with(density) { (labelCy - cellSize / 2f).toDp() }

            Box(
                modifier = Modifier
                    .offset(x = labelXDp, y = labelYDp)
                    .size(labelSizeDp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedLabel(
                    text = labelFor(buttonId),
                    alpha = alpha * labelOpacity,
                    fontSize = labelSize,
                    outlineColor = Color.White
                )
            }
        }
    }
}

/**
 * Renders SE/ST labels in the GBA circle halves.
 */
@Composable
fun GbaCircleLabels(
    circleRadius: Float,
    circleCenter: Float,
    alpha: Float,
    labelOpacity: Float,
    labelSize: Float
) {
    val density = LocalDensity.current
    val circleDp = with(density) { (circleRadius * 2f).toDp() }
    val circleOffDp = with(density) { (circleCenter - circleRadius).toDp() }

    Box(
        modifier = Modifier
            .offset(x = circleOffDp, y = circleOffDp)
            .size(circleDp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.offset(x = -(circleDp / 3.14f))) {
            OutlinedLabel(
                text = "SE",
                alpha = alpha * labelOpacity,
                fontSize = labelSize + 1,
                outlineColor = Color.White
            )
        }
        Box(modifier = Modifier.offset(x = circleDp / 3.14f)) {
            OutlinedLabel(
                text = "ST",
                alpha = alpha * labelOpacity,
                fontSize = labelSize + 1,
                outlineColor = Color.White
            )
        }
    }
}

/**
 * Text label with black fill and a 1px colored outline stroke.
 */
@Composable
private fun OutlinedLabel(
    text: String,
    alpha: Float,
    fontSize: Float,
    outlineColor: Color
) {
    Box(contentAlignment = Alignment.Center) {
        // Outline stroke
        Text(
            text = text,
            color = outlineColor.copy(alpha = alpha),
            fontSize = fontSize.sp,
            style = TextStyle(
                drawStyle = Stroke(width = 3f, join = StrokeJoin.Round),
                textAlign = TextAlign.Center
            )
        )
        // Black fill
        Text(
            text = text,
            color = Color.Black.copy(alpha = alpha),
            fontSize = fontSize.sp,
            style = TextStyle(
                textAlign = TextAlign.Center
            )
        )
    }
}
