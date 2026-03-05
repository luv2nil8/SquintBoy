package com.anaglych.squintboyadvance.presentation.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.anaglych.squintboyadvance.presentation.RomMetadataStore
import com.anaglych.squintboyadvance.presentation.findLatestScreenshotPath
import com.anaglych.squintboyadvance.presentation.rememberScreenshot
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.anaglych.squintboyadvance.shared.model.ScaleMode
import com.anaglych.squintboyadvance.presentation.ui.GB_GRID
import com.anaglych.squintboyadvance.presentation.ui.GBA_GRID
import com.anaglych.squintboyadvance.presentation.ui.OUTLINE_ALPHA
import com.anaglych.squintboyadvance.presentation.ui.OUTLINE_WIDTH
import com.anaglych.squintboyadvance.presentation.ui.drawOverlayGrid
import com.anaglych.squintboyadvance.presentation.ui.drawGbaCircle
import com.anaglych.squintboyadvance.presentation.ui.OverlayLabels
import com.anaglych.squintboyadvance.presentation.ui.GbaCircleLabels
import kotlin.math.roundToInt

// GBA: 240x160, GB/GBC: 160x144
private const val GBA_W = 240
private const val GBA_H = 160
private const val GB_W = 160
private const val GB_H = 144

/**
 * Scale editor screen / overlay.
 *
 * When used from the pause menu:
 *  - [liveFrame] provides the current game frame as the preview background
 *  - [isOverlay] = true → transparent background (game shows through)
 *  - [onDismiss] → show a back chip; tap it to return to the pause menu
 *
 * When used from the settings nav graph all three are left at their defaults.
 */
