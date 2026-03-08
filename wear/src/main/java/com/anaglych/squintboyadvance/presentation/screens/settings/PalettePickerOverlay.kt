package com.anaglych.squintboyadvance.presentation.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anaglych.squintboyadvance.shared.model.GbColorPalette
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sign

private val SWATCH_GAP = 6.dp

private object GridTuning {
    const val DEAD_ZONE = 0.25f
    const val FADE_ZONE = 1.1f
    const val SCALE_AMOUNT = 0.60f
    const val ALPHA_AMOUNT = 0.50f
    const val SIDE_COLUMN_BIAS = 0.20f
    fun ease(t: Float): Float = t * t  // quadratic ease-in
}

/**
 * Full-screen overlay palette picker for use from the pause menu.
 * Tap a palette to select it and dismiss. Tap the dark background to cancel.
 */
@Composable
fun PalettePickerOverlay(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PaletteGrid(
            selectedIndex = selectedIndex,
            onSelected = onSelected,
        )
    }
}

/**
 * Honeycomb hex grid of palette swatches (3 columns, edge columns offset
 * down by half a cell). Scrolls vertically with rotary support.
 * Cell size fills the available width.
 */
@Composable
fun PaletteGrid(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val palettes = GbColorPalette.ALL
    val density = LocalDensity.current

    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }

    // Scroll to center the selected palette on open
    LaunchedEffect(viewportWidth, viewportHeight) {
        if (viewportWidth == 0 || viewportHeight == 0) return@LaunchedEffect
        val gapPx = with(density) { SWATCH_GAP.toPx() }.toInt()
        val edgePad = with(density) { 8.dp.toPx() }.toInt()
        val available = viewportWidth - 2 * edgePad
        val cellPx = (available - 2 * gapPx) / 3
        val stepPx = cellPx + gapPx
        val halfStep = stepPx / 2
        val topPad = viewportHeight / 6

        val trio = selectedIndex / 3
        val slot = selectedIndex % 3
        val col = when (slot) { 0 -> 1; 1 -> 0; else -> 2 }
        val itemY = topPad + trio * stepPx + if (col != 1) halfStep else 0
        val target = (itemY + cellPx / 2 - viewportHeight / 2).coerceAtLeast(0)
        scrollState.scrollTo(target)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportWidth = it.width; viewportHeight = it.height }
            .onRotaryScrollEvent {
                coroutineScope.launch { scrollState.scrollBy(it.verticalScrollPixels) }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HexLayout(
                itemCount = palettes.size,
                cols = 3,
                gap = SWATCH_GAP,
                scrollOffset = scrollState.value,
                viewportHeight = viewportHeight,
            ) {
                palettes.forEachIndexed { index, palette ->
                    PaletteSwatch(
                        palette = palette,
                        selected = index == selectedIndex,
                        onClick = { onSelected(index) },
                    )
                }
            }
        }
    }
}

/**
 * Custom layout that places children in a honeycomb pattern:
 * 3 columns, edge columns offset down by half a cell height.
 * Cell size is computed from available width to fill the screen.
 */
@Composable
private fun HexLayout(
    itemCount: Int,
    cols: Int,
    gap: Dp,
    scrollOffset: Int,
    viewportHeight: Int,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val edgePad = (8.dp).roundToPx()
        val availableWidth = constraints.maxWidth - 2 * edgePad
        val cellPx = (availableWidth - (cols - 1) * gapPx) / cols
        val stepPx = cellPx + gapPx
        val halfStep = stepPx / 2
        val totalWidth = cols * cellPx + (cols - 1) * gapPx
        val offsetX = edgePad + (availableWidth - totalWidth) / 2
        val rows = ceil(itemCount / cols.toFloat()).toInt()
        val topPad = viewportHeight / 6
        val contentHeightPx = topPad + stepPx * rows + halfStep + topPad

        val placeables = measurables.map { it.measure(
            constraints.copy(
                minWidth = cellPx, maxWidth = cellPx,
                minHeight = cellPx, maxHeight = cellPx
            )
        ) }

        val halfViewport = viewportHeight / 2f
        val deadZone = halfViewport * GridTuning.DEAD_ZONE
        val fadeZone = (halfViewport * GridTuning.FADE_ZONE).coerceAtLeast(1f)

        layout(constraints.maxWidth, contentHeightPx) {
            // Layout order per trio: center (col 1), left (col 0), right (col 2)
            placeables.forEachIndexed { index, placeable ->
                val trio = index / cols
                val slot = index % cols
                val col = when (slot) {
                    0 -> 1  // first item → center
                    1 -> 0  // second item → left
                    else -> 2  // third item → right
                }
                val x = offsetX + col * stepPx
                val y = topPad + trio * stepPx + if (col != 1) halfStep else 0

                // Viewport-relative position for scaling — side columns get extra bias
                val viewportY = y - scrollOffset + cellPx / 2f
                val vertDist = abs(viewportY - viewportHeight / 2f)
                val sidePenalty = if (col != 1) halfViewport * GridTuning.SIDE_COLUMN_BIAS else 0f
                val distFromCenter = vertDist + sidePenalty
                val linear = ((distFromCenter - deadZone) / fadeZone).coerceIn(0f, 1f)
                val eased = GridTuning.ease(linear)
                val scale = 1f - eased * GridTuning.SCALE_AMOUNT
                val itemAlpha = 1f - eased * GridTuning.ALPHA_AMOUNT

                // Translate to maintain visual gap with unchanged siblings:
                // - center col: shift down (toward viewport center)
                // - side cols: shift down and toward center column
                val shrinkPx = (1f - scale) * cellPx * 0.5f
                val centerX = constraints.maxWidth / 2f
                val itemCenterX = x + cellPx / 2f
                val txX = if (col != 1) (centerX - itemCenterX).sign * shrinkPx else 0f
                val txY = if (viewportY < viewportHeight / 2f) shrinkPx else -shrinkPx

                placeable.placeRelativeWithLayer(x, y) {
                    scaleX = scale
                    scaleY = scale
                    alpha = itemAlpha
                    translationX = txX
                    translationY = txY
                }
            }
        }
    }
}

/**
 * A circular button divided into 4 quadrants, each filled with one of the palette's colors.
 * Clockwise from top-left: c0 (darkest), c1, c3 (lightest), c2.
 * A white ring is drawn when selected.
 */
@Composable
internal fun PaletteSwatch(
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
