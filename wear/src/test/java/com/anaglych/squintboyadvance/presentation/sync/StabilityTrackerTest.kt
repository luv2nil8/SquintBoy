package com.anaglych.squintboyadvance.presentation.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityTrackerTest {

    @Test
    fun `first sample never archives`() {
        val tracker = StabilityTracker(null)
        assertFalse(tracker.onSample("aaa"))
    }

    @Test
    fun `repeated sample archives once`() {
        val tracker = StabilityTracker(null)
        assertFalse(tracker.onSample("aaa"))
        assertTrue(tracker.onSample("aaa"))
        tracker.onArchived("aaa")
        // Stays stable but already archived — no re-archive.
        assertFalse(tracker.onSample("aaa"))
        assertFalse(tracker.onSample("aaa"))
    }

    @Test
    fun `constantly changing hashes never stabilize`() {
        val tracker = StabilityTracker(null)
        assertFalse(tracker.onSample("a"))
        assertFalse(tracker.onSample("b"))
        assertFalse(tracker.onSample("c"))
        assertFalse(tracker.onSample("d"))
    }

    @Test
    fun `change then stability archives new content`() {
        val tracker = StabilityTracker(null)
        tracker.onSample("a")
        assertTrue(tracker.onSample("a"))
        tracker.onArchived("a")
        assertFalse(tracker.onSample("b")) // torn read or fresh write
        assertTrue(tracker.onSample("b"))  // stabilized
    }

    @Test
    fun `initial archived hash from store suppresses unchanged sram`() {
        val tracker = StabilityTracker("persisted")
        assertFalse(tracker.onSample("persisted"))
        assertFalse(tracker.onSample("persisted")) // stable but identical to archived
        assertFalse(tracker.onFinalSample("persisted"))
    }

    @Test
    fun `final sample archives any unarchived content without repeat`() {
        val tracker = StabilityTracker(null)
        assertTrue(tracker.onFinalSample("x"))
        tracker.onArchived("x")
        assertFalse(tracker.onFinalSample("x"))
        assertTrue(tracker.onFinalSample("y"))
    }
}
