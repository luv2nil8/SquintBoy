package com.anaglych.squintboyadvance.presentation.screens.emulator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import com.anaglych.squintboyadvance.presentation.screens.settings.PaletteSwatch
import com.anaglych.squintboyadvance.shared.model.GbColorPalette

private val RED = Color(0xFFEC1358)
private val BLUE = Color(0xFF6A5ACD)
private val CELL_GAP = 6.dp

private object PauseTuning {
    const val DEAD_ZONE    = 0.25f
    const val FADE_ZONE    = 1.1f
    const val SCALE_AMOUNT = 0.50f
    const val ALPHA_AMOUNT = 0.40f
    const val SIDE_BIAS    = 0.20f
    fun ease(t: Float): Float = t * t
}

private enum class ShimmerStyle { NONE, PULSE, WAVE, HEARTBEAT }

private data class PauseAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val backgroundColor: Color = Color.White.copy(alpha = 0.12f),
    val iconColor: Color = Color.White,
    val enabled: Boolean = false,
    val shimmerStyle: ShimmerStyle = ShimmerStyle.NONE,
    val waveArrows: Int = 3,
    val expandContent: (@Composable () -> Unit)? = null,
    val onLongClick: (() -> Unit)? = null,
)

/**
 * Pause menu: scrollable honeycomb grid, same layout engine as the palette picker.
 *
 * Slot order: Volume · Save · FF · Resume · Scale · Controls · Link · Reset · Exit · [Palette]
 */
