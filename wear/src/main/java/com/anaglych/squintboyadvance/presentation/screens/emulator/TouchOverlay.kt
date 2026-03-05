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
                .pointerInput(systemType, hapticEnabled) {
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
                                                        if (event.type == PointerEventType.Press && hapticEnabled) {
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
                        addOval(Rect(
                            cx - pauseRadius, cy - pauseRadius,
                            cx + pauseRadius, cy + pauseRadius
                        ))
                    }

                    // For GBA: clip grid against the larger split circle; for GB: clip against pause
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

                    // 1) Grid cells
                    drawOverlayGrid(
                        grid = grid,
                        cellSize = cellSize,
                        alpha = drawAlpha,
                        buttonOpacity = drawButtonOpacity,
                        clipPath = gridClipPath,
                        pressedButtons = pressedButtons,
                        pressedOpacity = drawPressedOpacity,
                        outlineColor = drawOutlineColor
                    )

                    // 2) GBA SE/ST split circle
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
                            outlineColor = drawOutlineColor
                        )
                    }

                    // 3) Pause button — starts solid red, fades to outline-only
                    val pauseRed = Color(0xFFEC1358)
                    if (pf > 0f) {
                        drawCircle(
                            color = pauseRed.copy(alpha = drawAlpha * pf),
                            radius = pauseRadius,
                            center = Offset(cx, cy)
                        )
                    }
                    val pauseOutlineAlpha = drawAlpha * drawButtonOpacity * OUTLINE_ALPHA
                    drawCircle(
                        color = Color.Black.copy(alpha = pauseOutlineAlpha),
                        radius = pauseRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = OUTLINE_WIDTH)
                    )

                    // Pause bars
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
            // Text labels
            if (drawAlpha > 0f) {
                OverlayLabels(
                    grid = grid,
                    screenPx = screenPx,
                    alpha = drawAlpha,
                    labelOpacity = drawLabelOpacity,
                    labelSize = drawLabelSize
                )

                if (isGba) {
                    GbaCircleLabels(
                        circleRadius = circleRadius,
                        circleCenter = circleCenter,
                        alpha = drawAlpha,
                        labelOpacity = drawLabelOpacity,
                        labelSize = drawLabelSize
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
