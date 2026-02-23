package com.anaglych.squintboyadvance.presentation

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.anaglych.squintboyadvance.shared.model.SystemType
import java.io.File

/**
 * Loads a screenshot from disk and caches the ImageBitmap across recompositions.
 * Returns null if the path is null, the file doesn't exist, or decoding fails.
 */
@Composable
fun rememberScreenshot(path: String?, lastPlayed: Long? = null): ImageBitmap? {
    return remember(path, lastPlayed) {
        path?.let {
            try {
                val file = File(it)
                if (file.exists()) BitmapFactory.decodeFile(it)?.asImageBitmap() else null
            } catch (_: Exception) { null }
        }
    }
}

/**
 * Finds the screenshot path for the most recently played ROM of the given system type.
 */
fun findLatestScreenshotPath(metadataStore: RomMetadataStore, isGba: Boolean): String? {
    val gbaExts = setOf("gba")
    val gbExts = setOf("gb", "gbc")
    val targetExts = if (isGba) gbaExts else gbExts

    return metadataStore.getAll()
        .filter { (romId, meta) ->
            val ext = romId.substringAfterLast('.', "").lowercase()
            ext in targetExts && meta.thumbnailPath != null && meta.lastPlayed != null
        }
        .maxByOrNull { (_, meta) -> meta.lastPlayed ?: 0L }
        ?.value?.thumbnailPath
}
