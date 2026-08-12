package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BpmTest {
    @Test
    fun `the tempo keys move in tens and stay inside the supported range`() {
        assertEquals(110, Bpm.adjust(Bpm.DEFAULT, Bpm.STEP))
        assertEquals(90, Bpm.adjust(Bpm.DEFAULT, -Bpm.STEP))
        assertEquals(Bpm.MIN, Bpm.adjust(Bpm.MIN, -Bpm.STEP))
        assertEquals(Bpm.MAX, Bpm.adjust(Bpm.MAX, Bpm.STEP))
    }

    @Test
    fun `the range ends disable the matching key`() {
        assertFalse(Bpm.canDecrease(Bpm.MIN))
        assertTrue(Bpm.canDecrease(Bpm.MIN + Bpm.STEP))
        assertFalse(Bpm.canIncrease(Bpm.MAX))
        assertTrue(Bpm.canIncrease(Bpm.MAX - Bpm.STEP))
    }

    @Test
    fun `the default tempo is a practice pad tempo inside the range`() {
        assertEquals(100, Bpm.DEFAULT)
        assertEquals(40, Bpm.MIN)
        assertEquals(250, Bpm.MAX)
    }
}
