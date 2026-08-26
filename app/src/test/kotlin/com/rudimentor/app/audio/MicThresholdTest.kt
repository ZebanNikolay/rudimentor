package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The loudness gate, against the two populations of the dev.37 field log: room noise at an
 * envelope of 0.011-0.020 and pad strokes at 0.25-1.02 (decision 158).
 */
class MicThresholdTest {

    @Test
    fun `the default gate separates the noise of the field log from its strokes`() {
        val noise = listOf(0.0110f, 0.0125f, 0.0170f, 0.0198f)
        val strokes = listOf(0.25f, 0.41f, 0.78f, 1.02f)
        noise.forEach {
            assertFalse("$it should not pass", MicThreshold.passes(it, MicThreshold.DEFAULT_LEVEL))
        }
        strokes.forEach {
            assertTrue("$it should pass", MicThreshold.passes(it, MicThreshold.DEFAULT_LEVEL))
        }
    }

    @Test
    fun `a zero gate lets everything through`() {
        assertTrue(MicThreshold.passes(0.0001f, MicThreshold.MIN_LEVEL))
        assertTrue(MicThreshold.passes(0f, MicThreshold.MIN_LEVEL))
    }

    @Test
    fun `a non-finite envelope never passes a real gate`() {
        assertFalse(MicThreshold.passes(Float.NaN, MicThreshold.DEFAULT_LEVEL))
    }

    @Test
    fun `clamping keeps the level inside the slider and survives nonsense`() {
        assertEquals(MicThreshold.MAX_LEVEL, MicThreshold.clamp(4f), 0.0001f)
        assertEquals(MicThreshold.MIN_LEVEL, MicThreshold.clamp(-1f), 0.0001f)
        assertEquals(MicThreshold.DEFAULT_LEVEL, MicThreshold.clamp(Float.NaN), 0.0001f)
    }

    @Test
    fun `the meter scale is logarithmic, so the room is visible on it`() {
        // On a linear meter 0.012 is 1 % of the width, under one pixel of the practice
        // HUD bar; on this one it is a fifth of the way up.
        assertTrue(MicThreshold.toFraction(0.012f) > 0.2f)
        assertEquals(0f, MicThreshold.toFraction(0f), 0.0001f)
        assertEquals(1f, MicThreshold.toFraction(1f), 0.0001f)
    }

    @Test
    fun `the slider round-trips a level`() {
        listOf(0.005f, MicThreshold.DEFAULT_LEVEL, 0.1f, MicThreshold.MAX_LEVEL).forEach {
            val back = MicThreshold.fromFraction(MicThreshold.toFraction(it))
            assertEquals(it, back, it * 0.02f)
        }
    }

    @Test
    fun `decibels are floored instead of running to minus infinity`() {
        assertEquals(MicThreshold.FLOOR_DB, MicThreshold.decibels(0f), 0.01f)
        assertEquals(0f, MicThreshold.decibels(1f), 0.01f)
        assertEquals(-20f, MicThreshold.decibels(0.1f), 0.1f)
    }
}
