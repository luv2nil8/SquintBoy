package com.example.squintboyadvance.presentation.screens.emulator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.squintboyadvance.shared.model.ScaleMode
import kotlin.math.roundToInt

@Composable
fun GameDisplay(
    frame: ImageBitmap?,
    scaleMode: ScaleMode = ScaleMode.INTEGER,
    customScale: Float = 1.0f,
    filterEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        frame ?: return@Canvas

        val frameW = frame.width
        val frameH = frame.height
        val quality = if (filterEnabled) FilterQuality.Low else FilterQuality.None

        when (scaleMode) {
            ScaleMode.INTEGER -> {
                // 2x nearest-neighbor, capped to screen
                val maxScaleX = (size.width / frameW).toInt().coerceAtLeast(1)
                val maxScaleY = (size.height / frameH).toInt().coerceAtLeast(1)
                val scale = minOf(2, minOf(maxScaleX, maxScaleY))

                val dstW = frameW * scale
                val dstH = frameH * scale
                val offsetX = ((size.width - dstW) / 2).toInt()
                val offsetY = ((size.height - dstH) / 2).toInt()

                drawImage(
                    image = frame,
                    dstOffset = IntOffset(offsetX, offsetY),
                    dstSize = IntSize(dstW, dstH),
                    filterQuality = quality
                )
            }

            ScaleMode.CUSTOM -> {
                val dstW = (frameW * customScale).roundToInt()
                val dstH = (frameH * customScale).roundToInt()
                val offsetX = ((size.width - dstW) / 2).toInt()
                val offsetY = ((size.height - dstH) / 2).toInt()

                drawImage(
                    image = frame,
                    dstOffset = IntOffset(offsetX, offsetY),
                    dstSize = IntSize(dstW, dstH),
                    filterQuality = quality
                )
            }
        }
    }
}
