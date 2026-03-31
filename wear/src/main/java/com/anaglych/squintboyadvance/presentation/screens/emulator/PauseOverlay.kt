package com.anaglych.squintboyadvance.presentation.screens.emulator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.detectTapGestures
import com.anaglych.squintboyadvance.shared.model.GbColorPalette
import com.anaglych.squintboyadvance.shared.model.ScaleMode

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
    val waveArrows: Int = 2,
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
    // Scale
    customScale: Float,
    filterEnabled: Boolean,
    onSetCustomScale: (Float) -> Unit,
    onToggleFilter: () -> Unit,
    gbaFrameskip: Int,
    gbFrameskip: Int,
    onSetFrameskip: (Int) -> Unit,
    // Controls (OSC)
    oscVisible: Boolean,
    buttonOpacity: Float,
    pressedOpacity: Float,
    labelOpacity: Float,
    labelSize: Float,
    hapticEnabled: Boolean,
    layoutType: Int = 0,
    onSetLayoutType: (Int) -> Unit = {},
    onToggleOscVisible: () -> Unit,
    onSetButtonOpacity: (Float) -> Unit,
    onSetPressedOpacity: (Float) -> Unit,
    onSetLabelOpacity: (Float) -> Unit,
    onSetLabelSize: (Float) -> Unit,
    onToggleHaptic: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onUndoSave: () -> Unit,
    onUndoLoad: () -> Unit,
    onFastForward: () -> Unit,
    onSetFfSpeed: (Int) -> Unit,
    // Per-ROM settings
    isRomMode: Boolean,
    hasRomDifferences: Boolean,
    onSetRomMode: (Boolean) -> Unit,
    onSaveRomToGlobal: () -> Unit,
    onResetRomToGlobal: () -> Unit,
    onReset: () -> Unit,
    selectedPaletteIndex: Int,
    onPaletteSelected: (Int) -> Unit,
    onExit: () -> Unit,
    onGhostProgressChange: (Float) -> Unit = {},
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

    // ── Ghosting: reveal emulator behind overlay during panel interaction ──
    var lastInteraction by remember { mutableStateOf(0L) }
    var ghostActive by remember { mutableStateOf(false) }

    LaunchedEffect(lastInteraction) {
        if (lastInteraction == 0L) return@LaunchedEffect
        ghostActive = true
        delay(500)
        ghostActive = false
    }
    // Reset ghost when panel closes
    LaunchedEffect(expandedIndex) {
        if (expandedIndex == null) {
            ghostActive = false
            lastInteraction = 0L
        }
    }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (ghostActive) 0f else 0.82f,
        animationSpec = tween(if (ghostActive) 200 else 350),
        label = "overlayAlpha",
    )
    val ghostProgress by animateFloatAsState(
        targetValue = if (ghostActive) 1f else 0f,
        animationSpec = tween(if (ghostActive) 200 else 350),
        label = "ghostProgress",
    )
    // Report ghost progress to parent for OSC rendering
    LaunchedEffect(ghostProgress) { onGhostProgressChange(ghostProgress) }
    val onInteraction: () -> Unit = { lastInteraction = System.nanoTime() }

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
                    visualEnabled = !isMuted,
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
            add(PauseAction(Icons.Default.FastForward, "Fast Fwd", onClick = onFastForward, enabled = ffSpeed >= 2, shimmerStyle = ShimmerStyle.WAVE, waveArrows = 2))
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
        // 4: Scale (tap to expand panel)
        add(PauseAction(
            Icons.Default.AspectRatio, "Scale", {},
            backgroundColor = green.copy(alpha = 0.85f),
            expandContent = {
                ScaleExpandContent(
                    customScale = customScale,
                    isGba = isGba,
                    filterEnabled = filterEnabled,
                    onSetCustomScale = onSetCustomScale,
                    onToggleFilter = onToggleFilter,
                    gbaFrameskip = gbaFrameskip,
                    gbFrameskip = gbFrameskip,
                    onSetFrameskip = onSetFrameskip,
                    onInteraction = onInteraction,
                    ghostProgress = ghostProgress,
                )
            },
        ))
        // 5: Controls (tap toggles OSC visibility, long-press expands panel)
        add(PauseAction(
            Icons.Default.Gamepad, "Controls",
            onClick = onToggleOscVisible,
            backgroundColor = if (oscVisible) Color.White.copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.08f),
            iconColor = if (oscVisible) Color.White else RED,
            enabled = oscVisible,
            expandContent = {
                ControlsExpandContent(
                    buttonOpacity = buttonOpacity,
                    pressedOpacity = pressedOpacity,
                    labelOpacity = labelOpacity,
                    labelSize = labelSize,
                    hapticEnabled = hapticEnabled,
                    layoutType = layoutType,
                    onSetLayoutType = onSetLayoutType,
                    onSetButtonOpacity = onSetButtonOpacity,
                    onSetPressedOpacity = onSetPressedOpacity,
                    onSetLabelOpacity = onSetLabelOpacity,
                    onSetLabelSize = onSetLabelSize,
                    onToggleHaptic = onToggleHaptic,
                    onInteraction = onInteraction,
                    ghostProgress = ghostProgress,
                )
            },
            onLongClick = {},
        ))
        // 6: Per-ROM Settings
        add(PauseAction(
            Icons.Default.Tune, "Settings",
            onClick = {},
            backgroundColor = if (isRomMode) green.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f),
            iconColor = if (isRomMode) Color.White else Color.White,
            enabled = isRomMode,
            expandContent = {
                SettingsExpandContent(
                    isRomMode = isRomMode,
                    hasRomDifferences = hasRomDifferences,
                    onSetRomMode = onSetRomMode,
                    onSaveRomToGlobal = onSaveRomToGlobal,
                    onResetRomToGlobal = onResetRomToGlobal,
                )
            },
        ))
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
            .background(Color.Black.copy(alpha = overlayAlpha)),
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
            ghostProgress = ghostProgress,
        )
    }
}

