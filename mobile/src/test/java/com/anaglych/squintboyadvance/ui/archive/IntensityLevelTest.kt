package com.anaglych.squintboyadvance.ui.archive

import org.junit.Assert.assertEquals
import org.junit.Test

class IntensityLevelTest {

    @Test
    fun `buckets match the ramp spec`() {
        assertEquals(0, intensityLevel(0))
        assertEquals(0, intensityLevel(-1))
        assertEquals(1, intensityLevel(1))
        assertEquals(2, intensityLevel(2))
        assertEquals(2, intensityLevel(3))
        assertEquals(3, intensityLevel(4))
        assertEquals(3, intensityLevel(6))
        assertEquals(4, intensityLevel(7))
        assertEquals(4, intensityLevel(9))
        assertEquals(5, intensityLevel(10))
        assertEquals(5, intensityLevel(100))
    }
}
