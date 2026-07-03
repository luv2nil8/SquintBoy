package com.anaglych.squintboyadvance.shared.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveValidationTest {

    @Test
    fun `empty file is EMPTY regardless of extension`() {
        assertEquals(SaveSizeCheck.EMPTY, SaveValidation.check(0, "Pokemon.gba"))
        assertEquals(SaveSizeCheck.EMPTY, SaveValidation.check(0, "Pokemon.gb"))
        assertEquals(SaveSizeCheck.EMPTY, SaveValidation.check(0, "noext"))
    }

    @Test
    fun `gba accepts standard sram and flash sizes`() {
        for (size in listOf(512L, 8192L, 65536L, 131072L)) {
            assertEquals(SaveSizeCheck.VALID, SaveValidation.check(size, "Game.gba"))
        }
    }

    @Test
    fun `gb and gbc accept standard sizes`() {
        for (ext in listOf("gb", "gbc")) {
            for (size in listOf(8192L, 32768L, 131072L)) {
                assertEquals(SaveSizeCheck.VALID, SaveValidation.check(size, "Game.$ext"))
            }
        }
    }

    @Test
    fun `nonstandard size is SIZE_MISMATCH`() {
        assertEquals(SaveSizeCheck.SIZE_MISMATCH, SaveValidation.check(1000, "Game.gba"))
        assertEquals(SaveSizeCheck.SIZE_MISMATCH, SaveValidation.check(512, "Game.gb"))
    }

    @Test
    fun `unknown extension is always VALID when non-empty`() {
        assertEquals(SaveSizeCheck.VALID, SaveValidation.check(1234, "Game.rom"))
        assertEquals(SaveSizeCheck.VALID, SaveValidation.check(1234, "noext"))
    }

    @Test
    fun `extension check is case-insensitive`() {
        assertEquals(SaveSizeCheck.VALID, SaveValidation.check(8192, "Game.GBA"))
        assertEquals(SaveSizeCheck.SIZE_MISMATCH, SaveValidation.check(1000, "Game.GBC"))
    }
}