@Composable
fun ScaleEditorScreen(
    isGba: Boolean,
    liveFrame: ImageBitmap? = null,
    isOverlay: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val currentScale = if (isGba) settings.gbaCustomScale else settings.gbCustomScale
    val frameW = if (isGba) GBA_W else GB_W
    val frameH = if (isGba) GBA_H else GB_H

    // When opened from the pause menu, lock in CUSTOM mode immediately so
    // slider adjustments persist after the user taps Done.
    LaunchedEffect(isOverlay) {
        if (isOverlay) {
            if (isGba) viewModel.setGbaScaleMode(ScaleMode.CUSTOM)
            else viewModel.setGbScaleMode(ScaleMode.CUSTOM)
        }
    }

    var overlayVisible by remember { mutableStateOf(settings.controllerLayout.visible) }
    var isSliding by remember { mutableStateOf(false) }
    val labelAlpha = remember { Animatable(1f) }

    // Fade label out 1.5s after sliding stops
    LaunchedEffect(isSliding) {
        if (!isSliding) {
            delay(1500)
            labelAlpha.animateTo(0f, tween(500))
        } else {
            labelAlpha.animateTo(1f, tween(200))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(if (!isOverlay) Modifier.background(Color.Black) else Modifier),
    ) {
        val screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val density = LocalDensity.current

        val maxScale = screenWidthPx / frameW
        val tickerStep = 1.0f / frameW

        val scaledW = (frameW * currentScale).roundToInt()
        val scaledH = (frameH * currentScale).roundToInt()

        // Preview frame — live game frame, screenshot from disk, or colored fallback
        val scaledWDp = with(density) { scaledW.toDp() }
        val scaledHDp = with(density) { scaledH.toDp() }

        if (!isOverlay) {
            // Settings nav path: show screenshot or fallback
            val metadataStore = RomMetadataStore.getInstance(LocalContext.current)
            val screenshotPath = findLatestScreenshotPath(metadataStore, isGba)
            val screenshot = rememberScreenshot(screenshotPath)
            val fallbackColor = if (isGba) Color(0xFF2E7D32) else Color(0xFF1565C0)
            val previewBitmap = liveFrame ?: screenshot
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = "Preview",
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .size(scaledWDp, scaledHDp)
                        .align(Alignment.Center),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(scaledWDp, scaledHDp)
                        .background(fallbackColor)
                        .align(Alignment.Center),
                )
            }
        }
        // When isOverlay=true the game's GameDisplay renders behind this composable — no preview drawn here.

        // Static touch overlay preview — centered on full screen
        Box(modifier = Modifier.align(Alignment.Center)) {
            StaticOverlayPreview(
                isGba = isGba,
                screenPx = screenWidthPx,
                overlayVisible = overlayVisible,
                buttonOpacity = settings.controllerLayout.buttonOpacity,
                labelOpacity = settings.controllerLayout.labelOpacity,
                labelSize = settings.controllerLayout.labelSize,
                onToggleVisibility = { overlayVisible = !overlayVisible }
            )
        }

        // Controls at 75% from top (25% from bottom)
        val controlsOffsetY = with(density) { (screenHeightPx * 0.75f).toDp() }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = controlsOffsetY - 20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val pillColor = Color(0xFF2A2A2A)

            // Scale label pill — fades when not sliding, stays in layout
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .graphicsLayer { alpha = labelAlpha.value }
                    .clip(RoundedCornerShape(50))
                    .background(pillColor)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Scale: %.2fx".format(currentScale),
                    style = MaterialTheme.typography.caption2,
                    color = Color.White
                )
            }

            // Touch slider — inset track by thumb radius so handle stays on screen
            val primaryColor = MaterialTheme.colors.primary
            val trackColor = Color.White.copy(alpha = 0.3f)
            val fraction = ((currentScale - 1.0f) / (maxScale - 1.0f)).coerceIn(0f, 1f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .padding(horizontal = 32.dp)
                    .pointerInput(maxScale) {
                        detectHorizontalDragGestures(
                            onDragStart = { isSliding = true },
                            onDragEnd = { isSliding = false },
                            onDragCancel = { isSliding = false }
                        ) { change, _ ->
                            change.consume()
                            val thumbR = 8.dp.toPx()
                            val usable = size.width - thumbR * 2
                            val newFraction = ((change.position.x - thumbR) / usable)
                                .coerceIn(0f, 1f)
                            val newScale = 1.0f + newFraction * (maxScale - 1.0f)
                            if (isGba) viewModel.setGbaCustomScale(newScale)
                            else viewModel.setGbCustomScale(newScale)
                        }
                    }
            ) {
                val trackH = 4.dp.toPx()
                val trackY = (size.height - trackH) / 2
                val thumbRadius = 8.dp.toPx()
                // Inset track so thumb center stays within canvas bounds
                val trackLeft = thumbRadius
                val trackRight = size.width - thumbRadius
                val trackW = trackRight - trackLeft
                val thumbX = trackLeft + fraction * trackW

                // Inactive track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(trackLeft, trackY),
                    size = Size(trackW, trackH),
                    cornerRadius = CornerRadius(trackH / 2)
                )
                // Active track
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(trackLeft, trackY),
                    size = Size(thumbX - trackLeft, trackH),
                    cornerRadius = CornerRadius(trackH / 2)
                )
                // Thumb
                drawCircle(
                    color = primaryColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, size.height / 2)
                )
            }

            // Pill-shaped ticker: [−  |  +]
            val scope = rememberCoroutineScope()
            val pillW = 80.dp
            val pillH = 28.dp

            Box(
                modifier = Modifier
                    .offset(y = (-2).dp)
                    .width(pillW)
                    .height(pillH)
                    .clip(RoundedCornerShape(50))
                    .background(pillColor)
                    .pointerInput(maxScale, tickerStep) {
                        detectTapGestures(
                            onPress = { offset ->
                                val isLeft = offset.x < size.width / 2f
                                val step = if (isLeft) -tickerStep else tickerStep

                                // Show label while ticking
                                isSliding = true

                                // Fire once immediately
                                val cur = if (isGba) viewModel.settings.value.gbaCustomScale
                                else viewModel.settings.value.gbCustomScale
                                val newScale = (cur + step).coerceIn(1.0f, maxScale)
                                if (isGba) viewModel.setGbaCustomScale(newScale)
                                else viewModel.setGbCustomScale(newScale)

                                // Repeat while held
                                val repeatJob = scope.launch {
                                    delay(300) // initial delay before repeat
                                    while (true) {
                                        val s = if (isGba) viewModel.settings.value.gbaCustomScale
                                        else viewModel.settings.value.gbCustomScale
                                        val ns = (s + step).coerceIn(1.0f, maxScale)
                                        if (isGba) viewModel.setGbaCustomScale(ns)
                                        else viewModel.setGbCustomScale(ns)
                                        delay(200)
                                    }
                                }

                                tryAwaitRelease()
                                repeatJob.cancel()
                                isSliding = false
                            }
                        )
                    }
                    .drawBehind {
                        // Divider line
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(size.width / 2, size.height * 0.2f),
                            end = Offset(size.width / 2, size.height * 0.8f),
                            strokeWidth = 1.5f
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Decrease",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Increase",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // "Done" back chip — only when used as a pause-menu overlay
        if (onDismiss != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1A1A1A).copy(alpha = 0.85f))
                    .clickable {
                        viewModel.setOverlayVisible(overlayVisible)
                        onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Done",
                    color = MaterialTheme.colors.primary,
                    style = MaterialTheme.typography.caption2,
                )
            }
        }
    }
}