@Composable
fun PauseOverlay(
    isMuted: Boolean,
    ffSpeed: Int,
    ffSelectedSpeed: Int,
    isGb: Boolean,
    isGba: Boolean,
    volume: Float,
    hasSaveState: Boolean,
    canUndoSave: Boolean,
    canUndoLoad: Boolean,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onResume: () -> Unit,
    onScale: () -> Unit,
    onController: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onUndoSave: () -> Unit,
    onUndoLoad: () -> Unit,
    onFastForward: () -> Unit,
    onSetFfSpeed: (Int) -> Unit,
    onLinkCable: () -> Unit,
    onReset: () -> Unit,
    selectedPaletteIndex: Int,
    onPaletteSelected: (Int) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val green = MaterialTheme.colors.primary
    val haptic = LocalHapticFeedback.current

    var linkEnabled by remember { mutableStateOf(false) }
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    var paletteExpanded by remember { mutableStateOf(false) }

    // Haptic on collapse
    var prevExpandedIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(expandedIndex) {
        if (prevExpandedIndex != null && expandedIndex == null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        prevExpandedIndex = expandedIndex
    }
    var prevPaletteExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(paletteExpanded) {
        if (prevPaletteExpanded && !paletteExpanded) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        prevPaletteExpanded = paletteExpanded
    }

    // Palette collapse animation bookkeeping
    var lastPaletteExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(paletteExpanded) {
        if (paletteExpanded) lastPaletteExpanded = true
    }
    val showPalettes = paletteExpanded || lastPaletteExpanded
    val paletteProgress by animateFloatAsState(
        targetValue = if (paletteExpanded) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        finishedListener = { if (it == 0f) lastPaletteExpanded = false },
        label = "paletteProgress",
    )

    val actions = buildList {
        // 0: Audio (long-press to expand slider, tap toggles mute)
        add(PauseAction(
            if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            if (isMuted) "Unmute" else "Mute",
            onToggleMute,
            backgroundColor = if (isMuted) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f),
            iconColor = if (isMuted) RED else Color.White,
            enabled = !isMuted,
            shimmerStyle = ShimmerStyle.HEARTBEAT,
            expandContent = {
                CompactVolumeSlider(
                    volume = volume,
                    onVolumeChange = onVolumeChange,
                    enabled = !isMuted,
                )
            },
            onLongClick = {}, // placeholder, overridden in layout to toggle expand
        ))
        // 1: Save (expandable)
        add(PauseAction(
            Icons.Default.Save, "Save", {},
            backgroundColor = green.copy(alpha = 0.85f),
            expandContent = {
                SaveExpandContent(
                    hasSaveState = hasSaveState,
                    canUndoSave = canUndoSave,
                    canUndoLoad = canUndoLoad,
                    onSave = onSave,
                    onLoad = onLoad,
                    onUndoSave = onUndoSave,
                    onUndoLoad = onUndoLoad,
                )
            },
        ))
        // 2: Fast Forward (long-press to select speed on GB/GBC; 2× only on GBA)
        if (isGba) {
            add(PauseAction(Icons.Default.FastForward, "Fast Fwd", onClick = onFastForward, enabled = ffSpeed >= 2, shimmerStyle = ShimmerStyle.WAVE))
        } else {
            add(PauseAction(
                Icons.Default.FastForward, "Fast Fwd",
                onClick = onFastForward,
                enabled = ffSpeed >= 2,
                shimmerStyle = ShimmerStyle.WAVE,
                waveArrows = if (ffSpeed >= 2) ffSpeed else 2,
                expandContent = {
                    FfSpeedExpandContent(
                        currentSpeed = ffSelectedSpeed,
                        onSetSpeed = onSetFfSpeed,
                    )
                },
                onLongClick = {},
            ))
        }
        // 3: Resume
        add(PauseAction(Icons.Default.PlayArrow, "Resume", onResume, iconColor = green))
        // 4: Scale
        add(PauseAction(Icons.Default.AspectRatio, "Scale", onScale, backgroundColor = green.copy(alpha = 0.85f)))
        // 5: Controls
        add(PauseAction(Icons.Default.Gamepad, "Controls", onController, backgroundColor = green.copy(alpha = 0.85f)))
        // 6: Link Cable (GB/GBC only)
        if (!isGba) {
            add(PauseAction(
                Icons.Default.Cable, "Link Cable",
                onClick = {},
                expandContent = { LinkCableExpandContent() },
            ))
        }
        // 7: Reset
        add(PauseAction(Icons.Default.Refresh, "Reset", onReset, iconColor = RED))
        // 8: Exit
        add(PauseAction(Icons.Default.Close, "Exit", onExit, backgroundColor = RED.copy(alpha = 0.85f)))
        // 9: Palette (GB only)
        if (isGb) {
            add(PauseAction(
                Icons.Default.Brush, "Palette",
                onClick = { paletteExpanded = !paletteExpanded; expandedIndex = null },
                backgroundColor = green.copy(alpha = 0.85f),
                enabled = paletteExpanded,
            ))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        ScrollableHexGrid(
            actions = actions,
            expandedIndex = expandedIndex,
            onExpandToggle = { expandedIndex = it; if (it != null) paletteExpanded = false },
            paletteExpanded = paletteExpanded,
            onPaletteClose = { paletteExpanded = false },
            showPalettes = showPalettes,
            paletteProgress = paletteProgress,
            selectedPaletteIndex = selectedPaletteIndex,
            onPaletteSelected = { index ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                paletteExpanded = false
                onPaletteSelected(index)
            },
        )
    }
}

@Composable
private fun CompactVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    enabled: Boolean,
) {
    val primaryColor = MaterialTheme.colors.primary
    val disabledColor = Color.White.copy(alpha = 0.3f)
    val activeColor = if (enabled) primaryColor else disabledColor
    val trackColor = Color.White.copy(alpha = if (enabled) 0.12f else 0.06f)
    val volumeState = rememberUpdatedState(volume)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(if (enabled) Modifier.clickable {
                    val stepped = ((volumeState.value * 10).roundToInt() - 1)
                        .coerceIn(0, 10) / 10f
                    onVolumeChange(stepped)
                } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = InlineSliderDefaults.Decrease,
                contentDescription = "Decrease",
                tint = activeColor,
            )
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .then(if (enabled) Modifier.pointerInput(Unit) {
                    val thumbR = 6.dp.toPx()
                    val usable = size.width - thumbR * 2

                    fun fractionToVolume(x: Float): Float {
                        val f = ((x - thumbR) / usable).coerceIn(0f, 1f)
                        return (f * 10).roundToInt() / 10f
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val drag = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        }
                        if (drag != null) {
                            onVolumeChange(fractionToVolume(drag.position.x))
                            horizontalDrag(drag.id) { change ->
                                change.consume()
                                onVolumeChange(fractionToVolume(change.position.x))
                            }
                        } else {
                            onVolumeChange(fractionToVolume(down.position.x))
                        }
                    }
                } else Modifier),
        ) {
            val trackH = 3.dp.toPx()
            val trackY = (size.height - trackH) / 2
            val thumbRadius = 6.dp.toPx()
            val trackLeft = thumbRadius
            val trackRight = size.width - thumbRadius
            val trackW = trackRight - trackLeft
            val fraction = volume.coerceIn(0f, 1f)
            val thumbX = trackLeft + fraction * trackW

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackLeft, trackY),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(trackLeft, trackY),
                    size = Size(thumbX - trackLeft, trackH),
                    cornerRadius = CornerRadius(trackH / 2),
                )
            }
            val tickRadius = 1.5f.dp.toPx() / 2
            val tickColor = Color.White.copy(alpha = 0.25f)
            for (i in 0..10) {
                val tickFrac = i / 10f
                val tickX = trackLeft + tickFrac * trackW
                drawCircle(
                    color = if (tickFrac <= fraction) activeColor.copy(alpha = 0.5f) else tickColor,
                    radius = tickRadius,
                    center = Offset(tickX, size.height / 2),
                )
            }
            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2),
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(if (enabled) Modifier.clickable {
                    val stepped = ((volumeState.value * 10).roundToInt() + 1)
                        .coerceIn(0, 10) / 10f
                    onVolumeChange(stepped)
                } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = InlineSliderDefaults.Increase,
                contentDescription = "Increase",
                tint = activeColor,
            )
        }
    }
}

// ── Save expand content ─────────────────────────────────────────────