@Composable
private fun CompactVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    visualEnabled: Boolean,
) {
    val primaryColor = MaterialTheme.colors.primary
    val disabledColor = Color.White.copy(alpha = 0.3f)
    val activeColor = if (visualEnabled) primaryColor else disabledColor
    val trackColor = Color.White.copy(alpha = if (visualEnabled) 0.12f else 0.06f)
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
                .clickable {
                    val stepped = ((volumeState.value * 10).roundToInt() - 1)
                        .coerceIn(0, 10) / 10f
                    onVolumeChange(stepped)
                },
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
                .pointerInput(Unit) {
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
                },
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
                .clickable {
                    val stepped = ((volumeState.value * 10).roundToInt() + 1)
                        .coerceIn(0, 10) / 10f
                    onVolumeChange(stepped)
                },
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

// ── Per-ROM settings expand content ────────────────────────────────

private const val COMPACT_SLIDE_INITIAL     = 0.12f
private const val COMPACT_SLIDE_PULSE_AMP   = 0.07f
private const val COMPACT_SLIDE_PULSE_DELAY = 500L
private const val COMPACT_SLIDE_THRESHOLD   = 1.0f

/**
 * Compact swipe-to-confirm bar. Absolute position tracking — the end is always
 * reachable regardless of where the drag begins. Single-breath idle pulse after a
 * short delay. Fires at [COMPACT_SLIDE_THRESHOLD] with haptic feedback.
 * Cancel X on the right end, tappable only when not dragging.
 */
@Composable
private fun CompactSwipeBar(
    slideText: String,
    color: Color,
    onConfirmed: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope  = rememberCoroutineScope()

    var progress   by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var confirmed  by remember { mutableStateOf(false) }

    // Single-breath pulse after idle delay
    val pulseAnim = remember { Animatable(0f) }
    LaunchedEffect(isDragging, confirmed) {
        if (!isDragging && !confirmed) {
            delay(COMPACT_SLIDE_PULSE_DELAY)
            pulseAnim.animateTo(COMPACT_SLIDE_PULSE_AMP, tween(850, easing = FastOutSlowInEasing))
            pulseAnim.animateTo(0f,                      tween(850, easing = FastOutSlowInEasing))
        } else {
            pulseAnim.stop()
            pulseAnim.snapTo(0f)
        }
    }

    val displayProgress = if (isDragging) progress else COMPACT_SLIDE_INITIAL + pulseAnim.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.18f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val drag = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                    if (drag != null) {
                        isDragging = true
                        progress   = (drag.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        var fired  = false
                        horizontalDrag(drag.id) { change ->
                            change.consume()
                            progress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            if (!fired && progress >= COMPACT_SLIDE_THRESHOLD) {
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
        // Fill bar — brightens as it approaches the end
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                .background(
                    color.copy(alpha = (0.45f + displayProgress * 0.55f).coerceIn(0f, 1f)),
                    RoundedCornerShape(18.dp),
                ),
        )
        // Slide label — centered, fades as bar fills
        Text(
            text      = slideText,
            style     = MaterialTheme.typography.caption2,
            color     = Color.White.copy(alpha = (0.80f - displayProgress * 1.6f).coerceIn(0f, 0.80f)),
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .fillMaxWidth()
                .padding(end = 36.dp),
        )
        // Cancel icon — tappable only when not dragging
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(36.dp)
                .then(if (!isDragging) Modifier.clickable(onClick = onCancel) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.Close,
                contentDescription = "Cancel",
                tint               = Color.White.copy(alpha = if (isDragging) 0.20f else 0.60f),
                modifier           = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun SettingsExpandContent(
    isRomMode: Boolean,
    hasRomDifferences: Boolean,
    onSetRomMode: (Boolean) -> Unit,
    onSaveRomToGlobal: () -> Unit,
    onResetRomToGlobal: () -> Unit,
) {
    val green = MaterialTheme.colors.primary

    var confirmSave  by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    // Collapse sliders when ROM mode is turned off
    LaunchedEffect(isRomMode) {
        if (!isRomMode) {
            confirmSave  = false
            confirmReset = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Settings Overrides",
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Standard Wear OS toggle chip — matches the rest of the app's switch style
        ToggleChip(
            checked = isRomMode,
            onCheckedChange = onSetRomMode,
            label = {
                Text(
                    if (isRomMode) "ROM-specific" else "Global Edit",
                    style = MaterialTheme.typography.caption2,
                )
            },
            toggleControl = {
                Icon(
                    imageVector = ToggleChipDefaults.switchIcon(checked = isRomMode),
                    contentDescription = null,
                    modifier = Modifier.size(ToggleChipDefaults.IconSize),
                )
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
        )

        // Save to Global
        if (confirmSave) {
            CompactSwipeBar(
                slideText   = "→ Slide to save",
                color       = green,
                onConfirmed = { onSaveRomToGlobal(); confirmSave = false },
                onCancel    = { confirmSave = false },
            )
        } else {
            Chip(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                onClick  = { confirmSave = true },
                enabled  = isRomMode && hasRomDifferences,
                colors   = ChipDefaults.chipColors(
                    backgroundColor        = green.copy(alpha = 0.85f),
                    disabledBackgroundColor = Color.White.copy(alpha = 0.06f),
                ),
                label = { Text("Save to Global", style = MaterialTheme.typography.caption2) },
                icon  = {
                    Icon(
                        imageVector        = Icons.Default.Save,
                        contentDescription = null,
                        modifier           = Modifier.size(14.dp),
                    )
                },
            )
        }

        // Reset to Global
        if (confirmReset) {
            CompactSwipeBar(
                slideText   = "→ Slide to reset",
                color       = RED,
                onConfirmed = { onResetRomToGlobal(); confirmReset = false },
                onCancel    = { confirmReset = false },
            )
        } else {
            Chip(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                onClick  = { confirmReset = true },
                enabled  = isRomMode && hasRomDifferences,
                colors   = ChipDefaults.chipColors(
                    backgroundColor        = RED.copy(alpha = 0.85f),
                    disabledBackgroundColor = Color.White.copy(alpha = 0.06f),
                ),
                label = { Text("Reset to Global", style = MaterialTheme.typography.caption2) },
                icon  = {
                    Icon(
                        imageVector        = Icons.Default.Restore,
                        contentDescription = null,
                        modifier           = Modifier.size(14.dp),
                    )
                },
            )
        }
    }
}

// ── Scale expand content ────────────────────────────────────────────

private const val GBA_FRAME_W = 240
private const val GBA_FRAME_H = 160
private const val GB_FRAME_W = 160
private const val GB_FRAME_H = 144

@Composable
private fun ScaleExpandContent(
    customScale: Float,
    isGba: Boolean,
    filterEnabled: Boolean,
    onSetCustomScale: (Float) -> Unit,
    onToggleFilter: () -> Unit,
    gbaFrameskip: Int,
    gbFrameskip: Int,
    onSetFrameskip: (Int) -> Unit,
    onInteraction: () -> Unit,
    ghostProgress: Float = 0f,
) {
    val green = MaterialTheme.colors.primary
    val frameW = if (isGba) GBA_FRAME_W else GB_FRAME_W
    val contentAlpha = 1f - ghostProgress
    var integerLock by remember { mutableStateOf(false) }

    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
    val maxScale = screenWidthPx / frameW
    val maxInt = maxScale.toInt().coerceAtLeast(1)
    val tickerStep = if (integerLock) 1.0f else 1.0f / frameW

    fun snap(v: Float): Float = if (integerLock) {
        v.roundToInt().toFloat().coerceIn(1f, maxInt.toFloat())
    } else v

    Box(modifier = Modifier.fillMaxWidth()) {
        // Full content — fades during ghost
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.graphicsLayer { alpha = contentAlpha },
        ) {
            CompactTrackSlider(
                value = customScale,
                onValueChange = { onSetCustomScale(snap(it)); onInteraction() },
                valueRange = 1.0f..maxScale,
                tickerStep = tickerStep,
                onInteraction = onInteraction,
            )
            // Integer | scale label | Filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (integerLock) green.copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable {
                            integerLock = !integerLock
                            if (integerLock) onSetCustomScale(snap(customScale))
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Integer", style = MaterialTheme.typography.caption2, color = Color.White)
                }
                Text(
                    text = if (integerLock) "${customScale.roundToInt()}x"
                        else "%.2fx".format(customScale),
                    style = MaterialTheme.typography.caption2,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (filterEnabled) green.copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable { onToggleFilter() }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Filter", style = MaterialTheme.typography.caption2, color = Color.White)
                }
            }
            // Frameskip row
            val currentFrameskip = if (isGba) gbaFrameskip else gbFrameskip
            val skipLabels = listOf("Off", "1", "2", "3")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Frame", style = MaterialTheme.typography.caption2, color = Color.White.copy(alpha = 0.7f))
                    Text("Skip", style = MaterialTheme.typography.caption2, color = Color.White.copy(alpha = 0.7f))
                }
                for (skip in 0..3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (currentFrameskip == skip) green.copy(alpha = 0.85f)
                                else Color.White.copy(alpha = 0.12f)
                            )
                            .clickable { onSetFrameskip(skip) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(skipLabels[skip], style = MaterialTheme.typography.caption2, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        // Ghost-only overlays: [-], [+], scale label
        if (ghostProgress > 0f) {
            val panelBg = Color(0xFF16213E)
            val scope = rememberCoroutineScope()
            val valueRef = rememberUpdatedState(customScale)
            val stepRef = rememberUpdatedState(tickerStep)

            // [-] overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 2.dp, top = 4.dp)
                    .size(28.dp)
                    .graphicsLayer { alpha = ghostProgress }
                    .clip(CircleShape)
                    .background(panelBg)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onInteraction()
                                val nv = (valueRef.value - stepRef.value)
                                    .coerceIn(1.0f, maxScale)
                                onSetCustomScale(snap(nv))
                                val job = scope.launch {
                                    delay(300)
                                    while (true) {
                                        val cur = valueRef.value
                                        val next = (cur - stepRef.value)
                                            .coerceIn(1.0f, maxScale)
                                        onSetCustomScale(snap(next))
                                        onInteraction()
                                        delay(100)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = InlineSliderDefaults.Decrease,
                    contentDescription = "Decrease",
                    tint = green,
                )
            }

            // [+] overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp, top = 4.dp)
                    .size(28.dp)
                    .graphicsLayer { alpha = ghostProgress }
                    .clip(CircleShape)
                    .background(panelBg)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onInteraction()
                                val nv = (valueRef.value + stepRef.value)
                                    .coerceIn(1.0f, maxScale)
                                onSetCustomScale(snap(nv))
                                val job = scope.launch {
                                    delay(300)
                                    while (true) {
                                        val cur = valueRef.value
                                        val next = (cur + stepRef.value)
                                            .coerceIn(1.0f, maxScale)
                                        onSetCustomScale(snap(next))
                                        onInteraction()
                                        delay(100)
                                    }
                                }
                                tryAwaitRelease()
                                job.cancel()
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = InlineSliderDefaults.Increase,
                    contentDescription = "Increase",
                    tint = green,
                )
            }

            // Scale label overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(28.dp)
                    .graphicsLayer { alpha = ghostProgress }
                    .clip(RoundedCornerShape(14.dp))
                    .background(panelBg)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (integerLock) "${customScale.roundToInt()}x"
                        else "%.2fx".format(customScale),
                    style = MaterialTheme.typography.caption2,
                    color = Color.White,
                )
            }
        }
    }
}

// ── Controls expand content ─────────────────────────────────────────

@Composable
private fun ControlsExpandContent(
    buttonOpacity: Float,
    pressedOpacity: Float,
    labelOpacity: Float,
    labelSize: Float,
    hapticEnabled: Boolean,
    onSetButtonOpacity: (Float) -> Unit,
    onSetPressedOpacity: (Float) -> Unit,
    onSetLabelOpacity: (Float) -> Unit,
    onSetLabelSize: (Float) -> Unit,
    onToggleHaptic: () -> Unit,
    onInteraction: () -> Unit,
    ghostProgress: Float = 0f,
) {
    val green = MaterialTheme.colors.primary
    val haptic = LocalHapticFeedback.current
    val contentAlpha = 1f - ghostProgress

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha },
    ) {
        // Type row: layout selector buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Type",
                style = MaterialTheme.typography.caption2,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.width(36.dp),
            )
            (0..3).forEach { idx ->
                val isActive = idx == layoutType
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isActive) green.copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable {
                            if (idx <= 1) { onSetLayoutType(idx); onInteraction() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${idx + 1}",
                        style = MaterialTheme.typography.caption2,
                        color = if (idx <= 1) Color.White else Color.White.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
        // Sliders (first 3)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LabeledSliderRow(
                label = "Outline",
                value = buttonOpacity,
                onValueChange = { onSetButtonOpacity(it); onInteraction() },
                valueRange = 0f..1f,
                steps = 20,
                onInteraction = onInteraction,
            )
            LabeledSliderRow(
                label = "Pressed",
                value = pressedOpacity,
                onValueChange = { onSetPressedOpacity(it); onInteraction() },
                valueRange = 0f..0.5f,
                steps = 10,
                onInteraction = onInteraction,
                formatValue = { "${(it * 100).roundToInt()}%" },
            )
            LabeledSliderRow(
                label = "Labels",
                value = labelOpacity,
                onValueChange = { onSetLabelOpacity(it); onInteraction() },
                valueRange = 0f..1f,
                steps = 20,
                onInteraction = onInteraction,
            )
        }
        // Size row: label buttons + haptic toggle
        val sizeSteps = listOf(9f to "T", 11f to "S", 13f to "M", 15f to "L", 17f to "H")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Label", style = MaterialTheme.typography.caption2, color = Color.White.copy(alpha = 0.7f))
                Text("Size",  style = MaterialTheme.typography.caption2, color = Color.White.copy(alpha = 0.7f))
            }
            sizeSteps.forEach { (value, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (labelSize.roundToInt() == value.roundToInt()) green.copy(alpha = 0.85f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable { onSetLabelSize(value); onInteraction() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.caption2, color = Color.White, fontSize = 11.sp)
                }
            }
            // Haptic toggle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (hapticEnabled) green.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .clickable {
                        onToggleHaptic()
                        if (!hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = if (hapticEnabled) "Haptic on" else "Haptic off",
                    tint = if (hapticEnabled) Color.White else RED,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ── Compact track slider (shared by scale) ──────────────────────────

@Composable
private fun CompactTrackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    tickerStep: Float,
    onInteraction: () -> Unit,
) {
    val primaryColor = MaterialTheme.colors.primary
    val trackColor = Color.White.copy(alpha = 0.12f)
    val scope = rememberCoroutineScope()
    val valueState = rememberUpdatedState(value)

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
        // [-] pixel step
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .pointerInput(tickerStep, valueRange) {
                    detectTapGestures(
                        onPress = {
                            onInteraction()
                            val newVal = (valueState.value - tickerStep)
                                .coerceIn(valueRange.start, valueRange.endInclusive)
                            onValueChange(newVal)

                            val repeatJob = scope.launch {
                                delay(300)
                                while (true) {
                                    val cur = valueState.value
                                    val nv = (cur - tickerStep)
                                        .coerceIn(valueRange.start, valueRange.endInclusive)
                                    onValueChange(nv)
                                    onInteraction()
                                    delay(100)
                                }
                            }
                            tryAwaitRelease()
                            repeatJob.cancel()
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = InlineSliderDefaults.Decrease,
                contentDescription = "Decrease",
                tint = primaryColor,
            )
        }

        // Continuous track slider
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .pointerInput(valueRange) {
                    val thumbR = 6.dp.toPx()
                    val usable = size.width - thumbR * 2

                    fun xToValue(x: Float): Float {
                        val f = ((x - thumbR) / usable).coerceIn(0f, 1f)
                        return valueRange.start + f * (valueRange.endInclusive - valueRange.start)
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Only commit if drag direction is horizontal
                        val drag = awaitTouchSlopOrCancellation(down.id) { change, delta ->
                            if (abs(delta.x) > abs(delta.y)) change.consume()
                        }
                        if (drag != null) {
                            onInteraction()
                            onValueChange(xToValue(drag.position.x))
                            horizontalDrag(drag.id) { change ->
                                change.consume()
                                onValueChange(xToValue(change.position.x))
                                onInteraction()
                            }
                        }
                    }
                },
        ) {
            val trackH = 3.dp.toPx()
            val trackY = (size.height - trackH) / 2
            val thumbRadius = 6.dp.toPx()
            val trackLeft = thumbRadius
            val trackRight = size.width - thumbRadius
            val trackW = trackRight - trackLeft
            val fraction = ((value - valueRange.start) /
                (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val thumbX = trackLeft + fraction * trackW

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackLeft, trackY),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(trackLeft, trackY),
                    size = Size(thumbX - trackLeft, trackH),
                    cornerRadius = CornerRadius(trackH / 2),
                )
            }
            drawCircle(
                color = primaryColor,
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2),
            )
        }

        // [+] pixel step
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .pointerInput(tickerStep, valueRange) {
                    detectTapGestures(
                        onPress = {
                            onInteraction()
                            val newVal = (valueState.value + tickerStep)
                                .coerceIn(valueRange.start, valueRange.endInclusive)
                            onValueChange(newVal)

                            val repeatJob = scope.launch {
                                delay(300)
                                while (true) {
                                    val cur = valueState.value
                                    val nv = (cur + tickerStep)
                                        .coerceIn(valueRange.start, valueRange.endInclusive)
                                    onValueChange(nv)
                                    onInteraction()
                                    delay(100)
                                }
                            }
                            tryAwaitRelease()
                            repeatJob.cancel()
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = InlineSliderDefaults.Increase,
                contentDescription = "Increase",
                tint = primaryColor,
            )
        }
    }
}

