package com.rudimentor.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The self-calibration of one run (decision 167). The numbers come from the dev.41 field log:
 * a calibrated phone that still read every stroke +55…+65 ms late, with a spread of 25 ms.
 */
class LatencyTrackerTest {
    @Test
    fun `the capture phase reads the lateness of the run off its first strokes`() {
        val tracker = LatencyTracker()
        assertFalse(tracker.captured)
        // While it is capturing, the matching gets slack: a stroke 60 ms out has to reach its
        // note, or the opening of the run is charged as extras and misses at once.
        assertEquals(LatencyTracker.CAPTURE_WINDOW_MS, tracker.slackMs, 0.001f)

        listOf(58f, 62f, 55f, 65f).forEach { tracker.observe(it, windowMs = 85f) }

        assertTrue(tracker.captured)
        assertEquals(60f, tracker.biasMs, 0.001f)
        assertEquals(60f, tracker.captureMedianMs()!!, 0.001f)
        // Once the bias is known the windows go back to their real size.
        assertEquals(0f, tracker.slackMs, 0.001f)
        // And the judging clock is that much earlier than the arrival clock.
        assertEquals(940f, tracker.adjust(1000f), 0.001f)
    }

    @Test
    fun `a steady late run is graded as if it were on time`() {
        val tracker = LatencyTracker()
        // 64 strokes, every one 60 ms late, played steadily: the bias absorbs the lateness and
        // what is left is the playing itself, which is dead on.
        var last = 0f
        repeat(64) {
            tracker.observe(60f, windowMs = 85f)
            last = 60f - tracker.biasMs
        }
        assertEquals(60f, tracker.biasMs, 1f)
        assertEquals(0f, last, 1f)
    }

    @Test
    fun `tracking follows a drifting audio path without chasing the player`() {
        val tracker = LatencyTracker()
        // Capture on +115 ms, the opening of dev.41.
        repeat(LatencyTracker.CAPTURE_HITS) { tracker.observe(115f, windowMs = 85f) }
        assertEquals(115f, tracker.biasMs, 0.001f)

        // The path then drifts to +30 ms over the run; the tracker has to arrive there.
        repeat(120) { tracker.observe(30f, windowMs = 85f) }
        assertTrue("bias ${tracker.biasMs}", tracker.biasMs < 40f)

        // One wild stroke outside the window is a mistake by the player, not the phone:
        // it must not move the clock at all.
        val before = tracker.biasMs
        tracker.observe(before + 400f, windowMs = 85f)
        assertEquals(before, tracker.biasMs, 0.001f)
    }

    @Test
    fun `the bias never runs away`() {
        val tracker = LatencyTracker()
        repeat(LatencyTracker.CAPTURE_HITS) { tracker.observe(4_000f, windowMs = 85f) }
        assertEquals(LatencyTracker.MAX_BIAS_MS, tracker.biasMs, 0.001f)
    }

    @Test
    fun `a disabled tracker leaves the judging clock alone`() {
        val tracker = LatencyTracker.disabled()
        assertTrue(tracker.captured)
        assertEquals(0f, tracker.slackMs, 0.001f)
        repeat(20) { tracker.observe(60f, windowMs = 85f) }
        assertEquals(0f, tracker.biasMs, 0.001f)
        assertEquals(1000f, tracker.adjust(1000f), 0.001f)
    }
}
