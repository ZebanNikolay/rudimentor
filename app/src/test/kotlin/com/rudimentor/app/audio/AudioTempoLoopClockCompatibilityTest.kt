package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/** These value objects must stay usable without loading the native audio library. */
class AudioTempoLoopClockCompatibilityTest {
    @Test
    fun `old Hit constructor derives an exact fallback without changing Float position`() {
        val hit = PracticeSession.Hit(123.25f, 0.2f, 0.1f)
        assertEquals(123.25f, hit.positionMs, 0f)
        assertEquals(123.25, hit.positionMsExact, 0.0)
    }

    @Test
    fun `old Poll constructor derives an exact fallback without changing Float position`() {
        val poll = PracticeSession.Poll(
            anchored = true,
            positionMs = 123.25f,
            hits = emptyList(),
            envelope = 0f,
            threshold = 0f,
            peak = 0f,
            running = true,
        )
        assertEquals(123.25f, poll.positionMs, 0f)
        assertEquals(123.25, poll.positionMsExact, 0.0)
    }

    @Test
    fun `exact positions can carry frame resolution beyond Float precision`() {
        val exact = 172_800_000.0 + 1.0 / 48
        val hit = PracticeSession.Hit(exact.toFloat(), 0.2f, 0.1f, positionMsExact = exact)
        val poll = PracticeSession.Poll(
            anchored = true,
            positionMs = exact.toFloat(),
            hits = listOf(hit),
            envelope = 0f,
            threshold = 0f,
            peak = 0f,
            running = true,
            positionMsExact = exact,
        )
        assertEquals(exact, hit.positionMsExact, 0.0)
        assertEquals(exact, poll.positionMsExact, 0.0)
        assertEquals(172_800_000f, poll.positionMs, 0f)
    }
}