@Composable
private fun StaticOverlayPreview(
    isGba: Boolean,
    screenPx: Float,
    overlayVisible: Boolean,
    buttonOpacity: Float = 0.3f,
    labelOpacity: Float = 0.8f,
    labelSize: Float = 14f,
    onToggleVisibility: () -> Unit
) {
    val grid = if (isGba) GBA_GRID else GB_GRID
    val cellSize = screenPx / 3f
    val circleRadius = if (isGba) screenPx * 0.25f else 0f
    val circleCenter = screenPx / 2f
    val density = LocalDensity.current

    val gridAlpha = if (overlayVisible) 1f else 0f
    val eyeColor = Color(0xFF5C7A99) // darker pastel blue
    val eyeBgAlpha = if (overlayVisible) 1f else 0.25f

    Box(
        modifier = Modifier
            .size(with(density) { screenPx.toDp() })
            .drawBehind {
                val cx = screenPx / 2f
                val cy = screenPx / 2f
                val btnRadius = cellSize * 0.22f

                val btnPath = Path().apply {
                    addOval(Rect(cx - btnRadius, cy - btnRadius, cx + btnRadius, cy + btnRadius))
                }

                // Grid cells + GBA circle (hidden when overlay not visible)
                if (overlayVisible) {
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
                        btnPath
                    }

                    drawOverlayGrid(
                        grid = grid,
                        cellSize = cellSize,
                        alpha = 1f,
                        buttonOpacity = buttonOpacity,
                        clipPath = gridClipPath,
                        outlineColor = Color.Black
                    )

                    if (isGba) {
                        drawGbaCircle(
                            circleCenter = circleCenter,
                            circleRadius = circleRadius,
                            screenPx = screenPx,
                            alpha = 1f,
                            pausePath = btnPath,
                            seAlpha = buttonOpacity,
                            stAlpha = buttonOpacity,
                            outlineColor = Color.Black
                        )
                    }
                }

                // Eye toggle button — darker pastel blue circle only
                drawCircle(
                    color = eyeColor.copy(alpha = eyeBgAlpha),
                    radius = btnRadius,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f * eyeBgAlpha),
                    radius = btnRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = OUTLINE_WIDTH)
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cx = screenPx / 2f
                    val cy = screenPx / 2f
                    val btnRadius = cellSize * 0.22f
                    val dx = offset.x - cx
                    val dy = offset.y - cy
                    if (dx * dx + dy * dy <= btnRadius * btnRadius) {
                        onToggleVisibility()
                    }
                }
            }
    ) {
        // Eye icon composable — centered on button
        val btnRadiusDp = with(density) { (cellSize * 0.22f).toDp() }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (overlayVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (overlayVisible) "Hide overlay" else "Show overlay",
                tint = Color.White,
                modifier = Modifier.size(btnRadiusDp)
            )
        }

        // Text labels (only when visible)
        if (overlayVisible) {
            OverlayLabels(
                grid = grid,
                screenPx = screenPx,
                alpha = 1f,
                labelOpacity = labelOpacity,
                labelSize = labelSize
            )

            if (isGba) {
                GbaCircleLabels(
                    circleRadius = circleRadius,
                    circleCenter = circleCenter,
                    alpha = 1f,
                    labelOpacity = labelOpacity,
                    labelSize = labelSize
                )
            }
        }
    }
}
