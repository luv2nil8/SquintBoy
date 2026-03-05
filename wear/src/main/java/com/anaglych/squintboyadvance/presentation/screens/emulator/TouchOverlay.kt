package com.anaglych.squintboyadvance.presentation.screens.emulator

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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
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
import com.anaglych.squintboyadvance.shared.model.ButtonId
import com.anaglych.squintboyadvance.shared.model.SystemType
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    visible: Boolean = true,
    buttonOpacity: Float = 0.3f,
    pressedOpacity: Float = 0.6f,
    labelOpacity: Float = 0.8f,
    labelSize: Float = 10f,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isGba = systemType == SystemType.GBA

    // Flash animation: used when overlay is hidden (visible=false) for full reminder flash
    val flashAlpha = remember { Animatable(0f) }
    // Pause fade: when visible, pause starts solid red then fades to match buttons
    val pauseFade = remember { Animatable(1f) }
    LaunchedEffect(visible) {
        if (!visible) {
            flashAlpha.snapTo(0f)
            flashAlpha.animateTo(1f, tween(1000))
            flashAlpha.animateTo(0f, tween(1000))
        } else {
            pauseFade.snapTo(1f)
            delay(500)
            pauseFade.animateTo(0f, tween(1500))
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenPx = with(LocalDensity.current) { maxWidth.toPx() }
        val cellSize = screenPx / 3f

        val grid = if (isGba) GBA_GRID else GB_GRID
        val circleRadius = if (isGba) screenPx * 0.25f else 0f
        val circleCenter = screenPx / 2f

        // Track which pointers are pressing which buttons
        val activeButtons = remember { mutableStateMapOf<PointerId, ButtonId>() }

        // Derive the set of currently pressed button IDs for visual feedback
        val pressedButtons = activeButtons.values.toSet()
        val density = LocalDensity.current

        // When visible: steady display with configured settings
        // When hidden: brief flash with default values
        val drawAlpha = if (visible) 1f else flashAlpha.value
        val drawButtonOpacity = if (visible) buttonOpacity else 0.3f
        val drawPressedOpacity = if (visible) pressedOpacity else 0.6f
        val drawLabelOpacity = if (visible) labelOpacity else 0.8f
        val drawLabelSize = if (visible) labelSize else 10f
        // Pause fade: 1 = solid red, 0 = matches button style
        val pf = if (visible) pauseFade.value else 1f

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
                                                        if (hapticEnabled) haptic.performHapticFeedback(
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
                                                        if (hapticEnabled) haptic.performHapticFeedback(
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
                    if (drawAlpha == 0f) return@drawBehind

                    val cx = screenPx / 2f
                    val cy = screenPx / 2f
                    val pauseRadius = cellSize * 0.22f

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
                            val btnId = grid[idx] ?: continue

                            val cellX = col * cellSize
                            val cellY = row * cellSize
                            val cellRect = Size(cellSize, cellSize)
                            val isPressed = btnId in pressedButtons
                            val cellAlpha = if (isPressed) drawPressedOpacity else drawButtonOpacity

                            val drawCell = {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = drawAlpha * cellAlpha),
                                    topLeft = Offset(cellX, cellY),
                                    size = cellRect,
                                    cornerRadius = CornerRadius(CORNER_RADIUS_PX)
                                )
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = drawAlpha * cellAlpha * OUTLINE_ALPHA),
                                    topLeft = Offset(cellX, cellY),
                                    size = cellRect,
                                    cornerRadius = CornerRadius(CORNER_RADIUS_PX),
                                    style = Stroke(width = OUTLINE_WIDTH)
                                )
                            }

                            if (circlePath != null) {
                                clipPath(circlePath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                                    drawCell()
                                }
                            } else {
                                clipPath(pausePath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                                    drawCell()
                                }
                            }
                        }
                    }

                    // 2) GBA SE/ST — two independent semi-circles
                    if (isGba) {
                        val sePressed = ButtonId.SELECT in pressedButtons
                        val stPressed = ButtonId.START in pressedButtons
                        val seAlpha = if (sePressed) drawPressedOpacity else drawButtonOpacity
                        val stAlpha = if (stPressed) drawPressedOpacity else drawButtonOpacity

                        val circleRect = androidx.compose.ui.geometry.Rect(
                            circleCenter - circleRadius,
                            circleCenter - circleRadius,
                            circleCenter + circleRadius,
                            circleCenter + circleRadius
                        )

                        // Left half (SE) — clip to left of center, then clip out pause
                        val leftClip = Path().apply {
                            addRect(androidx.compose.ui.geometry.Rect(
                                0f, 0f, circleCenter, screenPx
                            ))
                        }
                        clipPath(pausePath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                            clipPath(leftClip) {
                                drawArc(
                                    color = Color.White.copy(alpha = drawAlpha * seAlpha),
                                    startAngle = 90f, sweepAngle = 180f, useCenter = true,
                                    topLeft = Offset(circleRect.left, circleRect.top),
                                    size = Size(circleRect.width, circleRect.height)
                                )
                                drawArc(
                                    color = Color.Black.copy(alpha = drawAlpha * seAlpha * OUTLINE_ALPHA),
                                    startAngle = 90f, sweepAngle = 180f, useCenter = false,
                                    topLeft = Offset(circleRect.left, circleRect.top),
                                    size = Size(circleRect.width, circleRect.height),
                                    style = Stroke(width = OUTLINE_WIDTH)
                                )
                            }
                        }

                        // Right half (ST) — clip to right of center, then clip out pause
                        val rightClip = Path().apply {
                            addRect(androidx.compose.ui.geometry.Rect(
                                circleCenter, 0f, screenPx, screenPx
                            ))
                        }
                        clipPath(pausePath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                            clipPath(rightClip) {
                                drawArc(
                                    color = Color.White.copy(alpha = drawAlpha * stAlpha),
                                    startAngle = 270f, sweepAngle = 180f, useCenter = true,
                                    topLeft = Offset(circleRect.left, circleRect.top),
                                    size = Size(circleRect.width, circleRect.height)
                                )
                                drawArc(
                                    color = Color.Black.copy(alpha = drawAlpha * stAlpha * OUTLINE_ALPHA),
                                    startAngle = 270f, sweepAngle = 180f, useCenter = false,
                                    topLeft = Offset(circleRect.left, circleRect.top),
                                    size = Size(circleRect.width, circleRect.height),
                                    style = Stroke(width = OUTLINE_WIDTH)
                                )
                            }
                        }

                        // Divider line between SE/ST
                        val divAlpha = (seAlpha + stAlpha) / 2f
                        clipPath(pausePath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
                            drawLine(
                                color = Color.Black.copy(alpha = drawAlpha * divAlpha * OUTLINE_ALPHA),
                                start = Offset(circleCenter, circleCenter - circleRadius),
                                end = Offset(circleCenter, circleCenter + circleRadius),
                                strokeWidth = 3f
                            )
                        }
                    }

                    // 3) Pause button — starts solid red, fades to match buttons
                    val pauseRed = Color(0xFFFF1744)
                    // Lerp fill: solid red → White at buttonOpacity
                    val pauseFillColor = lerp(Color.White, pauseRed, pf)
                    val pauseFillAlpha = drawAlpha * lerp(drawButtonOpacity, 1f, pf)
                    drawCircle(
                        color = pauseFillColor.copy(alpha = pauseFillAlpha),
                        radius = pauseRadius,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = drawAlpha * pauseFillAlpha * OUTLINE_ALPHA),
                        radius = pauseRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = OUTLINE_WIDTH)
                    )

                    // Pause bars — fade from bright to label opacity
                    val barW = 3f
                    val barH = pauseRadius * 0.7f
                    val barGap = 3.5f
                    val barAlpha = drawAlpha * lerp(drawLabelOpacity, 0.9f, pf)
                    drawRect(
                        color = Color.White.copy(alpha = barAlpha),
                        topLeft = Offset(cx - barGap - barW, cy - barH / 2),
                        size = Size(barW, barH)
                    )
                    drawRect(
                        color = Color.White.copy(alpha = barAlpha),
                        topLeft = Offset(cx + barGap, cy - barH / 2),
                        size = Size(barW, barH)
                    )
                }
        ) {
            // Text labels — shown when visible or during flash
            if (drawAlpha > 0f) {
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
                                color = Color.White.copy(alpha = drawAlpha * drawLabelOpacity),
                                fontSize = drawLabelSize.sp,
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
                            color = Color.White.copy(alpha = drawAlpha * drawLabelOpacity),
                            fontSize = (drawLabelSize + 1).sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.offset(x = -(circleDp / 4))
                        )
                        Text(
                            text = "ST",
                            color = Color.White.copy(alpha = drawAlpha * drawLabelOpacity),
                            fontSize = (drawLabelSize + 1).sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.offset(x = circleDp / 4)
                        )
                    }
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
