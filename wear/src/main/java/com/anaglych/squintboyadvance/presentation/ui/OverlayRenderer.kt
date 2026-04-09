package com.anaglych.squintboyadvance.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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

// ── Layout 2: triangle d-pad + corner buttons ──────────────────────

/** Corner button mapping for GBA Layout 2. */
val GBA_CORNERS = arrayOf(ButtonId.L, ButtonId.R, ButtonId.B, ButtonId.A) // TL, TR, BL, BR

/** Corner button mapping for GB/GBC Layout 2. */
val GB_CORNERS = arrayOf(ButtonId.SELECT, ButtonId.START, ButtonId.B, ButtonId.A) // TL, TR, BL, BR

/**
 * D-pad triangle path builder. Right triangles (90° at the tip) flush with screen edge,
 * tips at circle boundary. Half-base = height so the tip angle is exactly 90°.
 */
fun dpadTrianglePath(dir: ButtonId, screenPx: Float, circleRadius: Float): Path {
    val cx = screenPx / 2f
    val cy = screenPx / 2f
    val h = cx - circleRadius // height from tip to edge = half-base for 90° tip
    return Path().apply {
        when (dir) {
            ButtonId.DPAD_UP -> {
                moveTo(cx - h, 0f); lineTo(cx + h, 0f); lineTo(cx, cy - circleRadius); close()
            }
            ButtonId.DPAD_DOWN -> {
                moveTo(cx - h, screenPx); lineTo(cx + h, screenPx); lineTo(cx, cy + circleRadius); close()
            }
            ButtonId.DPAD_LEFT -> {
                moveTo(0f, cy - h); lineTo(0f, cy + h); lineTo(cx - circleRadius, cy); close()
            }
            ButtonId.DPAD_RIGHT -> {
                moveTo(screenPx, cy - h); lineTo(screenPx, cy + h); lineTo(cx + circleRadius, cy); close()
            }
            else -> {}
        }
    }
}

/** Simple quadrant rectangle path for corner zones. */
private fun quadrantPath(cornerIndex: Int, screenPx: Float): Path {
    val cx = screenPx / 2f
    val cy = screenPx / 2f
    return Path().apply {
        when (cornerIndex) {
            0 -> addRect(Rect(0f, 0f, cx, cy))         // TL
            1 -> addRect(Rect(cx, 0f, screenPx, cy))   // TR
            2 -> addRect(Rect(0f, cy, cx, screenPx))    // BL
            else -> addRect(Rect(cx, cy, screenPx, screenPx)) // BR
        }
    }
}

/**
 * Draws Layout 2 overlay: triangle d-pad + corner button quadrants.
 *
 * Corner zones are full screen quadrants (2×2 grid). D-pad triangles and the center
 * circle/pause are clipped out of them so d-pad appears to "cut into" the corners.
 */
