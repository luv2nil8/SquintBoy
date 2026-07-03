package com.anaglych.squintboyadvance.presentation.sync

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveSyncOutboxTest {

    private lateinit var dir: File
    private lateinit var outbox: SaveSyncOutbox

    @Before
    fun setUp() {
        dir = File.createTempFile("outbox", null).apply {
            delete()
            mkdirs()
        }
        outbox = SaveSyncOutbox(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `enqueue then list round-trips meta and blob`() {
        val bytes = byteArrayOf(1, 2, 3)
        assertTrue(outbox.enqueue("Game.gba", bytes, "hash1", 1000L))

        val entries = outbox.list()
        assertEquals(1, entries.size)
        with(entries[0]) {
            assertEquals("Game.gba", meta.romId)
            assertEquals(1000L, meta.timestampMs)
            assertEquals(3L, meta.sizeBytes)
            assertEquals("hash1", meta.sha256)
            assertTrue(blobFile.readBytes().contentEquals(bytes))
        }
    }

    @Test
    fun `duplicate hash for same rom is skipped`() {
        assertTrue(outbox.enqueue("Game.gba", byteArrayOf(1), "same", 1000L))
        assertFalse(outbox.enqueue("Game.gba", byteArrayOf(1), "same", 2000L))
        assertEquals(1, outbox.list().size)
        // Same hash for a different ROM is a distinct entry.
        assertTrue(outbox.enqueue("Other.gb", byteArrayOf(1), "same", 3000L))
    }

    @Test
    fun `empty and oversized blobs are refused`() {
        assertFalse(outbox.enqueue("Game.gba", ByteArray(0), "h", 1000L))
        assertFalse(outbox.enqueue("Game.gba", ByteArray(600 * 1024), "h", 1000L))
    }

    @Test
    fun `list is oldest first`() {
        outbox.enqueue("A.gb", byteArrayOf(1), "h1", 3000L)
        outbox.enqueue("B.gb", byteArrayOf(2), "h2", 1000L)
        outbox.enqueue("C.gb", byteArrayOf(3), "h3", 2000L)
        assertEquals(listOf(1000L, 2000L, 3000L), outbox.list().map { it.meta.timestampMs })
    }

    @Test
    fun `blob without meta is swept as aborted write`() {
        File(dir, "999_orphan.blob").writeBytes(byteArrayOf(1))
        assertTrue(outbox.list().isEmpty())
        assertFalse(File(dir, "999_orphan.blob").exists())
    }

    @Test
    fun `per-rom cap keeps newest 10`() {
        for (i in 1..12) {
            outbox.enqueue("Game.gba", byteArrayOf(i.toByte()), "hash$i", i * 1000L)
        }
        val entries = outbox.list().filter { it.meta.romId == "Game.gba" }
        assertEquals(10, entries.size)
        assertEquals(3000L, entries.first().meta.timestampMs) // 1 and 2 dropped
    }

    @Test
    fun `global cap trims oldest across roms`() {
        for (rom in 1..6) {
            for (i in 1..10) {
                outbox.enqueue("Rom$rom.gb", byteArrayOf(rom.toByte(), i.toByte()),
                    "h$rom-$i", (rom * 100L + i) * 1000L)
            }
        }
        assertEquals(50, outbox.list().size)
    }

    @Test
    fun `delete removes both files`() {
        outbox.enqueue("Game.gba", byteArrayOf(1), "h", 1000L)
        val entry = outbox.list().single()
        outbox.delete(entry)
        assertTrue(outbox.list().isEmpty())
        assertEquals(0, dir.listFiles().orEmpty().size)
    }

    @Test
    fun `clear empties the outbox`() {
        outbox.enqueue("A.gb", byteArrayOf(1), "h1", 1000L)
        outbox.enqueue("B.gb", byteArrayOf(2), "h2", 2000L)
        outbox.clear()
        assertTrue(outbox.isEmpty())
    }

    @Test
    fun `rom ids with path characters are sanitized in filenames`() {
        assertTrue(outbox.enqueue("weird/name game.gba", byteArrayOf(1), "h", 1000L))
        val entry = outbox.list().single()
        assertEquals("weird/name game.gba", entry.meta.romId)
        assertFalse(entry.blobFile.name.contains('/'))
    }
}
