package com.anaglych.squintboyadvance.presentation.screens.emulator

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import com.anaglych.squintboyadvance.presentation.ui.drawLayout3
import com.anaglych.squintboyadvance.presentation.ui.Layout3Labels
import com.anaglych.squintboyadvance.presentation.ui.layout3ArcRadius
import kotlin.math.PI
import kotlin.math.atan2
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 500L

@SuppressLint("UnusedBoxWithConstraintsScope")
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
    vdpadThresholdFactor: Float = 0.667f,
    ghostDemo: Int = 0,
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
        val circleCenter = screenPx / 2f
        val gbaCircleRadius = screenPx * 0.25f
        val circleRadius = gbaCircleRadius
        // Layout 2 and 3 both use GBA circle radius for d-pad triangle geometry
        val layout2DpadRadius = gbaCircleRadius

        // Track which pointers are pressing which buttons
        val activeButtons = remember { mutableStateMapOf<PointerId, ButtonId>() }
        // Buttons locked by tapping pause while held (Layout 1 only)
        val lockedButtons = remember { mutableStateMapOf<ButtonId, Unit>() }
        // Virtual d-pad directions active via drag (Layouts 2 & 3)
        val vdpadDirs = remember { mutableStateMapOf<ButtonId, Unit>() }
        // Floating vdpad indicator state
        val vdpadIsAnchored = remember { mutableStateOf(false) }
        val vdpadDrawCenter = remember { mutableStateOf(Offset.Zero) }
        val vdpadCurrentPos = remember { mutableStateOf(Offset.Zero) }
        // Threshold distance in px — recomputed whenever screenPx or factor changes
        val vdpadThreshold = screenPx / 9f * vdpadThresholdFactor

        // Ghost-demo simulation state (driven by pause menu slider interaction)
        val simVdpadCenter: Offset? = if (ghostDemo == 1 && layoutType != 0)
            Offset(screenPx * 0.25f, screenPx * 0.75f) else null
        val simPressedBtn = remember { mutableStateOf<ButtonId?>(null) }
        LaunchedEffect(ghostDemo, layoutType, isGba) {
            if (ghostDemo != 2 || layoutType > 1) { simPressedBtn.value = null; return@LaunchedEffect }
            val buttons = if (isGba)
                listOf(ButtonId.A, ButtonId.B, ButtonId.DPAD_UP, ButtonId.DPAD_DOWN,
                       ButtonId.DPAD_LEFT, ButtonId.DPAD_RIGHT, ButtonId.START, ButtonId.SELECT,
                       ButtonId.L, ButtonId.R)
            else
                listOf(ButtonId.A, ButtonId.B, ButtonId.DPAD_UP, ButtonId.DPAD_DOWN,
                       ButtonId.DPAD_LEFT, ButtonId.DPAD_RIGHT, ButtonId.START, ButtonId.SELECT)
            var idx = 0
            while (true) {
                simPressedBtn.value = buttons[idx]
                delay(333)
                idx = (idx + 1) % buttons.size
            }
        }

        // Derive the set of currently pressed button IDs for visual feedback
        val pressedButtons = activeButtons.values.toSet() + lockedButtons.keys + vdpadDirs.keys + setOfNotNull(simPressedBtn.value)

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
                .pointerInput(systemType, hapticEnabled, layoutType, vdpadThresholdFactor) {
                    val pauseRadius = cellSize * 0.22f

                    coroutineScope {
                        awaitPointerEventScope {
                            val longPressJobs = mutableMapOf<PointerId, Job>()
                            var vdpadPointerId: PointerId? = null
                            var vdpadCenter = Offset.Zero
                            val cx = screenPx / 2f
                            val cy = screenPx / 2f
                            // vdpadThreshold is computed in BoxWithConstraints scope above

                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes

                                when (event.type) {
                                    PointerEventType.Press, PointerEventType.Move -> {
                                        for (change in changes) {
                                            if (!change.pressed) continue
                                            val pos = change.position

                                            if (layoutType == 0) {
                                                // ── Layout 1: grid-based ──
                                                val inCenter = isInCenterCell(pos, cellSize)
                                                if (inCenter) {
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

                                                // GBA: the SE/ST circle extends outside the center cell into
                                                // adjacent grid cells. Check it before hitTestGrid so those
                                                // areas register SELECT/START rather than the wrong grid button.
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

                                                val btn = hitTestGrid(pos, cellSize, grid)
                                                if (btn != null && btn in lockedButtons) {
                                                    if (event.type == PointerEventType.Press) {
                                                        lockedButtons.remove(btn)
                                                        onButtonRelease(btn)
                                                        if (hapticEnabled) haptic.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove
                                                        )
                                                    }
                                                    val prev = activeButtons.remove(change.id)
                                                    if (prev != null && prev !in lockedButtons) onButtonRelease(prev)
                                                    change.consume()
                                                    continue
                                                }
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
                                                // ── Layouts 2 and 3: drag-based ──

                                                // Step 1: vdpad owner — update d-pad directions from fixed center
                                                if (change.id == vdpadPointerId) {
                                                    vdpadCurrentPos.value = pos
                                                    val vdx = pos.x - vdpadCenter.x
                                                    val vdy = pos.y - vdpadCenter.y
                                                    val distSq = vdx * vdx + vdy * vdy
                                                    val newDirs = if (distSq > vdpadThreshold * vdpadThreshold)
                                                        dpadDirsFromDrag(vdx, vdy) else emptySet()
                                                    val toRelease = vdpadDirs.keys.filter { it !in newDirs }
                                                    val toPress   = newDirs.filter { it !in vdpadDirs }
                                                    for (dir in toRelease) { onButtonRelease(dir); vdpadDirs.remove(dir) }
                                                    for (dir in toPress)   { onButtonPress(dir);   vdpadDirs[dir] = Unit  }
                                                    // Fire haptic on any direction change (press OR release)
                                                    // so diagonal transitions always produce feedback
                                                    if ((toPress.isNotEmpty() || toRelease.isNotEmpty()) && hapticEnabled) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                    change.consume()
                                                    continue
                                                }

                                                // Step 2: Pause circle — long-press to open menu
                                                val dpx = pos.x - cx; val dpy = pos.y - cy
                                                val inPause = dpx * dpx + dpy * dpy <= pauseRadius * pauseRadius
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
                                                longPressJobs.remove(change.id)?.cancel()

                                                // Step 3: DMG multitouch — while vdpad active, second finger fires opposite face button
                                                if (vdpadPointerId != null && !isGba) {
                                                    val vdpadBtn = activeButtons[vdpadPointerId]
                                                    val otherBtn = when (vdpadBtn) {
                                                        ButtonId.A -> ButtonId.B
                                                        ButtonId.B -> ButtonId.A
                                                        else -> null
                                                    }
                                                    if (otherBtn != null) {
                                                        if (event.type == PointerEventType.Press) {
                                                            activeButtons[change.id] = otherBtn
                                                            onButtonPress(otherBtn)
                                                            if (hapticEnabled) haptic.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove
                                                            )
                                                        }
                                                        change.consume()
                                                        continue
                                                    }
                                                }

                                                // Step 4: Layout 2 only — GBA SE/ST center circle
                                                if (layoutType == 1 && isGba) {
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

                                                // Step 5: Hit-test buttons
                                                val rawBtn = if (layoutType == 2) {
                                                    hitTestLayout3(pos, screenPx, layout2DpadRadius, pauseRadius, isGba)
                                                } else {
                                                    hitTestLayout2(pos, screenPx, layout2DpadRadius, isGba)
                                                }

                                                // Dpad buttons that will anchor the vdpad are pre-depressed
                                                // via vdpadDirs in Step 7 — skip activeButtons for them so
                                                // dragging to the neutral zone actually releases the direction.
                                                val isDpadAnchorPress = rawBtn != null &&
                                                    rawBtn in setOf(ButtonId.DPAD_UP, ButtonId.DPAD_DOWN,
                                                                    ButtonId.DPAD_LEFT, ButtonId.DPAD_RIGHT) &&
                                                    event.type == PointerEventType.Press &&
                                                    vdpadPointerId == null

                                                // Step 6: Register button immediately (non-dpad-anchor presses only)
                                                if (!isDpadAnchorPress) {
                                                    val prevBtn = activeButtons[change.id]
                                                    if (rawBtn != prevBtn) {
                                                        if (prevBtn != null) onButtonRelease(prevBtn)
                                                        if (rawBtn != null) {
                                                            activeButtons[change.id] = rawBtn
                                                            onButtonPress(rawBtn)
                                                            if (event.type == PointerEventType.Press && hapticEnabled) {
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.TextHandleMove
                                                                )
                                                            }
                                                        } else {
                                                            activeButtons.remove(change.id)
                                                        }
                                                    }
                                                }

                                                // Step 7: On press, anchor vdpad.
                                                // Dpad buttons use a shifted anchor (1.5 × threshold toward watch
                                                // center) so the initial touch is already beyond the threshold —
                                                // direction fires immediately, and dragging inward toward the ring
                                                // reaches neutral. The ring (vdpadDrawCenter) sits at the anchor,
                                                // giving the user a visible neutral target toward the watch center.
                                                // Top/bottom arc buttons (L, R, Start, Select) are excluded.
                                                // Layout 3 cutout circles fire no button but DO anchor the vdpad.
                                                val inCutout = layoutType == 2 && run {
                                                    val cutoutLx = screenPx / 4f
                                                    val cutoutRx = 3f * screenPx / 4f
                                                    val cdxL = pos.x - cutoutLx; val cdyL = pos.y - cy
                                                    val cdxR = pos.x - cutoutRx; val cdyR = pos.y - cy
                                                    cdxL * cdxL + cdyL * cdyL <= pauseRadius * pauseRadius ||
                                                    cdxR * cdxR + cdyR * cdyR <= pauseRadius * pauseRadius
                                                }
                                                if (event.type == PointerEventType.Press &&
                                                    vdpadPointerId == null &&
                                                    (rawBtn != null || inCutout) &&
                                                    rawBtn != ButtonId.START && rawBtn != ButtonId.SELECT &&
                                                    rawBtn != ButtonId.L && rawBtn != ButtonId.R
                                                ) {
                                                    vdpadPointerId = change.id
                                                    if (isDpadAnchorPress && rawBtn != null) {
                                                        // Shift anchor 1.5× threshold toward watch center so
                                                        // the touch position starts 1.5r from anchor (beyond
                                                        // threshold), pre-depressing the cardinal direction.
                                                        val shift = vdpadThreshold * 1.5f
                                                        val anchor = when (rawBtn) {
                                                            ButtonId.DPAD_UP    -> Offset(pos.x, pos.y + shift)
                                                            ButtonId.DPAD_DOWN  -> Offset(pos.x, pos.y - shift)
                                                            ButtonId.DPAD_LEFT  -> Offset(pos.x + shift, pos.y)
                                                            ButtonId.DPAD_RIGHT -> Offset(pos.x - shift, pos.y)
                                                            else                -> pos
                                                        }
                                                        vdpadCenter = anchor
                                                        vdpadDrawCenter.value = anchor
                                                        vdpadDirs[rawBtn] = Unit
                                                        onButtonPress(rawBtn)
                                                        if (hapticEnabled) haptic.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove)
                                                    } else {
                                                        vdpadCenter = pos
                                                        vdpadDrawCenter.value = pos
                                                    }
                                                    vdpadCurrentPos.value = pos
                                                    vdpadIsAnchored.value = true
                                                }
                                                change.consume()
                                            }
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        // First pass: layout 1 pause tap → toggle button locks
                                        for (change in changes) {
                                            if (change.pressed) continue
                                            val job = longPressJobs.remove(change.id)
                                            val wasCenterTap = job != null && job.isActive
                                            job?.cancel()
                                            if (wasCenterTap && layoutType == 0) {
                                                val toToggle = activeButtons.values
                                                    .filter { it != ButtonId.START && it != ButtonId.SELECT }
                                                    .toSet()
                                                for (btn in toToggle) {
                                                    if (btn in lockedButtons) {
                                                        lockedButtons.remove(btn)
                                                        onButtonRelease(btn)
                                                    } else {
                                                        lockedButtons[btn] = Unit
                                                    }
                                                }
                                                if (toToggle.isNotEmpty() && hapticEnabled) {
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.TextHandleMove
                                                    )
                                                }
                                            }
                                        }
                                        // Second pass: release buttons + vdpad cleanup
                                        for (change in changes) {
                                            if (change.pressed) continue
                                            if (change.id == vdpadPointerId) {
                                                for (dir in vdpadDirs.keys.toList()) onButtonRelease(dir)
                                                vdpadDirs.clear()
                                                vdpadPointerId = null
                                                vdpadIsAnchored.value = false
                                            }
                                            val prevBtn = activeButtons.remove(change.id)
                                            if (prevBtn != null && prevBtn !in lockedButtons) onButtonRelease(prevBtn)
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

                    if (layoutType == 2) {
                        // ── Layout 3: hemicircle A/B + arc Start/Select/L/R ──
                        drawLayout3(
                            screenPx = screenPx,
                            isGba = isGba,
                            dpadRadius = layout2DpadRadius,
                            pauseRadius = pauseRadius,
                            alpha = drawAlpha,
                            buttonOpacity = drawButtonOpacity,
                            pressedOpacity = drawPressedOpacity,
                            pressedButtons = pressedButtons,
                            outlineColor = drawOutlineColor,
                            pausePath = pausePath,
                        )
                    } else if (layoutType == 1) {
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

                    // ── Floating vdpad indicator (layouts 2 & 3) ─────────────────
                    // Arrows orbit the finger at exactly vdpadThreshold distance so the
                    // finger never obscures them. All 4 show when nothing is firing;
                    // only the actively-firing directions remain once the threshold is crossed.
                    // Style matches existing buttons: black fill + white outline stroke.
                    if (layoutType != 0 && vdpadIsAnchored.value) {
                        val indicatorAlpha = drawAlpha * 0.85f
                        val dc = vdpadDrawCenter.value
                        val cp = vdpadCurrentPos.value
                        val r     = vdpadThreshold
                        val depth = 14f  // triangle height
                        val hw    =  8f  // triangle half-base-width

                        // Threshold ring at anchor — styled as a depressed button:
                        // white fill at pressedOpacity, black outline on top
                        drawCircle(
                            color = Color.White.copy(alpha = drawAlpha * drawPressedOpacity),
                            radius = r,
                            center = dc,
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = drawAlpha * drawButtonOpacity * OUTLINE_ALPHA),
                            radius = r,
                            center = dc,
                            style = Stroke(width = OUTLINE_WIDTH),
                        )

                        val activeDirs = vdpadDirs.keys
                        val showAll = activeDirs.isEmpty()

                        // Two-pass helper: black fill then white outline, matching button style
                        val drawArrow: (Path) -> Unit = { path ->
                            drawPath(path, Color.Black.copy(alpha = indicatorAlpha))
                            drawPath(path, Color.White.copy(alpha = indicatorAlpha),
                                style = Stroke(width = OUTLINE_WIDTH))
                        }

                        // UP — tip points away from finger (upward), base toward finger
                        if (showAll || ButtonId.DPAD_UP in activeDirs) {
                            drawArrow(Path().apply {
                                moveTo(cp.x,      cp.y - r - depth)
                                lineTo(cp.x - hw, cp.y - r)
                                lineTo(cp.x + hw, cp.y - r)
                                close()
                            })
                        }
                        // DOWN
                        if (showAll || ButtonId.DPAD_DOWN in activeDirs) {
                            drawArrow(Path().apply {
                                moveTo(cp.x,      cp.y + r + depth)
                                lineTo(cp.x - hw, cp.y + r)
                                lineTo(cp.x + hw, cp.y + r)
                                close()
                            })
                        }
                        // LEFT
                        if (showAll || ButtonId.DPAD_LEFT in activeDirs) {
                            drawArrow(Path().apply {
                                moveTo(cp.x - r - depth, cp.y)
                                lineTo(cp.x - r,         cp.y - hw)
                                lineTo(cp.x - r,         cp.y + hw)
                                close()
                            })
                        }
                        // RIGHT
                        if (showAll || ButtonId.DPAD_RIGHT in activeDirs) {
                            drawArrow(Path().apply {
                                moveTo(cp.x + r + depth, cp.y)
                                lineTo(cp.x + r,         cp.y - hw)
                                lineTo(cp.x + r,         cp.y + hw)
                                close()
                            })
                        }
                    }

                    // ── Simulated vdpad for ghost preview (Drag slider) ──────────
                    if (simVdpadCenter != null) {
                        val dc = simVdpadCenter
                        val r = vdpadThreshold
                        val depth = 14f
                        val hw = 8f
                        val simAlpha = drawAlpha * 0.85f
                        drawCircle(Color.White.copy(alpha = drawAlpha * drawPressedOpacity), r, dc)
                        drawCircle(Color.Black.copy(alpha = drawAlpha * drawButtonOpacity * OUTLINE_ALPHA), r, dc, style = Stroke(width = OUTLINE_WIDTH))
                        val drawSimArrow: (Path) -> Unit = { path ->
                            drawPath(path, Color.Black.copy(alpha = simAlpha))
                            drawPath(path, Color.White.copy(alpha = simAlpha), style = Stroke(width = OUTLINE_WIDTH))
                        }
                        drawSimArrow(Path().apply { moveTo(dc.x, dc.y - r - depth); lineTo(dc.x - hw, dc.y - r); lineTo(dc.x + hw, dc.y - r); close() })
                        drawSimArrow(Path().apply { moveTo(dc.x, dc.y + r + depth); lineTo(dc.x - hw, dc.y + r); lineTo(dc.x + hw, dc.y + r); close() })
                        drawSimArrow(Path().apply { moveTo(dc.x - r - depth, dc.y); lineTo(dc.x - r, dc.y - hw); lineTo(dc.x - r, dc.y + hw); close() })
                        drawSimArrow(Path().apply { moveTo(dc.x + r + depth, dc.y); lineTo(dc.x + r, dc.y - hw); lineTo(dc.x + r, dc.y + hw); close() })
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
                if (layoutType == 2) {
                    Layout3Labels(
                        isGba = isGba,
                        screenPx = screenPx,
                        dpadRadius = layout2DpadRadius,
                        alpha = drawAlpha,
                        labelOpacity = drawLabelOpacity,
                        labelSize = drawLabelSize,
                    )
                } else if (layoutType == 1) {
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

/**
 * Layout 3 hit test:
 * 1. D-pad triangles (same geometry as Layout 2)
 * 2. Top arc → L/R (GBA only)
 * 3. Bottom arc → Select/Start
 * 4. Cutout circles → null (safe drag zones, no button registered)
 * 5. Left/right halves → B/A
 */
private fun hitTestLayout3(
    pos: Offset,
    screenPx: Float,
    dpadRadius: Float,
    pauseRadius: Float,
    isGba: Boolean,
): ButtonId? {
    val cx = screenPx / 2f
    val cy = screenPx / 2f
    val arcR = layout3ArcRadius(screenPx)

    // Top arc: L (left) / R (right) — GBA only
    if (isGba) {
        val dxT = pos.x - cx; val dyT = pos.y
        if (dxT * dxT + dyT * dyT <= arcR * arcR && pos.y <= arcR)
            return if (pos.x < cx) ButtonId.L else ButtonId.R
    }

    // Bottom arc: Select (left) / Start (right)
    val dxB = pos.x - cx; val dyB = pos.y - screenPx
    if (dxB * dxB + dyB * dyB <= arcR * arcR && pos.y >= screenPx - arcR)
        return if (pos.x < cx) ButtonId.SELECT else ButtonId.START

    // Cutout circles → no button press
    val cutoutLx = screenPx / 4f; val cutoutRx = 3f * screenPx / 4f
    val cdxL = pos.x - cutoutLx; val cdyL = pos.y - cy
    if (cdxL * cdxL + cdyL * cdyL <= pauseRadius * pauseRadius) return null
    val cdxR = pos.x - cutoutRx; val cdyR = pos.y - cy
    if (cdxR * cdxR + cdyR * cdyR <= pauseRadius * pauseRadius) return null

    // A/B halves
    return if (pos.x < cx) ButtonId.B else ButtonId.A
}

/**
 * 8-way d-pad direction from drag vector. Returns 1 (cardinal) or 2 (diagonal) ButtonIds.
 */
private fun dpadDirsFromDrag(dx: Float, dy: Float): Set<ButtonId> {
    val angle = atan2(dy.toDouble(), dx.toDouble())
    val norm = if (angle < 0) angle + 2 * PI else angle
    val sector = ((norm + PI / 8) / (PI / 4)).toInt() % 8
    return when (sector) {
        0 -> setOf(ButtonId.DPAD_RIGHT)
        1 -> setOf(ButtonId.DPAD_DOWN, ButtonId.DPAD_RIGHT)
        2 -> setOf(ButtonId.DPAD_DOWN)
        3 -> setOf(ButtonId.DPAD_DOWN, ButtonId.DPAD_LEFT)
        4 -> setOf(ButtonId.DPAD_LEFT)
        5 -> setOf(ButtonId.DPAD_UP, ButtonId.DPAD_LEFT)
        6 -> setOf(ButtonId.DPAD_UP)
        7 -> setOf(ButtonId.DPAD_UP, ButtonId.DPAD_RIGHT)
        else -> emptySet()
    }
}
