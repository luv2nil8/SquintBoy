package com.anaglych.squintboyadvance.data.archive

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDirNamingTest {

    @Test
    fun `dir name is human readable with stable hash suffix`() {
        val name = GameDirNaming.gameDirName("Pokemon Crystal.gbc")
        assertTrue(name.startsWith("Pokemon Crystal-"))
        assertEquals(name, GameDirNaming.gameDirName("Pokemon Crystal.gbc"))
    }

    @Test
    fun `same base name different extension gets different dirs`() {
        assertNotEquals(
            GameDirNaming.gameDirName("Tetris.gb"),
            GameDirNaming.gameDirName("Tetris.gbc"),
        )
    }

    @Test
    fun `unsafe characters are sanitized`() {
        val name = GameDirNaming.gameDirName("weird/rom\\name*?.gba")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.contains('*'))
        assertFalse(name.contains('?'))
    }

    @Test
    fun `very long names are truncated but keep suffix`() {
        val longId = "A".repeat(100) + ".gba"
        val name = GameDirNaming.gameDirName(longId)
        assertTrue(name.length <= 40 + 1 + 8)
        assertTrue(Regex("-[0-9a-f]{8}$").containsMatchIn(name))
    }

    @Test
    fun `save file name embeds timestamp and hash prefix`() {
        val utc = ZoneId.of("UTC")
        // 2026-07-02T14:35:01Z
        val ts = 1_782_830_101_000L
        val name = GameDirNaming.saveFileName(ts, "deadbeefcafebabe0123", utc)
        assertTrue(name.endsWith("_deadbeef.sav"))
        assertTrue(Regex("^\\d{8}_\\d{6}_").containsMatchIn(name))
    }

    @Test
    fun `dayKey packs local date as yyyymmdd`() {
        val utc = ZoneId.of("UTC")
        // 1970-01-02T00:00:00Z
        assertEquals(19700102, GameDirNaming.dayKey(86_400_000L, utc))
    }
}