fun DrawScope.drawLayout2(
    screenPx: Float,
    isGba: Boolean,
    circleRadius: Float,
    pauseRadius: Float,
    alpha: Float,
    buttonOpacity: Float,
    pressedOpacity: Float,
    pressedButtons: Set<ButtonId>,
    outlineColor: Color,
    pausePath: Path,
) {
    val corners = if (isGba) GBA_CORNERS else GB_CORNERS
    val dpadDirs = arrayOf(ButtonId.DPAD_UP, ButtonId.DPAD_DOWN, ButtonId.DPAD_LEFT, ButtonId.DPAD_RIGHT)

    // Build a combined clip path: all 4 d-pad triangles + center exclusion
    val dpadClip = Path().apply {
        for (dir in dpadDirs) addPath(dpadTrianglePath(dir, screenPx, circleRadius))
    }
    val centerClip = if (isGba) {
        Path().apply {
            addOval(Rect(
                screenPx / 2f - circleRadius, screenPx / 2f - circleRadius,
                screenPx / 2f + circleRadius, screenPx / 2f + circleRadius,
            ))
        }
    } else pausePath

    // Corner button quadrants — clipped against d-pad triangles and center
    for (i in corners.indices) {
        val btnId = corners[i]
        val quadrant = quadrantPath(i, screenPx)
        val isPressed = btnId in pressedButtons
        val opacity = if (isPressed) pressedOpacity else buttonOpacity

        clipPath(dpadClip, clipOp = ClipOp.Difference) {
            clipPath(centerClip, clipOp = ClipOp.Difference) {
                if (isPressed) {
                    drawPath(quadrant, color = Color.White.copy(alpha = alpha * pressedOpacity))
                }
                drawPath(
                    quadrant,
                    color = outlineColor.copy(alpha = alpha * opacity * OUTLINE_ALPHA),
                    style = Stroke(width = OUTLINE_WIDTH, join = StrokeJoin.Round),
                )
            }
        }
    }

    // D-pad triangles — clipped against center only
    for (dir in dpadDirs) {
        val path = dpadTrianglePath(dir, screenPx, circleRadius)
        val isPressed = dir in pressedButtons
        val opacity = if (isPressed) pressedOpacity else buttonOpacity

        clipPath(pausePath, clipOp = ClipOp.Difference) {
            if (isPressed) {
                drawPath(path, color = Color.White.copy(alpha = alpha * pressedOpacity))
            }
            drawPath(
                path,
                color = outlineColor.copy(alpha = alpha * opacity * OUTLINE_ALPHA),
                style = Stroke(width = OUTLINE_WIDTH, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * Labels for Layout 2 zones — placed at visual centroids.
 */
@Composable
fun Layout2Labels(
    isGba: Boolean,
    screenPx: Float,
    circleRadius: Float,
    pauseRadius: Float,
    alpha: Float,
    labelOpacity: Float,
    labelSize: Float,
) {
    val density = LocalDensity.current
    val corners = if (isGba) GBA_CORNERS else GB_CORNERS
    val innerR = if (isGba) circleRadius else pauseRadius
    val cx = screenPx / 2f

    // D-pad labels at triangle centroids
    val dpadInfo = arrayOf(
        ButtonId.DPAD_UP    to Offset(cx, (0f + 0f + cx - circleRadius) / 3f),
        ButtonId.DPAD_DOWN  to Offset(cx, (screenPx + screenPx + cx + circleRadius) / 3f),
        ButtonId.DPAD_LEFT  to Offset((0f + 0f + cx - circleRadius) / 3f, cx),
        ButtonId.DPAD_RIGHT to Offset((screenPx + screenPx + cx + circleRadius) / 3f, cx),
    )
    for ((btnId, centroid) in dpadInfo) {
        val boxSizePx = screenPx / 4f
        val boxSizeDp = with(density) { boxSizePx.toDp() }
        val xDp = with(density) { (centroid.x - boxSizePx / 2f).toDp() }
        val yDp = with(density) { (centroid.y - boxSizePx / 2f).toDp() }
        Box(
            modifier = Modifier
                .offset(x = xDp, y = yDp)
                .size(boxSizeDp),
            contentAlignment = Alignment.Center,
        ) {
            OutlinedLabel(
                text = labelFor(btnId),
                alpha = alpha * labelOpacity,
                fontSize = labelSize,
                outlineColor = Color.White,
            )
        }
    }

    // Corner labels
    val cornerPositions = arrayOf(
        Offset(screenPx * 0.25f, screenPx * 0.25f), // TL
        Offset(screenPx * 0.75f, screenPx * 0.25f), // TR
        Offset(screenPx * 0.25f, screenPx * 0.75f), // BL
        Offset(screenPx * 0.75f, screenPx * 0.75f), // BR
    )
    for (i in corners.indices) {
        val pos = cornerPositions[i]
        val boxSizePx = screenPx / 4f
        val boxSizeDp = with(density) { boxSizePx.toDp() }
        val xDp = with(density) { (pos.x - boxSizePx / 2f).toDp() }
        val yDp = with(density) { (pos.y - boxSizePx / 2f).toDp() }
        Box(
            modifier = Modifier
                .offset(x = xDp, y = yDp)
                .size(boxSizeDp),
            contentAlignment = Alignment.Center,
        ) {
            OutlinedLabel(
                text = labelFor(corners[i]),
                alpha = alpha * labelOpacity,
                fontSize = labelSize,
                outlineColor = Color.White,
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
                drawStyle = Stroke(width = 4f, join = StrokeJoin.Round),
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
// ── Layout 3: hemicircle A/B + arc Start/Select/L/R ────────────────

/** Radius of the Start/Select (and GBA L/R) arc band in Layout 3 — half the screen width. */
fun layout3ArcRadius(screenPx: Float) = screenPx / 4f

/**
 * Draws Layout 3 overlay:
 * - B (left watch hemicircle) and A (right watch hemicircle) dominate the display
 * - Start/Select: quarter-circles forming a semicircle on the bottom edge, centered
 * - GBA: L/R mirror of Start/Select on the top edge
 * - Two cutout circles (safe d-pad drag zones) vertically centred at ¼ and ¾ screen width
 */
fun DrawScope.drawLayout3(
    screenPx: Float,
    isGba: Boolean,
    dpadRadius: Float,
    pauseRadius: Float,
    alpha: Float,
    buttonOpacity: Float,
    pressedOpacity: Float,
    pressedButtons: Set<ButtonId>,
    outlineColor: Color,
    pausePath: Path,
) {
    val cx = screenPx / 2f
    val cy = screenPx / 2f
    val arcR = layout3ArcRadius(screenPx)
    val cutoutLx = screenPx / 4f
    val cutoutRx = 3f * screenPx / 4f

    // ── Shared clip path builders ────────────────────────────────────
    val bottomArcClip = Path().apply {
        addOval(Rect(cx - arcR, screenPx - arcR, cx + arcR, screenPx + arcR))
    }
    val topArcClip = Path().apply {
        addOval(Rect(cx - arcR, -arcR, cx + arcR, arcR))
    }
    val leftHalf  = Path().apply { addRect(Rect(0f, 0f, cx, screenPx)) }
    val rightHalf = Path().apply { addRect(Rect(cx, 0f, screenPx, screenPx)) }

    // ── Bottom arc: Select (left quarter) + Start (right quarter) ────
    val seIsPressed = ButtonId.SELECT in pressedButtons
    val stIsPressed = ButtonId.START in pressedButtons
    val seOpacity = if (seIsPressed) pressedOpacity else buttonOpacity
    val stOpacity = if (stIsPressed) pressedOpacity else buttonOpacity

    clipPath(leftHalf) {
        if (seIsPressed) drawPath(bottomArcClip, Color.White.copy(alpha = alpha * pressedOpacity))
        drawPath(bottomArcClip, color = outlineColor.copy(alpha = alpha * seOpacity * OUTLINE_ALPHA),
            style = Stroke(width = OUTLINE_WIDTH, join = StrokeJoin.Round))
    }
    clipPath(rightHalf) {
        if (stIsPressed) drawPath(bottomArcClip, Color.White.copy(alpha = alpha * pressedOpacity))
        drawPath(bottomArcClip, color = outlineColor.copy(alpha = alpha * stOpacity * OUTLINE_ALPHA),
            style = Stroke(width = OUTLINE_WIDTH, join = StrokeJoin.Round))
    }
    drawLine(
        color = outlineColor.copy(alpha = alpha * ((seOpacity + stOpacity) / 2f) * OUTLINE_ALPHA),
        start = Offset(cx, screenPx - arcR), end = Offset(cx, screenPx),
        strokeWidth = OUTLINE_WIDTH,
    )

    // ── Top arc: L (left quarter) + R (right quarter) — GBA only ─────
    if (isGba) {
        val lIsPressed = ButtonId.L in pressedButtons
        val rIsPressed = ButtonId.R in pressedButtons
        val lOpacity = if (lIsPressed) pressedOpacity else buttonOpacity
        val rOpacity = if (rIsPressed) pressedOpacity else buttonOpacity

        clipPath(leftHalf) {
            if (lIsPressed) drawPath(topArcClip, Color.White.copy(alpha = alpha * pressedOpacity))
            drawPath(topArcClip, color = outlineColor.copy(alpha = alpha * lOpacity * OUTLINE_ALPHA),
                style = Stroke(width = OUTLINE_WIDTH, join = StrokeJoin.Round))
        }
        clipPath(rightHalf) {
            if (rIsPressed) drawPath(topArcClip, Color.White.copy(alpha = alpha * pressedOpacity))
            drawPath(topArcClip, color = outlineColor.copy(alpha = alpha * rOpacity * OUTLINE_ALPHA),
                style = Stroke(width = OUTLINE_WIDTH, join = StrokeJoin.Round))
        }
        drawLine(
            color = outlineColor.copy(alpha = alpha * ((lOpacity + rOpacity) / 2f) * OUTLINE_ALPHA),
            start = Offset(cx, 0f), end = Offset(cx, arcR),
            strokeWidth = OUTLINE_WIDTH,
        )
    }

    // ── Cutout circle outlines (safe d-pad drag zones) ────────────────
    val cutoutAlpha = buttonOpacity * 0.7f
    drawCircle(
        color = outlineColor.copy(alpha = alpha * cutoutAlpha * OUTLINE_ALPHA),
        radius = pauseRadius,
        center = Offset(cutoutLx, cy),
        style = Stroke(width = OUTLINE_WIDTH),
    )
    drawCircle(
        color = outlineColor.copy(alpha = alpha * cutoutAlpha * OUTLINE_ALPHA),
        radius = pauseRadius,
        center = Offset(cutoutRx, cy),
        style = Stroke(width = OUTLINE_WIDTH),
    )
}

/**
 * Labels for Layout 3 zones.
 */
@Composable
fun Layout3Labels(
    isGba: Boolean,
    screenPx: Float,
    dpadRadius: Float,
    alpha: Float,
    labelOpacity: Float,
    labelSize: Float,
) {
    val density = LocalDensity.current
    val cx = screenPx / 2f
    val arcR = layout3ArcRadius(screenPx)

    // Start/Select and (GBA) L/R arc labels only — A/B/dpad are shown via floating indicator
    val arcInfo = buildList {
        add(ButtonId.SELECT to Offset(cx - arcR * 0.5f, screenPx - arcR * 0.5f))
        add(ButtonId.START  to Offset(cx + arcR * 0.5f, screenPx - arcR * 0.5f))
        if (isGba) {
            add(ButtonId.L to Offset(cx - arcR * 0.5f, arcR * 0.5f))
            add(ButtonId.R to Offset(cx + arcR * 0.5f, arcR * 0.5f))
        }
    }

    for ((btnId, centroid) in arcInfo) {
        val boxSizePx = screenPx / 4f
        val boxSizeDp = with(density) { boxSizePx.toDp() }
        val xDp = with(density) { (centroid.x - boxSizePx / 2f).toDp() }
        val yDp = with(density) { (centroid.y - boxSizePx / 2f).toDp() }
        Box(
            modifier = Modifier.offset(x = xDp, y = yDp).size(boxSizeDp),
            contentAlignment = Alignment.Center,
        ) {
            OutlinedLabel(
                text = labelFor(btnId),
                alpha = alpha * labelOpacity,
                fontSize = labelSize,
                outlineColor = Color.White,
            )
        }
    }
}

/*
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
*/