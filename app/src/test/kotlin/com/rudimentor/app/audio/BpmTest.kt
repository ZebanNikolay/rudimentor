package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BpmTest {
    @Test
    fun `adjust clamps tempo to supported range`() {
        assertEquals(Bpm.MIN, Bpm.adjust(Bpm.MIN, -5))
        assertEquals(119, Bpm.adjust(120, -1))
        assertEquals(125, Bpm.adjust(120, 5))
        assertEquals(Bpm.MAX, Bpm.adjust(Bpm.MAX, 1))
    }
}
