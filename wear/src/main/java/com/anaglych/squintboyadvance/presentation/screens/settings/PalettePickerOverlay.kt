package com.anaglych.squintboyadvance.presentation.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.anaglych.squintboyadvance.shared.model.GbColorPalette
import kotlinx.coroutines.launch

private val SWATCH_SIZE = 44.dp
private val SWATCH_SPACING = 6.dp

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
 * Scrollable 4×6 grid of palette swatches. Used both in the in-game overlay
 * and in the settings screen scaffold.
 */
@Composable
fun PaletteGrid(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val palettes = GbColorPalette.ALL
    val rows = palettes.chunked(4)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent {
                    coroutineScope.launch { listState.scrollBy(it.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(rows) { rowIndex, rowPalettes ->
                val startIndex = rowIndex * 4
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        SWATCH_SPACING,
                        Alignment.CenterHorizontally,
                    ),
                ) {
                    rowPalettes.forEachIndexed { colIndex, palette ->
                        val paletteIndex = startIndex + colIndex
                        PaletteSwatch(
                            palette = palette,
                            selected = paletteIndex == selectedIndex,
                            onClick = { onSelected(paletteIndex) },
                        )
                    }
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
private fun PaletteSwatch(
    palette: GbColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = size.minDimension
            val arcSize = Size(d, d)

            // Top-left (c0 = darkest), top-right (c1), bottom-right (c3 = lightest), bottom-left (c2)
            drawArc(color = Color(palette.c0), startAngle = 180f, sweepAngle = 90f, useCenter = true, size = arcSize)
            drawArc(color = Color(palette.c1), startAngle = 270f, sweepAngle = 90f, useCenter = true, size = arcSize)
            drawArc(color = Color(palette.c3), startAngle = 0f,   sweepAngle = 90f, useCenter = true, size = arcSize)
            drawArc(color = Color(palette.c2), startAngle = 90f,  sweepAngle = 90f, useCenter = true, size = arcSize)

            if (selected) {
                drawCircle(
                    color = Color.White,
                    radius = d / 2 - 2.dp.toPx(),
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }
        }
    }
}
