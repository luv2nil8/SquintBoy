package com.example.squintboyadvance.presentation.screens.emulator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.squintboyadvance.shared.model.ButtonId
import com.example.squintboyadvance.shared.model.SystemType
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val BG_ALPHA = 0.3f
private const val OUTLINE_ALPHA = 0.5f
private const val OUTLINE_WIDTH = 2f
private const val LONG_PRESS_MS = 500L
private val CORNER_RADIUS_PX = 16f

private val GB_GRID = arrayOf(
    ButtonId.SELECT,   ButtonId.DPAD_UP,   ButtonId.START,
    ButtonId.DPAD_LEFT, null,              ButtonId.DPAD_RIGHT,
    ButtonId.B,        ButtonId.DPAD_DOWN, ButtonId.A,
)

private val GBA_GRID = arrayOf(
    ButtonId.L,         ButtonId.DPAD_UP,   ButtonId.R,
    ButtonId.DPAD_LEFT, null,               ButtonId.DPAD_RIGHT,
    ButtonId.B,        ButtonId.DPAD_DOWN,  ButtonId.A,
)

private fun labelFor(id: ButtonId): String = when (id) {
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

@Composable
fun TouchOverlay(
    systemType: SystemType?,
    onButtonPress: (ButtonId) -> Unit,
    onButtonRelease: (ButtonId) -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isGba = systemType == SystemType.GBA

    // Label flash animation: 0 → 1 → 0 (notify then vanish)
    val labelAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        labelAlpha.animateTo(1f, tween(1000))
        labelAlpha.animateTo(0f, tween(1000))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenPx = with(LocalDensity.current) { maxWidth.toPx() }
        val cellSize = screenPx / 3f

        val grid = if (isGba) GBA_GRID else GB_GRID
        val circleRadius = if (isGba) screenPx * 0.25f else 0f
        val circleCenter = screenPx / 2f

        // Track which pointers are pressing which buttons
        val activeButtons = remember { mutableStateMapOf<PointerId, ButtonId>() }

        val alpha = labelAlpha.value
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(systemType) {
                    coroutineScope {
                        awaitPointerEventScope {
                            // Track long-press jobs for center cell
                            val longPressJobs = mutableMapOf<PointerId, Job>()

                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes

                                when (event.type) {
                                    PointerEventType.Press, PointerEventType.Move -> {
                                        for (change in changes) {
                                            if (!change.pressed) continue
                                            val pos = change.position

                                            // Check if pointer is in center cell
                                            val inCenter = isInCenterCell(
                                                pos, cellSize
                                            )

                                            if (inCenter) {
                                                // For GBA, check circle halves first (tap)
                                                if (isGba) {
                                                    val circleBtn = hitTestCircle(
                                                        pos, screenPx
                                                    )
                                                    val prevBtn = activeButtons[change.id]
                                                    if (circleBtn != null && circleBtn != prevBtn) {
                                                        if (prevBtn != null) onButtonRelease(prevBtn)
                                                        activeButtons[change.id] = circleBtn
                                                        onButtonPress(circleBtn)
                                                        if (event.type == PointerEventType.Press) {
                                                            haptic.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove
                                                            )
                                                        }
                                                    }
                                                }

                                                // Start long-press timer on initial press
                                                if (event.type == PointerEventType.Press &&
                                                    longPressJobs[change.id] == null
                                                ) {
                                                    longPressJobs[change.id] = launch {
                                                        delay(LONG_PRESS_MS)
                                                        // Release any active button first
                                                        val btn = activeButtons.remove(change.id)
                                                        if (btn != null) onButtonRelease(btn)
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        onPause()
                                                    }
                                                }
                                                change.consume()
                                                continue
                                            }

                                            // Pointer moved out of center — cancel long-press
                                            longPressJobs.remove(change.id)?.cancel()

                                            // Normal grid hit test
                                            val btn = hitTestGrid(pos, cellSize, grid)

                                            val prevBtn = activeButtons[change.id]
                                            if (btn != prevBtn) {
                                                if (prevBtn != null) onButtonRelease(prevBtn)
                                                if (btn != null) {
                                                    activeButtons[change.id] = btn
                                                    onButtonPress(btn)
                                                    if (event.type == PointerEventType.Press) {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove
                                                        )
                                                    }
                                                } else {
                                                    activeButtons.remove(change.id)
                                                }
                                            }
                                            change.consume()
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        for (change in changes) {
                                            longPressJobs.remove(change.id)?.cancel()
                                            val prevBtn = activeButtons.remove(change.id)
                                            if (prevBtn != null) onButtonRelease(prevBtn)
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .drawBehind {
                    val cx = screenPx / 2f
                    val cy = screenPx / 2f
                    val pauseRadius = cellSize * 0.22f
                    val pauseRed = Color(0xFFFF1744)

                    // Pause circle clip path (used by both modes)
                    val pausePath = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                cx - pauseRadius, cy - pauseRadius,
                                cx + pauseRadius, cy + pauseRadius
                            )
                        )
                    }

                    // Split circle clip path for GBA
                    val circlePath = if (isGba) {
                        Path().apply {
                            addOval(
                                androidx.compose.ui.geometry.Rect(
                                    circleCenter - circleRadius,
                                    circleCenter - circleRadius,
                                    circleCenter + circleRadius,
                                    circleCenter + circleRadius
                                )
                            )
                        }
                    } else null

                    // 1) Grid cells — clip out split circle (GBA) and pause circle
                    for (row in 0..2) {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            if (grid[idx] == null) continue

                            val cellX = col * cellSize
                            val cellY = row * cellSize
                            val cellRect = Size(cellSize, cellSize)

                            val drawCell = {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = alpha * BG_ALPHA),
                                    topLeft = Offset(cellX, cellY),
                                    size = cellRect,
                                    cornerRadius = CornerRadius(CORNER_RADIUS_PX)
                                )
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = alpha * OUTLINE_ALPHA),
                                    topLeft = Offset(cellX, cellY),
                                    size = cellRect,
                                    cornerRadius = CornerRadius(CORNER_RADIUS_PX),
                                    style = Stroke(width = OUTLINE_WIDTH)
                                )
                            }

                            if (circlePath != null) {
                                // GBA: clip out both split circle and pause circle
                                clipPath(circlePath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                                    drawCell()
                                }
                            } else {
                                // GB/GBC: clip out pause circle from center-adjacent cells
                                clipPath(pausePath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                                    drawCell()
                                }
                            }
                        }
                    }

                    // 2) GBA split circle — clip out pause circle
                    if (isGba) {
                        clipPath(pausePath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                            drawCircle(
                                color = Color.White.copy(alpha = alpha * BG_ALPHA),
                                radius = circleRadius,
                                center = Offset(circleCenter, circleCenter)
                            )
                            drawCircle(
                                color = Color.Black.copy(alpha = alpha * OUTLINE_ALPHA),
                                radius = circleRadius,
                                center = Offset(circleCenter, circleCenter),
                                style = Stroke(width = OUTLINE_WIDTH)
                            )
                            // Divider line — full height of circle
                            drawLine(
                                color = Color.Black.copy(alpha = alpha * OUTLINE_ALPHA),
                                start = Offset(circleCenter, circleCenter - circleRadius),
                                end = Offset(circleCenter, circleCenter + circleRadius),
                                strokeWidth = 3f
                            )
                        }
                    }

                    // 3) Pause button circle — red fill + dark outline
                    drawCircle(
                        color = pauseRed.copy(alpha = alpha),
                        radius = pauseRadius,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = alpha * OUTLINE_ALPHA),
                        radius = pauseRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = OUTLINE_WIDTH)
                    )

                    // Pause bars (white icon on red circle)
                    val barW = 3f
                    val barH = pauseRadius * 0.7f
                    val barGap = 3.5f
                    drawRect(
                        color = Color.White.copy(alpha = alpha * 0.9f),
                        topLeft = Offset(cx - barGap - barW, cy - barH / 2),
                        size = Size(barW, barH)
                    )
                    drawRect(
                        color = Color.White.copy(alpha = alpha * 0.9f),
                        topLeft = Offset(cx + barGap, cy - barH / 2),
                        size = Size(barW, barH)
                    )
                }
        ) {
            // Text labels for grid cells
            for (row in 0..2) {
                for (col in 0..2) {
                    val idx = row * 3 + col
                    val buttonId = grid[idx] ?: continue

                    val cellX = col * cellSize
                    val cellY = row * cellSize
                    val cellDp = with(density) { cellSize.toDp() }
                    val offsetXDp = with(density) { cellX.toDp() }
                    val offsetYDp = with(density) { cellY.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(x = offsetXDp, y = offsetYDp)
                            .size(cellDp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelFor(buttonId),
                            color = Color.White.copy(alpha = alpha * 0.8f),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // GBA: SE/ST labels in circle halves
            if (isGba) {
                val circleDp = with(density) { (circleRadius * 2f).toDp() }
                val circleOffDp = with(density) { (circleCenter - circleRadius).toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = circleOffDp, y = circleOffDp)
                        .size(circleDp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SE",
                        color = Color.White.copy(alpha = alpha * 0.9f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.offset(x = -(circleDp / 4))
                    )
                    Text(
                        text = "ST",
                        color = Color.White.copy(alpha = alpha * 0.9f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.offset(x = circleDp / 4)
                    )
                }
            }
        }
    }
}

private fun isInCenterCell(pos: Offset, cellSize: Float): Boolean {
    val col = (pos.x / cellSize).toInt()
    val row = (pos.y / cellSize).toInt()
    return col == 1 && row == 1
}

private fun hitTestGrid(
    pos: Offset,
    cellSize: Float,
    grid: Array<ButtonId?>
): ButtonId? {
    val col = (pos.x / cellSize).toInt()
    val row = (pos.y / cellSize).toInt()
    if (col !in 0..2 || row !in 0..2) return null
    return grid[row * 3 + col]
}

private fun hitTestCircle(
    pos: Offset,
    screenSize: Float
): ButtonId? {
    val center = screenSize / 2f
    val radius = screenSize * 0.25f
    val dx = pos.x - center
    val dy = pos.y - center
    if (dx * dx + dy * dy > radius * radius) return null
    return if (dx < 0) ButtonId.SELECT else ButtonId.START
}
