package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatPatternTest {
    @Test
    fun `default pattern accents only the first beat`() {
        val pattern = BeatPattern.default()

        assertEquals(4, pattern.size)
        assertEquals(1, pattern.accentMask)
        assertEquals(listOf(true, false, false, false), pattern.accents)
    }

    @Test
    fun `toggle changes the selected accent and mask`() {
        val pattern = BeatPattern.default().toggleAccent(0).toggleAccent(2)

        assertFalse(pattern.accents[0])
        assertTrue(pattern.accents[2])
        assertEquals(4, pattern.accentMask)
    }

    @Test
    fun `beat count stays within four and eight beats`() {
        val minimum = BeatPattern.default()
        assertSame(minimum, minimum.removeBeat())

        val maximum = generateSequence(minimum) { it.addBeat() }
            .first { it.size == BeatPattern.MAX_BEATS }

        assertEquals(8, maximum.size)
        assertSame(maximum, maximum.addBeat())
        assertEquals(7, maximum.removeBeat().size)
    }
}
