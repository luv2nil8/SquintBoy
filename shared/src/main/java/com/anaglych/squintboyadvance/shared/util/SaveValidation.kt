package com.anaglych.squintboyadvance.shared.util

enum class SaveSizeCheck { VALID, EMPTY, SIZE_MISMATCH }

/**
 * Sanity checks for battery-save (SRAM) files, keyed by the ROM's file extension.
 * Sizes are the standard cartridge SRAM/flash capacities; anything else is flagged
 * as SIZE_MISMATCH rather than rejected outright, since odd homebrew sizes exist.
 */
object SaveValidation {
    fun check(sizeBytes: Long, romId: String): SaveSizeCheck {
        if (sizeBytes == 0L) return SaveSizeCheck.EMPTY
        val ext = romId.substringAfterLast('.', "").lowercase()
        val validSizes: Set<Long> = when (ext) {
            "gba" -> setOf(512L, 8192L, 65536L, 131072L)
            "gb", "gbc" -> setOf(8192L, 32768L, 131072L)
            else -> return SaveSizeCheck.VALID
        }
        return if (sizeBytes in validSizes) SaveSizeCheck.VALID else SaveSizeCheck.SIZE_MISMATCH
    }
}
