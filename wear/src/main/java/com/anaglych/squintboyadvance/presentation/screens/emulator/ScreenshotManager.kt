package com.anaglych.squintboyadvance.presentation.screens.emulator

import android.graphics.Bitmap
import android.util.Log
import com.anaglych.squintboyadvance.core.MgbaEmulator
import com.anaglych.squintboyadvance.presentation.RomMetadataStore
import java.io.File

/**
 * Captures and persists screenshots for ROM library thumbnails.
 */
class ScreenshotManager(
    private val screenshotDir: File,
    private val metadataStore: RomMetadataStore,
) {
    companion object {
        private const val TAG = "ScreenshotManager"
    }

    init {
        screenshotDir.mkdirs()
    }

    fun capture(emulator: MgbaEmulator, romId: String) {
        val pixels = emulator.captureScreenshot() ?: return
        val w = emulator.width
        val h = emulator.height
        if (w <= 0 || h <= 0) return

        try {
            val file = File(screenshotDir, "${romId.substringBeforeLast('.')}.png")
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 80, out)
            }
            bitmap.recycle()

            metadataStore.update(romId) { meta ->
                meta.copy(thumbnailPath = file.absolutePath)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture screenshot", e)
        }
    }
}
