package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The measured gate: the room, then the strokes, then a level between them (decision 158).
 */
class ThresholdProbeTest {

    private fun probe(noise: List<Float>, strokes: List<Float>): ThresholdProbe {
        val probe = ThresholdProbe()
        noise.forEach(probe::addNoise)
        probe.startStrokes()
        strokes.forEach(probe::addStroke)
        return probe
    }

    @Test
    fun `the field log room and strokes give a gate between them`() {
        val result = probe(
            noise = listOf(0.011f, 0.017f, 0.0198f),
            strokes = List(8) { 0.4f },
        ).result()!!
        assertEquals(0.0198f, result.noiseLevel, 0.0001f)
        assertEquals(0.4f, result.strokeLevel, 0.0001f)
        assertTrue(result.separated)
        assertTrue(result.thresholdLevel > result.noiseLevel)
        assertTrue(result.thresholdLevel < result.strokeLevel)
        // Every noise event of the log is refused and every stroke of it passes.
        assertFalse(MicThreshold.passes(0.0198f, result.thresholdLevel))
        assertTrue(MicThreshold.passes(0.25f, result.thresholdLevel))
    }

    @Test
    fun `no result before the strokes are in`() {
        val probe = probe(noise = listOf(0.01f), strokes = List(7) { 0.5f })
        assertNull(probe.result())
        assertEquals(7, probe.strokeCount)
    }

    @Test
    fun `noise cannot be counted as a stroke`() {
        val probe = ThresholdProbe()
        probe.addNoise(0.02f)
        probe.startStrokes()
        // Another noise event at the level just measured: it must not define the stroke
        // level that is meant to gate it away.
        assertFalse(probe.addStroke(0.021f))
        assertTrue(probe.addStroke(0.4f))
        assertEquals(1, probe.strokeCount)
    }

    @Test
    fun `a room as loud as the playing is reported instead of gating the playing away`() {
        val result = probe(
            noise = listOf(0.3f),
            strokes = List(8) { 0.35f },
        ).result()
        // The strokes never clear the separation bar, so none of them is counted and the
        // probe has nothing to report: the screen asks for a quieter room.
        assertNull(result)
    }

    @Test
    fun `a quiet room still gets a usable gate`() {
        // Three times almost nothing is still almost nothing, so the gate falls back to a
        // fraction of the stroke.
        val result = probe(noise = listOf(0.0002f), strokes = List(8) { 0.5f }).result()!!
        assertTrue(result.thresholdLevel > 0.01f)
        assertTrue(MicThreshold.passes(0.2f, result.thresholdLevel))
    }

    @Test
    fun `the gate stays under half a stroke so soft strokes still pass`() {
        val result = probe(noise = listOf(0.05f), strokes = List(8) { 0.3f }).result()!!
        assertTrue(result.thresholdLevel <= 0.3f * ThresholdProbe.STROKE_CEILING + 0.0001f)
    }

    @Test
    fun `reset puts the probe back at the room`() {
        val probe = probe(noise = listOf(0.02f), strokes = List(8) { 0.4f })
        probe.reset()
        assertEquals(ThresholdProbe.Stage.Noise, probe.stage)
        assertEquals(0, probe.strokeCount)
        assertEquals(0f, probe.measuredNoise, 0.0001f)
        assertNull(probe.result())
    }
}