@Composable
private fun SaveExpandContent(
    hasSaveState: Boolean,
    canUndoSave: Boolean,
    canUndoLoad: Boolean,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onUndoSave: () -> Unit,
    onUndoLoad: () -> Unit,
) {
    val green = MaterialTheme.colors.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompactSaveLoadRow(
            label = "Save",
            icon = Icons.Default.Save,
            chipBg = green.copy(alpha = 0.85f),
            chipEnabled = true,
            onChip = onSave,
            accentColor = green,
            undoEnabled = canUndoSave,
            onUndo = onUndoSave,
        )
        CompactSaveLoadRow(
            label = "Load",
            icon = Icons.Default.Restore,
            chipBg = BLUE.copy(alpha = 0.85f),
            disabledChipBg = BLUE.copy(alpha = 0.15f),
            chipEnabled = hasSaveState,
            onChip = onLoad,
            accentColor = BLUE,
            undoEnabled = canUndoLoad,
            onUndo = onUndoLoad,
        )
    }
}

@Composable
private fun CompactSaveLoadRow(
    label: String,
    icon: ImageVector,
    chipBg: Color,
    disabledChipBg: Color = chipBg.copy(alpha = 0.10f),
    chipEnabled: Boolean,
    onChip: () -> Unit,
    accentColor: Color,
    undoEnabled: Boolean,
    onUndo: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Chip(
            modifier = Modifier.weight(1f).height(36.dp),
            onClick = onChip,
            enabled = chipEnabled,
            colors = ChipDefaults.chipColors(
                backgroundColor = chipBg,
                disabledBackgroundColor = disabledChipBg,
            ),
            label = { Text(label, style = MaterialTheme.typography.body2) },
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = onUndo,
            enabled = undoEnabled,
            modifier = Modifier.size(36.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.White.copy(alpha = 0.12f),
                disabledBackgroundColor = Color.White.copy(alpha = 0.06f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = accentColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Link cable expand content ───────────────────────────────────────

@Composable
private fun LinkCableExpandContent() {
    val blue = BLUE
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Chip(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            onClick = {},
            enabled = false,
            colors = ChipDefaults.chipColors(
                backgroundColor = blue.copy(alpha = 0.85f),
                disabledBackgroundColor = blue.copy(alpha = 0.15f),
            ),
            label = { Text("Host", style = MaterialTheme.typography.body2) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        Chip(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            onClick = {},
            enabled = false,
            colors = ChipDefaults.chipColors(
                backgroundColor = blue.copy(alpha = 0.85f),
                disabledBackgroundColor = blue.copy(alpha = 0.15f),
            ),
            label = { Text("Join", style = MaterialTheme.typography.body2) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }
}

// ── Scrollable hex grid ─────────────────────────────────────────────

@Composable
private fun ScrollableHexGrid(
    actions: List<PauseAction>,
    expandedIndex: Int?,
    onExpandToggle: (Int?) -> Unit,
    paletteExpanded: Boolean,
    onPaletteClose: () -> Unit,
    showPalettes: Boolean,
    paletteProgress: Float,
    selectedPaletteIndex: Int,
    onPaletteSelected: (Int) -> Unit,
) {
    val scrollState    = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val density        = LocalDensity.current

    var viewportWidth  by remember { mutableStateOf(0) }
    var viewportHeight by remember { mutableStateOf(0) }

    // ── Drag-to-dismiss state ──
    var resistOffset by remember { mutableStateOf(0f) }
    var dragTotal    by remember { mutableStateOf(0f) }

    // Threshold = one cell height in px
    val threshold = remember(viewportWidth) {
        if (viewportWidth == 0) 0f
        else {
            val edgePx = with(density) { 8.dp.toPx() }
            val gapPx  = with(density) { CELL_GAP.toPx() }
            (viewportWidth - 2f * edgePx - 2f * gapPx) / 3f
        }
    }

    // Helper: compute scroll target to center a button index
    fun scrollTargetFor(index: Int): Int {
        val edgePx    = with(density) { 8.dp.toPx() }
        val gapPx     = with(density) { CELL_GAP.toPx() }
        val available = viewportWidth - 2f * edgePx
        val cellPx    = (available - 2f * gapPx) / 3f
        val stepPx    = cellPx + gapPx
        val halfStep  = stepPx / 2f
        val topPad    = viewportHeight / 6f
        val trio = index / 3
        val col  = when (index % 3) { 0 -> 1; 1 -> 0; else -> 2 }
        val y = topPad + trio * stepPx + if (col != 1) halfStep else 0f
        return (y + cellPx / 2f - viewportHeight / 2f).coerceAtLeast(0f).toInt()
    }

    // Scroll to center expanded panel, or Resume on close / initial open
    LaunchedEffect(expandedIndex, viewportWidth, viewportHeight) {
        if (viewportWidth == 0 || viewportHeight == 0) return@LaunchedEffect

        if (expandedIndex != null) {
            // Opening: reset drag, center on panel
            dragTotal = 0f
            resistOffset = 0f
            scrollState.animateScrollTo(scrollTargetFor(expandedIndex))
        } else {
            // Closing or initial: spring back resist offset, center Resume
            if (resistOffset != 0f) {
                launch {
                    val start = resistOffset
                    animate(start, 0f, animationSpec = tween(150, easing = FastOutSlowInEasing)) { v, _ ->
                        resistOffset = v
                    }
                }
            }
            dragTotal = 0f
            val target = scrollTargetFor(3) // Resume is index 3
            scrollState.scrollTo(target)
        }
    }

    // Palette auto-scroll and scroll-to-dismiss
    LaunchedEffect(paletteExpanded) {
        if (!paletteExpanded) return@LaunchedEffect
        if (viewportWidth == 0 || viewportHeight == 0) return@LaunchedEffect
        // Snap to palette button area, wait for content to expand
        scrollState.scrollTo(scrollTargetFor(actions.lastIndex))
        delay(320) // let paletteProgress (300ms tween) finish
        // Dismiss when palette button is back at/above center
        val dismissScrollY = scrollTargetFor(actions.lastIndex)
        // Center on the currently selected palette swatch
        val selectedGridIdx = actions.lastIndex + selectedPaletteIndex
        scrollState.animateScrollTo(scrollTargetFor(selectedGridIdx))
        // Only watch for dismiss if we actually scrolled past the palette button
        if (scrollState.value > dismissScrollY) {
            snapshotFlow { scrollState.value }
                .collect { scrollY ->
                    if (scrollY <= dismissScrollY) {
                        onPaletteClose()
                    }
                }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // ── Nested scroll: block normal scroll when expanded, track drag ──
    val expandedRef  = rememberUpdatedState(expandedIndex)
    val thresholdRef = rememberUpdatedState(threshold)
    val onToggleRef  = rememberUpdatedState(onExpandToggle)

    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val expanded = expandedRef.value ?: return Offset.Zero
                val t = thresholdRef.value
                if (t <= 0f) return Offset(0f, available.y)

                dragTotal += available.y
                val progress = (abs(dragTotal) / t).coerceIn(0f, 1f)
                // Quadratic ease-out: moves freely at first, decelerates
                resistOffset = dragTotal.sign * t * 0.3f *
                    (1f - (1f - progress) * (1f - progress))

                if (progress >= 1f) {
                    onToggleRef.value(null)
                }
                return Offset(0f, available.y) // consume all vertical scroll
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (expandedRef.value != null && abs(dragTotal) > 0f && abs(dragTotal) < thresholdRef.value) {
                    // Below threshold — spring back
                    val start = resistOffset
                    animate(start, 0f, animationSpec = tween(200, easing = FastOutSlowInEasing)) { v, _ ->
                        resistOffset = v
                    }
                    dragTotal = 0f
                }
                return if (expandedRef.value != null) available else Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportWidth = it.width; viewportHeight = it.height }
            .nestedScroll(nestedScroll)
            .onRotaryScrollEvent {
                if (expandedIndex != null) {
                    // Rotary counts as drag when expanded
                    val t = threshold
                    if (t > 0f) {
                        dragTotal += it.verticalScrollPixels
                        val progress = (abs(dragTotal) / t).coerceIn(0f, 1f)
                        resistOffset = dragTotal.sign * t * 0.3f *
                            (1f - (1f - progress) * (1f - progress))
                        if (progress >= 1f) {
                            onExpandToggle(null)
                        }
                    }
                } else {
                    coroutineScope.launch { scrollState.scrollBy(it.verticalScrollPixels) }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .graphicsLayer { translationY = resistOffset },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HexButtonLayout(
                actions              = actions,
                gap                  = CELL_GAP,
                scrollOffset         = scrollState.value,
                viewportHeight       = viewportHeight,
                expandedIndex        = expandedIndex,
                onExpandToggle       = onExpandToggle,
                showPalettes         = showPalettes,
                paletteProgress      = paletteProgress,
                selectedPaletteIndex = selectedPaletteIndex,
                onPaletteSelected    = onPaletteSelected,
            )
        }
    }
}

// ── Hex layout with morphing expand ─────────────────────────────────

@Composable
private fun HexButtonLayout(
    actions: List<PauseAction>,
    gap: Dp,
    scrollOffset: Int,
    viewportHeight: Int,
    expandedIndex: Int?,
    onExpandToggle: (Int?) -> Unit,
    showPalettes: Boolean,
    paletteProgress: Float,
    selectedPaletteIndex: Int,
    onPaletteSelected: (Int) -> Unit,
) {
    // Track last expanded index so collapse animation has content to render.
    var lastExpandedIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(expandedIndex) {
        if (expandedIndex != null) lastExpandedIndex = expandedIndex
    }
    val activeExpandedIndex = expandedIndex ?: lastExpandedIndex

    val expandProgress by animateFloatAsState(
        targetValue = if (expandedIndex != null) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        finishedListener = { if (it == 0f) lastExpandedIndex = null },
        label = "expandProgress",
    )

    Layout(
        content = {
            // Hex buttons — expandable ones get click/long-press wired to toggle expansion.
            actions.forEachIndexed { index, action ->
                val effectiveAction = if (action.expandContent != null) {
                    val toggleExpand = { onExpandToggle(if (expandedIndex == index) null else index) }
                    if (action.onLongClick != null) {
                        // Long press to expand; tap uses original onClick
                        action.copy(onLongClick = toggleExpand)
                    } else {
                        // Tap to expand
                        action.copy(onClick = toggleExpand)
                    }
                } else action
                HexButton(effectiveAction)
            }
            // Morphing expanded panel — measured at interpolated width.
            MorphPanel(expandProgress) {
                if (activeExpandedIndex != null && expandProgress > 0f) {
                    actions.getOrNull(activeExpandedIndex)?.expandContent?.invoke()
                }
            }
            // Palette swatches (when palette is expanding or collapsing)
            if (showPalettes) {
                GbColorPalette.ALL.forEachIndexed { index, palette ->
                    PaletteSwatch(
                        palette = palette,
                        selected = index == selectedPaletteIndex,
                        onClick = { onPaletteSelected(index) },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val gapPx      = gap.roundToPx()
        val edgePad    = (8.dp).roundToPx()
        val available  = constraints.maxWidth - 2 * edgePad
        val cellPx     = (available - 2 * gapPx) / 3
        val stepPx     = cellPx + gapPx
        val halfStep   = stepPx / 2
        val totalWidth = 3 * cellPx + 2 * gapPx
        val offsetX    = edgePad + (available - totalWidth) / 2
        val topPad     = viewportHeight / 6
        val rows       = ceil(actions.size / 3f).toInt()

        // Expanded button geometry
        val expandedTrio = activeExpandedIndex?.let { it / 3 }
        val expandedCol = activeExpandedIndex?.let {
            when (it % 3) { 0 -> 1; 1 -> 0; else -> 2 }
        }
        val expandedBaseY = if (expandedTrio != null && expandedCol != null) {
            topPad + expandedTrio * stepPx + if (expandedCol != 1) halfStep else 0
        } else 0

        // Measure hex buttons at cell size
        val hexPlaceables = measurables.take(actions.size).map {
            it.measure(constraints.copy(
                minWidth = cellPx, maxWidth = cellPx,
                minHeight = cellPx, maxHeight = cellPx,
            ))
        }

        // Panel measurement — width interpolates from cell to full row
        val panelWidth = cellPx + ((totalWidth - cellPx) * expandProgress).toInt()
        val panelPlaceable = measurables[actions.size].measure(
            constraints.copy(minWidth = panelWidth, maxWidth = panelWidth, minHeight = 0)
        )
        val panelExtra = (panelPlaceable.height - cellPx).coerceAtLeast(0)

        // Measure palette swatches at cell size
        val palettePlaceables = if (showPalettes) {
            measurables.drop(actions.size + 1).map {
                it.measure(constraints.copy(
                    minWidth = cellPx, maxWidth = cellPx,
                    minHeight = cellPx, maxHeight = cellPx,
                ))
            }
        } else emptyList()

        // ── Cohort displacement system ──────────────────────────────
        // Each column (left/center/right) splits into above and below
        // cohorts at the expanding button's Y. All buttons in a cohort
        // shift by the same amount — the block moves as one unit.
        // Displacement is derived from geometry: below-cohort's first
        // button must clear panelBottom + gapPx; above-cohort's last
        // button must clear expandedBaseY - gapPx. The own-column is
        // the 1× baseline; other columns' displacements are whatever
        // the geometry requires, giving natural speed multipliers so
        // all cohorts arrive at their destinations simultaneously.

        val belowDisp = IntArray(3) // col 0=left, 1=center, 2=right
        val aboveDisp = IntArray(3)

        if (activeExpandedIndex != null) {
            val panelBottom = expandedBaseY + cellPx + panelExtra

            for (col in 0..2) {
                var firstBelowY = Int.MAX_VALUE
                var lastAboveY  = Int.MIN_VALUE

                for (i in actions.indices) {
                    if (i == activeExpandedIndex) continue
                    val c = when (i % 3) { 0 -> 1; 1 -> 0; else -> 2 }
                    if (c != col) continue
                    val y = topPad + (i / 3) * stepPx + if (c != 1) halfStep else 0
                    val isBelow = y > expandedBaseY ||
                                  (y == expandedBaseY && i > activeExpandedIndex)
                    if (isBelow  && y < firstBelowY) firstBelowY = y
                    if (!isBelow && y > lastAboveY)  lastAboveY  = y
                }

                // Below: first button clears panel bottom with gapPx spacing
                belowDisp[col] = if (firstBelowY != Int.MAX_VALUE) {
                    (panelBottom + gapPx - firstBelowY).coerceAtLeast(0)
                } else 0

                // Above: last button's bottom edge clears panel top with gapPx
                aboveDisp[col] = if (lastAboveY != Int.MIN_VALUE) {
                    (lastAboveY + cellPx + gapPx - expandedBaseY).coerceAtLeast(0)
                } else 0
            }
        }

        val insertHeight = if (activeExpandedIndex != null) {
            (maxOf(belowDisp[0], belowDisp[1], belowDisp[2]) * expandProgress).toInt()
        } else 0

        // Extra height for inline palette swatches (first swatch replaces palette button)
        val paletteCount = palettePlaceables.size
        val totalRows = if (paletteCount > 0) {
            ceil(((actions.size - 1) + paletteCount) / 3f).toInt()
        } else rows
        val paletteExtraH = ((totalRows - rows) * stepPx * paletteProgress).toInt()

        val contentH = topPad + stepPx * rows + halfStep + topPad + insertHeight + paletteExtraH

        val halfViewport = viewportHeight / 2f
        val deadZone = halfViewport * PauseTuning.DEAD_ZONE
        val fadeZone = (halfViewport * PauseTuning.FADE_ZONE).coerceAtLeast(1f)

        layout(constraints.maxWidth, contentH) {
            hexPlaceables.forEachIndexed { index, placeable ->
                val trio = index / 3
                val col  = when (index % 3) { 0 -> 1; 1 -> 0; else -> 2 }
                val x = offsetX + col * stepPx
                val baseY = topPad + trio * stepPx + if (col != 1) halfStep else 0

                // Look up this button's cohort displacement
                val yOffset = if (activeExpandedIndex != null && index != activeExpandedIndex) {
                    val isAbove = baseY < expandedBaseY ||
                                  (baseY == expandedBaseY && index < activeExpandedIndex)
                    if (isAbove) {
                        (-aboveDisp[col] * expandProgress).toInt()
                    } else {
                        (belowDisp[col] * expandProgress).toInt()
                    }
                } else 0

                val y = baseY + yOffset

                // Fade out buttons replaced by panel/palette; dim others
                val isExpandedButton = index == activeExpandedIndex
                val isPaletteReplaced = showPalettes && index == actions.lastIndex
                val dimProgress = maxOf(expandProgress, paletteProgress)
                val buttonAlpha = when {
                    isExpandedButton -> 1f - expandProgress
                    isPaletteReplaced -> 1f - paletteProgress
                    dimProgress > 0f -> 1f - 0.3f * dimProgress
                    else -> 1f
                }

                val viewportY   = y - scrollOffset + cellPx / 2f
                val vertDist    = abs(viewportY - viewportHeight / 2f)
                val sidePenalty = if (col != 1) halfViewport * PauseTuning.SIDE_BIAS else 0f
                val distCenter  = vertDist + sidePenalty
                val linear      = ((distCenter - deadZone) / fadeZone).coerceIn(0f, 1f)
                val eased       = PauseTuning.ease(linear)
                val scale       = 1f - eased * PauseTuning.SCALE_AMOUNT
                val itemAlpha   = (1f - eased * PauseTuning.ALPHA_AMOUNT) * buttonAlpha

                val shrinkPx    = (1f - scale) * cellPx * 0.5f
                val centerX     = constraints.maxWidth / 2f
                val itemCenterX = x + cellPx / 2f
                val txX = if (col != 1) (centerX - itemCenterX).sign * shrinkPx else 0f
                val txY = if (viewportY < viewportHeight / 2f) shrinkPx else -shrinkPx

                placeable.placeRelativeWithLayer(x, y) {
                    scaleX = scale; scaleY = scale
                    alpha = itemAlpha
                    translationX = txX; translationY = txY
                }
            }

            // Place expanded panel centered on the originating button
            if (expandedTrio != null && expandedCol != null && expandProgress > 0f) {
                val buttonX = offsetX + expandedCol * stepPx
                val buttonCenterX = buttonX + cellPx / 2
                val panelX = (buttonCenterX - panelWidth / 2)
                    .coerceIn(offsetX, offsetX + totalWidth - panelWidth)

                panelPlaceable.placeRelativeWithLayer(panelX, expandedBaseY) {
                    alpha = expandProgress
                }
            }

            // Place palette swatches, animating from palette button origin
            if (palettePlaceables.isNotEmpty()) {
                val palBtnIdx = actions.size - 1
                val palBtnTrio = palBtnIdx / 3
                val palBtnSlot = palBtnIdx % 3
                val palBtnCol = when (palBtnSlot) { 0 -> 1; 1 -> 0; else -> 2 }
                val palBtnX = offsetX + palBtnCol * stepPx
                val palBtnY = topPad + palBtnTrio * stepPx +
                    if (palBtnCol != 1) halfStep else 0

                palettePlaceables.forEachIndexed { pIdx, placeable ->
                    val gridIdx = (actions.size - 1) + pIdx
                    val trio = gridIdx / 3
                    val slot = gridIdx % 3
                    val col = when (slot) { 0 -> 1; 1 -> 0; else -> 2 }
                    val targetX = offsetX + col * stepPx
                    val targetY = topPad + trio * stepPx +
                        if (col != 1) halfStep else 0

                    val x = palBtnX + ((targetX - palBtnX) * paletteProgress).toInt()
                    val y = palBtnY + ((targetY - palBtnY) * paletteProgress).toInt()

                    val viewportY = y - scrollOffset + cellPx / 2f
                    val vertDist = abs(viewportY - viewportHeight / 2f)
                    val sidePenalty = if (col != 1)
                        halfViewport * PauseTuning.SIDE_BIAS else 0f
                    val distCenter = vertDist + sidePenalty
                    val linear = ((distCenter - deadZone) / fadeZone)
                        .coerceIn(0f, 1f)
                    val eased = PauseTuning.ease(linear)
                    val scale = 1f - eased * PauseTuning.SCALE_AMOUNT
                    val itemAlpha = (1f - eased * PauseTuning.ALPHA_AMOUNT) *
                        paletteProgress

                    val shrinkPx = (1f - scale) * cellPx * 0.5f
                    val ctrX = constraints.maxWidth / 2f
                    val itemCtrX = x + cellPx / 2f
                    val txX = if (col != 1) (ctrX - itemCtrX).sign * shrinkPx
                        else 0f
                    val txY = if (viewportY < viewportHeight / 2f) shrinkPx
                        else -shrinkPx

                    placeable.placeRelativeWithLayer(x, y) {
                        scaleX = scale; scaleY = scale
                        alpha = itemAlpha
                        translationX = txX; translationY = txY
                    }
                }
            }
        }
    }
}

/**
 * Morphing panel that transitions from hex-button shape to pill.
 * Corner radius and background interpolate with [expandProgress].
 */
@Composable
private fun MorphPanel(expandProgress: Float, content: @Composable () -> Unit) {
    val cornerRadius = androidx.compose.ui.unit.lerp(24.dp, 20.dp, expandProgress)
    val bg = lerp(Color.Transparent, Color(0xFF16213E).copy(alpha = 0.95f), expandProgress)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(cornerRadius))
            .padding(
                horizontal = (12 * expandProgress).dp,
                vertical = (8 * expandProgress).dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── Fast-forward ripple icon ────────────────────────────────────────

@Composable
private fun FfRippleIcon(arrowCount: Int = 3) {
    val count = arrowCount.coerceIn(2, 4)
    key(count) {
    val transition = rememberInfiniteTransition(label = "ff_ripple")
    val pulseWindow = 375          // total time for the ripple wave
    val rest = 825                 // quiet time before next cycle
    val duration = pulseWindow + rest  // 1200ms total
    // Evenly space pulse starts using floats to avoid integer division drift
    val pulseWidth = 150           // each arrow's up-down pulse duration

    val scales = (0 until count).map { i ->
        val center = (pulseWindow.toFloat() * (i + 0.5f) / count).toInt()
        val start = (center - pulseWidth / 2).coerceAtLeast(0)
        val end = (center + pulseWidth / 2).coerceAtMost(pulseWindow)
        transition.animateFloat(
            initialValue  = 1f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = duration
                    1f   at 0
                    1f   at start
                    1.3f at center
                    1f   at end
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "ff$i",
        )
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        val iconSize = maxWidth * (if (count <= 3) 0.34f else 0.28f)
        val overlap  = iconSize * 0.54f

        Row(
            horizontalArrangement = Arrangement.spacedBy(-overlap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            scales.forEach { scale ->
                val s by scale
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize).graphicsLayer { scaleX = s; scaleY = s },
                )
            }
        }
    }
    } // key(count)
}

// ── Fast-forward speed expand content ───────────────────────────────

@Composable
private fun FfSpeedExpandContent(
    currentSpeed: Int,
    onSetSpeed: (Int) -> Unit,
) {
    val green = MaterialTheme.colors.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (speed in 2..4) {
            val selected = currentSpeed == speed
            Button(
                onClick = { onSetSpeed(speed) },
                modifier = Modifier.weight(1f).height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selected) green.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.12f),
                ),
            ) {
                Text("${speed}x", style = MaterialTheme.typography.body2)
            }
        }
    }
}

// ── Hex button ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HexButton(action: PauseAction) {
    val green = MaterialTheme.colors.primary

    val effectiveBg = if (action.enabled) green else action.backgroundColor

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulsePhase by pulseTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulsePhase",
    )
    val sinVal    = sin(pulsePhase)
    val pulseAlpha = 0.875f + 0.125f * sinVal
    val pulseScale = 0.910f + 0.090f * sinVal

    val heartbeatTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartbeatScale by heartbeatTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                1f    at 0
                1.18f at 100
                1f    at 230
                1.12f at 330
                1f    at 460
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "heartbeatScale",
    )

    val (iconAlpha, iconScale) = when {
        !action.enabled                               -> Pair(1f, 1f)
        action.shimmerStyle == ShimmerStyle.PULSE     -> Pair(pulseAlpha, pulseScale)
        action.shimmerStyle == ShimmerStyle.HEARTBEAT -> Pair(1f, heartbeatScale)
        else                                          -> Pair(1f, 1f)
    }

    val iconContent: @Composable () -> Unit = {
        if (action.shimmerStyle == ShimmerStyle.WAVE && action.enabled) {
            FfRippleIcon(arrowCount = action.waveArrows)
        } else {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = action.iconColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        alpha = iconAlpha
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
    }

    if (action.onLongClick != null) {
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val onClickRef = rememberUpdatedState(action.onClick)
        val onLongClickRef = rememberUpdatedState(action.onLongClick!!)

        val fillProgress = remember { Animatable(0f) }
        val fizzleProgress = remember { Animatable(0f) }
        var fizzleStartSweep by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Button with custom gesture handling
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(effectiveBg)
                    .pointerInput(Unit) {
                        val longPressMs = viewConfiguration.longPressTimeoutMillis
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            var longPressTriggered = false

                            val fillJob = scope.launch {
                                fizzleProgress.snapTo(0f)
                                fillProgress.snapTo(0f)
                                fillProgress.animateTo(
                                    1f,
                                    tween(longPressMs.toInt(), easing = LinearEasing),
                                )
                                longPressTriggered = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClickRef.value.invoke()
                                fillProgress.animateTo(0f, tween(150))
                            }

                            val up = waitForUpOrCancellation()

                            if (!longPressTriggered) {
                                fillJob.cancel()
                                val currentFill = fillProgress.value
                                if (currentFill > 0.05f) {
                                    fizzleStartSweep = currentFill * 180f
                                    scope.launch {
                                        fillProgress.snapTo(0f)
                                        fizzleProgress.snapTo(0f)
                                        fizzleProgress.animateTo(
                                            1f,
                                            tween(300, easing = FastOutSlowInEasing),
                                        )
                                        fizzleProgress.snapTo(0f)
                                    }
                                } else {
                                    scope.launch { fillProgress.snapTo(0f) }
                                }
                                if (up != null) onClickRef.value.invoke()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { iconContent() }

            // Ring overlay
            val fill = fillProgress.value
            val fizzle = fizzleProgress.value
            val ringColor = if (action.enabled) Color.White else green
            if (fill > 0f || fizzle > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 2.5f.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val arcOffset = Offset(inset, inset)

                    if (fill > 0f) {
                        val sweep = fill * 180f
                        // CW arc from 12 o'clock
                        drawArc(
                            color = ringColor,
                            startAngle = 270f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = arcOffset,
                            size = arcSize,
                        )
                        // CCW arc from 12 o'clock
                        drawArc(
                            color = ringColor,
                            startAngle = 270f,
                            sweepAngle = -sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = arcOffset,
                            size = arcSize,
                        )
                    }

                    if (fizzle > 0f) {
                        val baseSweep = fizzleStartSweep
                        // Body fades out quickly
                        val bodyAlpha = (1f - fizzle * 2f).coerceIn(0f, 1f)
                        // Tips travel forward, thin, and fade
                        val tipTravel = fizzle * 45f
                        val tipAlpha = (1f - fizzle).coerceIn(0f, 1f)
                        val tipStroke = strokeWidth * (1f - fizzle * 0.8f)
                        val tipLen = 20f * (1f - fizzle * 0.5f)

                        if (bodyAlpha > 0f) {
                            drawArc(
                                color = ringColor.copy(alpha = bodyAlpha),
                                startAngle = 270f,
                                sweepAngle = baseSweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                topLeft = arcOffset,
                                size = arcSize,
                            )
                            drawArc(
                                color = ringColor.copy(alpha = bodyAlpha),
                                startAngle = 270f,
                                sweepAngle = -baseSweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                topLeft = arcOffset,
                                size = arcSize,
                            )
                        }

                        if (tipAlpha > 0f && tipLen > 0f) {
                            // CW tracer tip
                            drawArc(
                                color = ringColor.copy(alpha = tipAlpha),
                                startAngle = 270f + baseSweep + tipTravel - tipLen,
                                sweepAngle = tipLen,
                                useCenter = false,
                                style = Stroke(width = tipStroke, cap = StrokeCap.Round),
                                topLeft = arcOffset,
                                size = arcSize,
                            )
                            // CCW tracer tip
                            drawArc(
                                color = ringColor.copy(alpha = tipAlpha),
                                startAngle = 270f - baseSweep - tipTravel,
                                sweepAngle = -tipLen,
                                useCenter = false,
                                style = Stroke(width = tipStroke, cap = StrokeCap.Round),
                                topLeft = arcOffset,
                                size = arcSize,
                            )
                        }
                    }
                }
            }
        }
    } else {
        Button(
            onClick = action.onClick,
            modifier = Modifier.size(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = effectiveBg),
        ) { iconContent() }
    }
}