// ── Labeled slider row (compact, for controls panel) ────────────────

@Composable
private fun LabeledSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onInteraction: () -> Unit,
    formatValue: ((Float) -> String)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val primaryColor = MaterialTheme.colors.primary
    val trackColor = Color.White.copy(alpha = 0.12f)
    val displayText = if (formatValue != null) {
        formatValue(value)
    } else {
        "${((value - valueRange.start) /
            (valueRange.endInclusive - valueRange.start) * 100).roundToInt()}%"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.width(48.dp),
        )

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .pointerInput(valueRange, steps) {
                    val thumbR = 5.dp.toPx()
                    val usable = size.width - thumbR * 2

                    fun xToValue(x: Float): Float {
                        val f = ((x - thumbR) / usable).coerceIn(0f, 1f)
                        val raw = valueRange.start + f * (valueRange.endInclusive - valueRange.start)
                        return if (steps > 0) {
                            val stepSize = (valueRange.endInclusive - valueRange.start) / steps
                            (stepSize * (raw / stepSize).roundToInt())
                                .coerceIn(valueRange.start, valueRange.endInclusive)
                        } else raw
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Only commit if drag direction is horizontal
                        val drag = awaitTouchSlopOrCancellation(down.id) { change, delta ->
                            if (abs(delta.x) > abs(delta.y)) change.consume()
                        }
                        if (drag != null) {
                            onInteraction()
                            onValueChange(xToValue(drag.position.x))
                            horizontalDrag(drag.id) { change ->
                                change.consume()
                                onValueChange(xToValue(change.position.x))
                                onInteraction()
                            }
                        }
                    }
                },
        ) {
            val trackH = 3.dp.toPx()
            val trackY = (size.height - trackH) / 2
            val thumbRadius = 5.dp.toPx()
            val trackLeft = thumbRadius
            val trackRight = size.width - thumbRadius
            val trackW = trackRight - trackLeft
            val fraction = ((value - valueRange.start) /
                (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val thumbX = trackLeft + fraction * trackW

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(trackLeft, trackY),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(trackLeft, trackY),
                    size = Size(thumbX - trackLeft, trackH),
                    cornerRadius = CornerRadius(trackH / 2),
                )
            }
            drawCircle(
                color = primaryColor,
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2),
            )
        }

        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = displayText,
                style = MaterialTheme.typography.caption2,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
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
    ghostProgress: Float = 0f,
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

    // ── Panel geometry for edge-aware resist ──
    var panelMeasuredHeight by remember { mutableStateOf(0) }

    // ── Expand animation state (lives here so scroll can be derived from it) ──
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

    // Threshold = one cell height in px
    val threshold = remember(viewportWidth) {
        if (viewportWidth == 0) 0f
        else {
            val edgePx = with(density) { 8.dp.toPx() }
            val gapPx  = with(density) { CELL_GAP.toPx() }
            (viewportWidth - 2f * edgePx - 2f * gapPx) / 3f
        }
    }

    // Panel top-edge Y in content-space (mirrors HexButtonLayout geometry)
    val expandedBaseYPx = remember(activeExpandedIndex, viewportWidth, viewportHeight) {
        if (activeExpandedIndex == null || viewportWidth == 0) return@remember 0f
        val edgePx   = with(density) { 8.dp.toPx() }
        val gapPx    = with(density) { CELL_GAP.toPx() }
        val cellPx   = (viewportWidth - 2f * edgePx - 2f * gapPx) / 3f
        val stepPx   = cellPx + gapPx
        val halfStep = stepPx / 2f
        val topPad   = viewportHeight / 6f
        val trio = activeExpandedIndex / 3
        val col  = when (activeExpandedIndex % 3) { 0 -> 1; 1 -> 0; else -> 2 }
        topPad + trio * stepPx + if (col != 1) halfStep else 0f
    }

    // Palette grid bounds in content-space
    val paletteBaseYPx = remember(actions.size, viewportWidth, viewportHeight) {
        if (viewportWidth == 0) return@remember 0f
        val edgePx   = with(density) { 8.dp.toPx() }
        val gapPx    = with(density) { CELL_GAP.toPx() }
        val cellPx   = (viewportWidth - 2f * edgePx - 2f * gapPx) / 3f
        val stepPx   = cellPx + gapPx
        val halfStep = stepPx / 2f
        val topPad   = viewportHeight / 6f
        val palIdx   = actions.lastIndex
        val trio = palIdx / 3
        val col  = when (palIdx % 3) { 0 -> 1; 1 -> 0; else -> 2 }
        topPad + trio * stepPx + if (col != 1) halfStep else 0f
    }
    val paletteGridHeight = remember(actions.size, viewportWidth, paletteExpanded) {
        if (viewportWidth == 0 || !paletteExpanded) return@remember 0f
        val edgePx   = with(density) { 8.dp.toPx() }
        val gapPx    = with(density) { CELL_GAP.toPx() }
        val cellPx   = (viewportWidth - 2f * edgePx - 2f * gapPx) / 3f
        val stepPx   = cellPx + gapPx
        val halfStep = stepPx / 2f
        val paletteCount = GbColorPalette.ALL.size
        val totalItems   = (actions.size - 1) + paletteCount
        val totalRows    = ceil(totalItems / 3f).toInt()
        // Height from palette button row to last palette row bottom
        val lastIdx = totalItems - 1
        val lastTrio = lastIdx / 3
        val lastCol  = when (lastIdx % 3) { 0 -> 1; 1 -> 0; else -> 2 }
        val lastY = lastTrio * stepPx + if (lastCol != 1) halfStep else 0f
        val firstTrio = actions.lastIndex / 3
        val firstCol  = when (actions.lastIndex % 3) { 0 -> 1; 1 -> 0; else -> 2 }
        val firstY = firstTrio * stepPx + if (firstCol != 1) halfStep else 0f
        lastY - firstY + cellPx
    }

    // Helper: compute scroll target to center a button index
    // panelBias shifts the centering point down to account for panel height
    fun scrollTargetFor(index: Int, panelBias: Float = 0f): Int {
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
        return (y + cellPx / 2f + panelBias - viewportHeight / 2f).coerceAtLeast(0f).toInt()
    }

    // Reset drag state on expand/collapse or palette open/close
    LaunchedEffect(expandedIndex, paletteExpanded) {
        if (expandedIndex != null || paletteExpanded) {
            dragTotal = 0f
            resistOffset = 0f
        } else {
            if (resistOffset != 0f) {
                launch {
                    val start = resistOffset
                    animate(start, 0f, animationSpec = tween(150, easing = FastOutSlowInEasing)) { v, _ ->
                        resistOffset = v
                    }
                }
            }
            dragTotal = 0f
        }
    }

    // Initial scroll to center Resume
    LaunchedEffect(viewportWidth, viewportHeight) {
        if (viewportWidth == 0 || viewportHeight == 0) return@LaunchedEffect
        scrollState.scrollTo(scrollTargetFor(3))
    }

    // Drive scroll during expand/collapse animations; allow free scroll when fully open.
    LaunchedEffect(activeExpandedIndex, viewportWidth, viewportHeight) {
        if (viewportWidth == 0 || viewportHeight == 0) return@LaunchedEffect
        if (activeExpandedIndex == null) return@LaunchedEffect
        val edgePx    = with(density) { 8.dp.toPx() }
        val gapPx     = with(density) { CELL_GAP.toPx() }
        val cellPx    = (viewportWidth - 2f * edgePx - 2f * gapPx) / 3f
        val expandStartScroll = scrollState.value
        // Scroll panel top to the resist margin — predictable position
        // regardless of which button or how tall the content is.
        val margin = viewportHeight / 8f
        val openTarget  = (expandedBaseYPx - margin).coerceAtLeast(0f).toInt()
        val closeTarget = scrollTargetFor(activeExpandedIndex)

        var freeScrolling = false
        var collapseStartScroll = 0

        snapshotFlow { expandProgress to (expandedIndex != null) }.collect { (ep, isExpanded) ->
            when {
                isExpanded && ep < 1f -> {
                    val target = expandStartScroll + ((openTarget - expandStartScroll) * ep)
                    scrollState.scrollTo(target.toInt())
                    freeScrolling = false
                }
                isExpanded -> {
                    freeScrolling = true
                }
                !isExpanded && ep > 0f -> {
                    if (freeScrolling) {
                        collapseStartScroll = scrollState.value
                        freeScrolling = false
                    }
                    val target = collapseStartScroll + ((closeTarget - collapseStartScroll) * (1f - ep))
                    scrollState.scrollTo(target.toInt())
                }
                else -> {
                    scrollState.scrollTo(closeTarget)
                }
            }
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

    // ── Nested scroll: edge-aware resist when expanded ──
    // Allows normal scroll while panel is within 1/8 screen of edges.
    // Resist kicks in when the gap exceeds 1/8 screen; threshold crossing dismisses.
    val expandedRef     = rememberUpdatedState(expandedIndex)
    val thresholdRef    = rememberUpdatedState(threshold)
    val onToggleRef     = rememberUpdatedState(onExpandToggle)
    val expandBaseYRef  = rememberUpdatedState(expandedBaseYPx)
    val panelHeightRef  = rememberUpdatedState(panelMeasuredHeight)
    val viewportHRef    = rememberUpdatedState(viewportHeight)
    val scrollValRef    = rememberUpdatedState(scrollState.value)
    val paletteExpandedRef = rememberUpdatedState(paletteExpanded)
    val paletteBaseYRef    = rememberUpdatedState(paletteBaseYPx)
    val paletteHeightRef   = rememberUpdatedState(paletteGridHeight)
    val onPaletteCloseRef  = rememberUpdatedState(onPaletteClose)

    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val isExpanded = expandedRef.value != null
                val isPalette  = paletteExpandedRef.value
                if (!isExpanded && !isPalette) return Offset.Zero
                val t = thresholdRef.value
                if (t <= 0f) return Offset.Zero
                val isDrag = source == NestedScrollSource.Drag

                val dy = available.y
                val vpH = viewportHRef.value.toFloat()
                val margin = vpH / 8f
                val scrollY = scrollValRef.value.toFloat()
                val panelTop: Float
                val panelBottom: Float
                if (isExpanded) {
                    panelTop    = expandBaseYRef.value - scrollY
                    panelBottom = expandBaseYRef.value + panelHeightRef.value - scrollY
                } else {
                    panelTop    = paletteBaseYRef.value - scrollY
                    panelBottom = paletteBaseYRef.value + paletteHeightRef.value - scrollY
                }

                // If already in resist, unwind first when reversing direction
                if (dragTotal != 0f) {
                    val sameDir = (dragTotal < 0 && dy < 0) || (dragTotal > 0 && dy > 0)
                    if (!sameDir) {
                        val old = dragTotal
                        dragTotal += dy
                        if (old.sign != dragTotal.sign || dragTotal == 0f) {
                            dragTotal = 0f
                            resistOffset = 0f
                            return Offset.Zero
                        }
                        val progress = (abs(dragTotal) / t).coerceIn(0f, 1f)
                        resistOffset = dragTotal.sign * t * 0.3f *
                            (1f - (1f - progress) * (1f - progress))
                        return Offset(0f, dy)
                    }
                    // Same direction — continue resist
                    dragTotal += dy
                    val progress = (abs(dragTotal) / t).coerceIn(0f, 1f)
                    resistOffset = dragTotal.sign * t * 0.3f *
                        (1f - (1f - progress) * (1f - progress))
                    if (isDrag && progress >= 1f) {
                        if (isExpanded) onToggleRef.value(null)
                        else onPaletteCloseRef.value()
                    }
                    return Offset(0f, dy)
                }

                // Check if we've reached the edge threshold
                val atEdge = when {
                    dy < 0 -> panelBottom < (vpH - margin)
                    dy > 0 -> panelTop > margin
                    else -> false
                }

                if (atEdge) {
                    if (!isDrag) {
                        // Fling: hard-stop at the edge, no resist
                        return Offset(0f, dy)
                    }
                    // Drag: enter resist zone
                    dragTotal += dy
                    val progress = (abs(dragTotal) / t).coerceIn(0f, 1f)
                    resistOffset = dragTotal.sign * t * 0.3f *
                        (1f - (1f - progress) * (1f - progress))
                    if (progress >= 1f) {
                        if (isExpanded) onToggleRef.value(null)
                        else onPaletteCloseRef.value()
                    }
                    return Offset(0f, dy)
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (expandedRef.value != null && abs(dragTotal) > 0f && abs(dragTotal) < thresholdRef.value) {
                    val start = resistOffset
                    animate(start, 0f, animationSpec = tween(200, easing = FastOutSlowInEasing)) { v, _ ->
                        resistOffset = v
                    }
                    dragTotal = 0f
                }
                return if (expandedRef.value != null && abs(dragTotal) > 0f) available else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportWidth = it.width; viewportHeight = it.height }
            .nestedScroll(nestedScroll)
            .onRotaryScrollEvent {
                val isExpanded = expandedIndex != null
                val isPalette  = paletteExpanded
                if (isExpanded || isPalette) {
                    val t = threshold
                    if (t > 0f) {
                        val dy = it.verticalScrollPixels
                        val vpH = viewportHeight.toFloat()
                        val margin = vpH / 8f
                        val scrollY = scrollState.value.toFloat()
                        val pTop: Float
                        val pBottom: Float
                        if (isExpanded) {
                            pTop    = expandedBaseYPx - scrollY
                            pBottom = expandedBaseYPx + panelMeasuredHeight - scrollY
                        } else {
                            pTop    = paletteBaseYPx - scrollY
                            pBottom = paletteBaseYPx + paletteGridHeight - scrollY
                        }

                        val atEdge = dragTotal != 0f ||
                            (dy < 0 && pBottom < (vpH - margin)) ||
                            (dy > 0 && pTop > margin)

                        if (atEdge) {
                            // Rotary acts like fling — stop at edge, no resist
                            // (don't accumulate dragTotal)
                        } else {
                            coroutineScope.launch { scrollState.scrollBy(dy) }
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
                expandProgress       = expandProgress,
                activeExpandedIndex  = activeExpandedIndex,
                showPalettes         = showPalettes,
                paletteProgress      = paletteProgress,
                selectedPaletteIndex = selectedPaletteIndex,
                onPaletteSelected    = onPaletteSelected,
                ghostProgress        = ghostProgress,
                onPanelHeightMeasured = { panelMeasuredHeight = it },
            )
        }
        PositionIndicator(scrollState = scrollState)
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
    expandProgress: Float,
    activeExpandedIndex: Int?,
    showPalettes: Boolean,
    paletteProgress: Float,
    selectedPaletteIndex: Int,
    onPaletteSelected: (Int) -> Unit,
    ghostProgress: Float = 0f,
    onPanelHeightMeasured: (Int) -> Unit = {},
) {
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
            MorphPanel(expandProgress, ghostProgress) {
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
        onPanelHeightMeasured(panelPlaceable.height)

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

                belowDisp[col] = if (firstBelowY != Int.MAX_VALUE) {
                    (panelBottom + gapPx - firstBelowY).coerceAtLeast(0)
                } else 0

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

        // Extra bottom padding so the scroll can reach the resist-margin target
        // for panels on the last row. Without this, maxScrollValue is too small.
        val margin = viewportHeight / 8
        val panelPad = if (activeExpandedIndex != null && expandProgress > 0f) {
            val baseContent = topPad + stepPx * rows + halfStep + topPad + insertHeight + paletteExtraH
            val neededScroll = (expandedBaseYPx - margin).coerceAtLeast(0)
            val neededContent = neededScroll + viewportHeight
            ((neededContent - baseContent) * expandProgress).toInt().coerceAtLeast(0)
        } else 0
        val contentH = topPad + stepPx * rows + halfStep + topPad + insertHeight + paletteExtraH + panelPad

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

                // Fade out buttons replaced by panel/palette; dim others; ghost hides all
                val isExpandedButton = index == activeExpandedIndex
                val isPaletteReplaced = showPalettes && index == actions.lastIndex
                val dimProgress = maxOf(expandProgress, paletteProgress)
                val ghostDim = 1f - ghostProgress
                val buttonAlpha = when {
                    isExpandedButton -> (1f - expandProgress) * ghostDim
                    isPaletteReplaced -> (1f - paletteProgress) * ghostDim
                    dimProgress > 0f -> (1f - 0.3f * dimProgress) * ghostDim
                    else -> 1f * ghostDim
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
private fun MorphPanel(expandProgress: Float, ghostProgress: Float = 0f, content: @Composable () -> Unit) {
    val cornerRadius = androidx.compose.ui.unit.lerp(24.dp, 20.dp, expandProgress)
    val panelAlpha = 0.95f * (1f - ghostProgress)
    val bg = lerp(Color.Transparent, Color(0xFF16213E).copy(alpha = panelAlpha), expandProgress)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(cornerRadius))
            .pointerInput(Unit) { detectTapGestures { } }
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
    // Each arrow's pulse spans the full spacing between centers so the wave
    // propagates smoothly — wider for fewer arrows, tighter for more.
    val pulseWidth = (pulseWindow.toFloat() / count * 1.6f).toInt().coerceAtMost(pulseWindow)

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
        val iconSize = maxWidth * 0.34f
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

@Composable
private fun PaletteSwatch(
    palette: GbColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = size.minDimension
            val arcSize = Size(d, d)

            drawArc(color = Color(palette.c0), startAngle = 180f, sweepAngle = 90f, useCenter = true, size = arcSize)
            drawArc(color = Color(palette.c1), startAngle = 270f, sweepAngle = 90f, useCenter = true, size = arcSize)
            drawArc(color = Color(palette.c3), startAngle = 0f,   sweepAngle = 90f, useCenter = true, size = arcSize)
            drawArc(color = Color(palette.c2), startAngle = 90f,  sweepAngle = 90f, useCenter = true, size = arcSize)

            if (selected) {
                val strokeW = 2.5.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = d / 2 - strokeW / 2,
                    style = Stroke(width = strokeW),
                )
            }
        }
    }
}
