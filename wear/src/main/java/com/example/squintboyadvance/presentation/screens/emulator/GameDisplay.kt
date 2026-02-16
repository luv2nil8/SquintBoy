package com.example.squintboyadvance.presentation.screens.emulator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

@Composable
fun GameDisplay(
    frame: ImageBitmap?,
    targetScale: Int = 2,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        frame ?: return@Canvas

        val frameW = frame.width
        val frameH = frame.height

        // Use target scale, but cap to what fits on screen
        val maxScaleX = (size.width / frameW).toInt().coerceAtLeast(1)
        val maxScaleY = (size.height / frameH).toInt().coerceAtLeast(1)
        val maxFit = minOf(maxScaleX, maxScaleY)
        val scale = minOf(targetScale, maxFit)

        val dstW = frameW * scale
        val dstH = frameH * scale

        // Center on canvas
        val offsetX = ((size.width - dstW) / 2).toInt()
        val offsetY = ((size.height - dstH) / 2).toInt()

        drawImage(
            image = frame,
            dstOffset = IntOffset(offsetX, offsetY),
            dstSize = IntSize(dstW, dstH),
            filterQuality = FilterQuality.None
        )
    }
}
