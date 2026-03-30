package com.anaglych.squintboyadvance.presentation.screens.emulator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.anaglych.squintboyadvance.shared.model.ButtonId
import com.anaglych.squintboyadvance.shared.model.SystemType
import com.anaglych.squintboyadvance.presentation.ui.GB_GRID
import com.anaglych.squintboyadvance.presentation.ui.GBA_GRID
import com.anaglych.squintboyadvance.presentation.ui.OUTLINE_ALPHA
import com.anaglych.squintboyadvance.presentation.ui.OUTLINE_WIDTH
import com.anaglych.squintboyadvance.presentation.ui.drawOverlayGrid
import com.anaglych.squintboyadvance.presentation.ui.drawGbaCircle
import com.anaglych.squintboyadvance.presentation.ui.OverlayLabels
import com.anaglych.squintboyadvance.presentation.ui.GbaCircleLabels
import com.anaglych.squintboyadvance.presentation.ui.drawLayout2
import com.anaglych.squintboyadvance.presentation.ui.Layout2Labels
import com.anaglych.squintboyadvance.presentation.ui.GBA_CORNERS
import com.anaglych.squintboyadvance.presentation.ui.GB_CORNERS
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 500L

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
    labelSize: Float = 14f,
    hapticEnabled: Boolean = true,
    layoutType: Int = 0,
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
        val gbaCircleRadius = screenPx * 0.25f
        val circleRadius = if (isGba) gbaCircleRadius else 0f
        val circleCenter = screenPx / 2f
        // Layout 2 always uses GBA circle radius for d-pad triangle geometry
        val layout2DpadRadius = gbaCircleRadius

        // Track which pointers are pressing which buttons
        val activeButtons = remember { mutableStateMapOf<PointerId, ButtonId>() }

        // Derive the set of currently pressed button IDs for visual feedback
        val pressedButtons = activeButtons.values.toSet()

        // When visible: steady display with configured settings
        // When hidden: brief flash with default values
        val drawAlpha = if (visible) 1f else flashAlpha.value
        val drawButtonOpacity = if (visible) buttonOpacity else 1f
        val drawPressedOpacity = if (visible) pressedOpacity else 0.6f
        val drawLabelOpacity = if (visible) labelOpacity else 0.8f
        val drawLabelSize = if (visible) labelSize else 13f
        val drawOutlineColor = Color.Black
        // Pause fade: 1 = solid red, 0 = matches button style
        val pf = if (visible) pauseFade.value else 1f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(systemType, hapticEnabled, layoutType) {
                    val pauseRadius = cellSize * 0.22f

                    coroutineScope {
                        awaitPointerEventScope {
                            val longPressJobs = mutableMapOf<PointerId, Job>()

                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes

                                when (event.type) {
                                    PointerEventType.Press, PointerEventType.Move -> {
                                        for (change in changes) {
                                            if (!change.pressed) continue
                                            val pos = change.position

                                            if (layoutType == 1) {
                                                // ── Layout 2 hit testing ──
                                                val cx = screenPx / 2f
                                                val cy = screenPx / 2f

                                                // 1) Pause button (small circle in center)
                                                val dx = pos.x - cx
                                                val dy = pos.y - cy
                                                val inPause = dx * dx + dy * dy <= pauseRadius * pauseRadius

                                                if (inPause) {
                                                    if (event.type == PointerEventType.Press &&
                                                        longPressJobs[change.id] == null
                                                    ) {
                                                        longPressJobs[change.id] = launch {
                                                            delay(LONG_PRESS_MS)
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

                                                // Cancel long-press if moved out of pause
                                                longPressJobs.remove(change.id)?.cancel()

                                                // 2) GBA SE/ST circle (same as layout 1)
                                                if (isGba) {
                                                    val circleBtn = hitTestCircle(pos, screenPx)
                                                    if (circleBtn != null) {
                                                        val prevBtn = activeButtons[change.id]
                                                        if (circleBtn != prevBtn) {
                                                            if (prevBtn != null) onButtonRelease(prevBtn)
                                                            activeButtons[change.id] = circleBtn
                                                            onButtonPress(circleBtn)
                                                            if (event.type == PointerEventType.Press && hapticEnabled) {
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.TextHandleMove
                                                                )
                                                            }
                                                        }
                                                        change.consume()
                                                        continue
                                                    }
                                                }

                                                // 3) D-pad triangles + corner zones
                                                val btn = hitTestLayout2(
                                                    pos, screenPx, layout2DpadRadius, isGba
                                                )

                                                val prevBtn = activeButtons[change.id]
                                                if (btn != prevBtn) {
                                                    if (prevBtn != null) onButtonRelease(prevBtn)
                                                    if (btn != null) {
                                                        activeButtons[change.id] = btn
                                                        onButtonPress(btn)
                                                        if (event.type == PointerEventType.Press && hapticEnabled) {
                                                            haptic.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove
                                                            )
                                                        }
                                                    } else {
                                                        activeButtons.remove(change.id)
                                                    }
                                                }
                                                change.consume()
                                            } else {
                                                // ── Layout 1 (original grid) ──
                                                val inCenter = isInCenterCell(pos, cellSize)

                                                if (inCenter) {
                                                    if (isGba) {
                                                        val circleBtn = hitTestCircle(pos, screenPx)
                                                        val prevBtn = activeButtons[change.id]
                                                        if (circleBtn != null && circleBtn != prevBtn) {
                                                            if (prevBtn != null) onButtonRelease(prevBtn)
                                                            activeButtons[change.id] = circleBtn
                                                            onButtonPress(circleBtn)
                                                            if (event.type == PointerEventType.Press && hapticEnabled) {
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.TextHandleMove
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (event.type == PointerEventType.Press &&
                                                        longPressJobs[change.id] == null
                                                    ) {
                                                        longPressJobs[change.id] = launch {
                                                            delay(LONG_PRESS_MS)
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

                                                longPressJobs.remove(change.id)?.cancel()

                                                val btn = hitTestGrid(pos, cellSize, grid)
                                                val prevBtn = activeButtons[change.id]
                                                if (btn != prevBtn) {
                                                    if (prevBtn != null) onButtonRelease(prevBtn)
                                                    if (btn != null) {
                                                        activeButtons[change.id] = btn
                                                        onButtonPress(btn)
                                                        if (event.type == PointerEventType.Press && hapticEnabled) {
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

                    // Pause circle clip path (used by both layouts)
                    val pausePath = Path().apply {
                        addOval(Rect(
                            cx - pauseRadius, cy - pauseRadius,
                            cx + pauseRadius, cy + pauseRadius
                        ))
                    }

                    if (layoutType == 1) {
                        // ── Layout 2: triangle d-pad + corner zones ──
                        drawLayout2(
                            screenPx = screenPx,
                            isGba = isGba,
                            circleRadius = layout2DpadRadius,
                            pauseRadius = pauseRadius,
                            alpha = drawAlpha,
                            buttonOpacity = drawButtonOpacity,
                            pressedOpacity = drawPressedOpacity,
                            pressedButtons = pressedButtons,
                            outlineColor = drawOutlineColor,
                            pausePath = pausePath,
                        )

                        // GBA SE/ST split circle (same as layout 1)
                        if (isGba) {
                            val seIsPressed = ButtonId.SELECT in pressedButtons
                            val stIsPressed = ButtonId.START in pressedButtons
                            val seAlpha = if (seIsPressed) drawPressedOpacity else drawButtonOpacity
                            val stAlpha = if (stIsPressed) drawPressedOpacity else drawButtonOpacity

                            drawGbaCircle(
                                circleCenter = circleCenter,
                                circleRadius = circleRadius,
                                screenPx = screenPx,
                                alpha = drawAlpha,
                                pausePath = pausePath,
                                seAlpha = seAlpha,
                                stAlpha = stAlpha,
                                sePressed = seIsPressed,
                                stPressed = stIsPressed,
                                outlineColor = drawOutlineColor,
                            )
                        }
                    } else {
                        // ── Layout 1: original grid ──
                        val gridClipPath = if (isGba) {
                            Path().apply {
                                addOval(Rect(
                                    circleCenter - circleRadius,
                                    circleCenter - circleRadius,
                                    circleCenter + circleRadius,
                                    circleCenter + circleRadius
                                ))
                            }
                        } else {
                            pausePath
                        }

                        drawOverlayGrid(
                            grid = grid,
                            cellSize = cellSize,
                            alpha = drawAlpha,
                            buttonOpacity = drawButtonOpacity,
                            clipPath = gridClipPath,
                            pressedButtons = pressedButtons,
                            pressedOpacity = drawPressedOpacity,
                            outlineColor = drawOutlineColor,
                        )

                        if (isGba) {
                            val seIsPressed = ButtonId.SELECT in pressedButtons
                            val stIsPressed = ButtonId.START in pressedButtons
                            val seAlpha = if (seIsPressed) drawPressedOpacity else drawButtonOpacity
                            val stAlpha = if (stIsPressed) drawPressedOpacity else drawButtonOpacity

                            drawGbaCircle(
                                circleCenter = circleCenter,
                                circleRadius = circleRadius,
                                screenPx = screenPx,
                                alpha = drawAlpha,
                                pausePath = pausePath,
                                seAlpha = seAlpha,
                                stAlpha = stAlpha,
                                sePressed = seIsPressed,
                                stPressed = stIsPressed,
                                outlineColor = drawOutlineColor,
                            )
                        }
                    }

                    // Pause button — starts solid red, fades to outline-only
                    val pauseRed = Color(0xFFEC1358)
                    if (pf > 0f) {
                        drawCircle(
                            color = pauseRed.copy(alpha = drawAlpha * pf),
                            radius = pauseRadius,
                            center = Offset(cx, cy),
                        )
                    }
                    val pauseOutlineAlpha = drawAlpha * drawButtonOpacity * OUTLINE_ALPHA
                    drawCircle(
                        color = Color.Black.copy(alpha = pauseOutlineAlpha),
                        radius = pauseRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = OUTLINE_WIDTH),
                    )

                    // Pause bars
                    val barW = 3f
                    val barH = pauseRadius * 0.7f
                    val barGap = 3.5f
                    val barAlpha = drawAlpha * lerp(drawLabelOpacity, 0.9f, pf)
                    drawRect(
                        color = Color.White.copy(alpha = barAlpha),
                        topLeft = Offset(cx - barGap - barW, cy - barH / 2),
                        size = Size(barW, barH),
                    )
                    drawRect(
                        color = Color.White.copy(alpha = barAlpha),
                        topLeft = Offset(cx + barGap, cy - barH / 2),
                        size = Size(barW, barH),
                    )
                }
        ) {
            // Text labels
            if (drawAlpha > 0f) {
                if (layoutType == 1) {
                    Layout2Labels(
                        isGba = isGba,
                        screenPx = screenPx,
                        circleRadius = layout2DpadRadius,
                        pauseRadius = cellSize * 0.22f,
                        alpha = drawAlpha,
                        labelOpacity = drawLabelOpacity,
                        labelSize = drawLabelSize,
                    )
                } else {
                    OverlayLabels(
                        grid = grid,
                        screenPx = screenPx,
                        alpha = drawAlpha,
                        labelOpacity = drawLabelOpacity,
                        labelSize = drawLabelSize,
                    )
                }

                if (isGba) {
                    GbaCircleLabels(
                        circleRadius = circleRadius,
                        circleCenter = circleCenter,
                        alpha = drawAlpha,
                        labelOpacity = drawLabelOpacity,
                        labelSize = drawLabelSize,
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

/**
 * Point-in-triangle test using cross product sign.
 */
private fun pointInTriangle(
    px: Float, py: Float,
    ax: Float, ay: Float,
    bx: Float, by: Float,
    cx: Float, cy: Float,
): Boolean {
    val d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by)
    val d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy)
    val d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay)
    val hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0)
    val hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0)
    return !(hasNeg && hasPos)
}

/**
 * Layout 2 hit test: d-pad triangles first, then corner zones.
 * Does NOT test the center circle or pause button (handled by caller).
 */
private fun hitTestLayout2(
    pos: Offset,
    screenPx: Float,
    circleRadius: Float,
    isGba: Boolean,
): ButtonId? {
    val cx = screenPx / 2f
    val cy = screenPx / 2f

    // D-pad triangles — 90° right triangles, half-base = height
    val h = cx - circleRadius
    // UP: base centered on top edge, tip at (cx, cy-circleRadius)
    if (pointInTriangle(pos.x, pos.y, cx - h, 0f, cx + h, 0f, cx, cy - circleRadius))
        return ButtonId.DPAD_UP
    // DOWN: base centered on bottom edge
    if (pointInTriangle(pos.x, pos.y, cx - h, screenPx, cx + h, screenPx, cx, cy + circleRadius))
        return ButtonId.DPAD_DOWN
    // LEFT: base centered on left edge
    if (pointInTriangle(pos.x, pos.y, 0f, cy - h, 0f, cy + h, cx - circleRadius, cy))
        return ButtonId.DPAD_LEFT
    // RIGHT: base centered on right edge
    if (pointInTriangle(pos.x, pos.y, screenPx, cy - h, screenPx, cy + h, cx + circleRadius, cy))
        return ButtonId.DPAD_RIGHT

    // Corner zones — full quadrants (center circle/pause already handled by caller)
    val corners = if (isGba) GBA_CORNERS else GB_CORNERS
    return when {
        pos.x < cx && pos.y < cy -> corners[0] // TL
        pos.x >= cx && pos.y < cy -> corners[1] // TR
        pos.x < cx && pos.y >= cy -> corners[2] // BL
        else -> corners[3] // BR
    }
}
