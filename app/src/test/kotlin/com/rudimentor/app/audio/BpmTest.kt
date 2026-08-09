package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BpmTest {
    @Test
    fun `quick steps adjust tempo and clamp to supported range`() {
        assertEquals(listOf(100, 110, 130, 140), Bpm.QUICK_STEPS.map { Bpm.adjust(120, it) })
        assertEquals(Bpm.MIN, Bpm.adjust(40, -20))
        assertEquals(Bpm.MAX, Bpm.adjust(230, 20))
    }
}
