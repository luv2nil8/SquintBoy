package com.anaglych.squintboyadvance.data.retention

import com.anaglych.squintboyadvance.data.db.ArchivedSaveEntity
import com.anaglych.squintboyadvance.data.db.LocalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionEngineTest {

    private val dayMs = 86_400_000L
    private val now = 1_000L * dayMs // stable "now" on a day boundary

    private var nextId = 1L

    private fun save(
        romId: String = "Game.gba",
        ageDays: Double,
        pinned: Boolean = false,
        localState: String = LocalState.PRESENT,
    ): ArchivedSaveEntity {
        val ts = now - (ageDays * dayMs).toLong()
        val day = (ts / dayMs).toInt() // monotonic day bucket, fine for tests
        return ArchivedSaveEntity(
            id = nextId++,
            romId = romId,
            gameDirName = "dir",
            fileName = "f${nextId}.sav",
            timestampMs = ts,
            dayKey = day,
            sizeBytes = 8192,
            sha256 = "h${nextId}",
            pinned = pinned,
            localState = localState,
        )
    }

    private fun policy(
        maxAgeDays: Int? = null,
        maxCountPerGame: Int? = null,
        thinAfterDays: Int? = null,
    ) = RetentionPolicy(maxAgeDays, maxCountPerGame, thinAfterDays)

    // ── No-op policies ─────────────────────────────────────────────────

    @Test
    fun `empty policy deletes nothing`() {
        val saves = List(20) { save(ageDays = it.toDouble()) }
        assertTrue(RetentionEngine.selectDeletions(saves, policy(), now).isEmpty())
    }

    @Test
    fun `empty input is fine`() {
        assertTrue(
            RetentionEngine.selectDeletions(emptyList(), policy(1, 1, 1), now).isEmpty()
        )
    }

    // ── Pinned exemption ───────────────────────────────────────────────

    @Test
    fun `pinned saves are never deleted`() {
        val saves = List(10) { save(ageDays = 100.0 + it, pinned = true) }
        val deletions = RetentionEngine.selectDeletions(saves, policy(30, 2, 7), now)
        assertTrue(deletions.isEmpty())
    }

    @Test
    fun `pinned saves do not count toward the cap`() {
        val pinned = List(5) { save(ageDays = 1.0 + it, pinned = true) }
        val unpinned = List(3) { save(ageDays = 10.0 + it) }
        val deletions = RetentionEngine.selectDeletions(pinned + unpinned, policy(maxCountPerGame = 3), now)
        assertTrue(deletions.isEmpty())
    }

    // ── Newest survivor safety ─────────────────────────────────────────

    @Test
    fun `newest save survives even past max age`() {
        val saves = listOf(save(ageDays = 400.0), save(ageDays = 500.0))
        val deletions = RetentionEngine.selectDeletions(saves, policy(maxAgeDays = 30), now)
        assertEquals(1, deletions.size)
        assertEquals(saves[1].id, deletions[0].id) // only the older one goes
    }

    @Test
    fun `tombstoned rows are not candidates`() {
        val saves = listOf(
            save(ageDays = 400.0, localState = LocalState.DELETED),
            save(ageDays = 500.0),
        )
        val deletions = RetentionEngine.selectDeletions(saves, policy(maxAgeDays = 30), now)
        // The DELETED row is ignored entirely; the 500-day row is the newest PRESENT → kept.
        assertTrue(deletions.isEmpty())
    }

    // ── Count cap ──────────────────────────────────────────────────────

    @Test
    fun `count cap keeps the newest N`() {
        val saves = List(10) { save(ageDays = it.toDouble()) } // 0 = newest
        val deletions = RetentionEngine.selectDeletions(saves, policy(maxCountPerGame = 4), now)
        assertEquals(6, deletions.size)
        val deletedIds = deletions.map { it.id }.toSet()
        for (i in 0 until 4) assertFalse(saves[i].id in deletedIds)
    }

    @Test
    fun `count cap of zero still keeps the newest`() {
        val saves = List(3) { save(ageDays = it.toDouble()) }
        val deletions = RetentionEngine.selectDeletions(saves, policy(maxCountPerGame = 0), now)
        assertEquals(2, deletions.size)
        assertFalse(saves[0].id in deletions.map { it.id })
    }

    // ── Age cap ────────────────────────────────────────────────────────

    @Test
    fun `age cap boundary is exclusive of exactly-at-cutoff`() {
        val atCutoff = save(ageDays = 30.0)
        val past = save(ageDays = 30.5)
        val fresh = save(ageDays = 1.0)
        val deletions =
            RetentionEngine.selectDeletions(listOf(atCutoff, past, fresh), policy(maxAgeDays = 30), now)
        assertEquals(listOf(past.id), deletions.map { it.id })
    }

    // ── Thinning ───────────────────────────────────────────────────────

    @Test
    fun `thinning keeps newest per day past the threshold`() {
        // 3 saves on day -10 (ages 10.1/10.5/10.9), 2 saves on day -0.2/-0.3 (recent)
        val oldA = save(ageDays = 10.1)
        val oldB = save(ageDays = 10.5)
        val oldC = save(ageDays = 10.9)
        val recentA = save(ageDays = 0.2)
        val recentB = save(ageDays = 0.3)
        val deletions = RetentionEngine.selectDeletions(
            listOf(oldA, oldB, oldC, recentA, recentB), policy(thinAfterDays = 7), now
        )
        // oldA is the newest of its day → survives; oldB/oldC deleted; recent untouched.
        assertEquals(setOf(oldB.id, oldC.id), deletions.map { it.id }.toSet())
    }

    @Test
    fun `saves newer than thinning threshold are untouched`() {
        val saves = List(5) { save(ageDays = 0.1 * (it + 1)) } // all today
        val deletions = RetentionEngine.selectDeletions(saves, policy(thinAfterDays = 7), now)
        assertTrue(deletions.isEmpty())
    }

    // ── Combined & multi-game ──────────────────────────────────────────

    @Test
    fun `policies apply per game independently`() {
        val gameA = List(5) { save(romId = "A.gb", ageDays = it.toDouble()) }
        val gameB = List(5) { save(romId = "B.gba", ageDays = it.toDouble()) }
        val deletions =
            RetentionEngine.selectDeletions(gameA + gameB, policy(maxCountPerGame = 3), now)
        assertEquals(4, deletions.size)
        assertEquals(2, deletions.count { it.romId == "A.gb" })
        assertEquals(2, deletions.count { it.romId == "B.gba" })
    }

    @Test
    fun `thinning then cap then age compose without deleting everything`() {
        val saves =
            // 4 saves per day for 20 days
            (0 until 20).flatMap { day ->
                List(4) { i -> save(ageDays = day + i * 0.1) }
            }
        val deletions = RetentionEngine.selectDeletions(
            saves, policy(maxAgeDays = 15, maxCountPerGame = 10, thinAfterDays = 7), now
        )
        val survivors = saves.filter { s -> deletions.none { it.id == s.id } }
        assertTrue(survivors.isNotEmpty())
        assertTrue(survivors.size <= 10)
        // Newest overall always among survivors.
        val newest = saves.maxBy { it.timestampMs }
        assertTrue(survivors.any { it.id == newest.id })
        // Days 8..15: at most one survivor per dayKey (thinned).
        val thinned = survivors.filter { it.timestampMs < now - 7 * dayMs }
        assertEquals(thinned.map { it.dayKey }.toSet().size, thinned.size)
    }

    @Test
    fun `unpinning re-exposes a save to the next pass`() {
        val old = save(ageDays = 100.0, pinned = true)
        val fresh = save(ageDays = 1.0)
        assertTrue(
            RetentionEngine.selectDeletions(listOf(old, fresh), policy(maxAgeDays = 30), now).isEmpty()
        )
        val unpinned = old.copy(pinned = false)
        val deletions =
            RetentionEngine.selectDeletions(listOf(unpinned, fresh), policy(maxAgeDays = 30), now)
        assertEquals(listOf(unpinned.id), deletions.map { it.id })
    }
}
