package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calibration rule: what is thrown away, how many strokes are needed and how the
 * number handed to the setting is picked (decision 154).
 */
class LatencyCalibrationTest {

    @Test
    fun `the count-in is thrown away before anything is collected`() {
        val calibration = LatencyCalibration()
        repeat(LatencyCalibration.WARM_UP_SAMPLES) { calibration.add(208f) }
        val reading = calibration.reading()
        assertEquals(LatencyCalibration.WARM_UP_SAMPLES, reading.skipped)
        assertTrue(reading.samples.isEmpty())
        assertFalse(reading.ready)
    }

    @Test
    fun `no number is offered below the minimum count`() {
        val calibration = LatencyCalibration(warmUp = 0)
        repeat(LatencyCalibration.MIN_SAMPLES - 1) { calibration.add(200f) }
        assertNull(calibration.reading().medianMs)
        calibration.add(200f)
        assertEquals(200f, calibration.reading().medianMs!!, 0.01f)
    }

    @Test
    fun `one rushed stroke does not move the median`() {
        val calibration = LatencyCalibration(warmUp = 0)
        repeat(LatencyCalibration.MIN_SAMPLES - 1) { calibration.add(210f) }
        calibration.add(60f)
        val reading = calibration.reading()
        assertEquals(210f, reading.medianMs!!, 0.01f)
        assertEquals(150f, reading.spreadMs, 0.01f)
    }

    @Test
    fun `a creak outside the plausible range never enters the samples`() {
        val calibration = LatencyCalibration(warmUp = 0)
        calibration.add(LatencyCalibration.MAX_PLAUSIBLE_MS + 1f)
        calibration.add(LatencyCalibration.MIN_PLAUSIBLE_MS - 1f)
        calibration.add(Float.NaN)
        calibration.add(Float.POSITIVE_INFINITY)
        val reading = calibration.reading()
        assertTrue(reading.samples.isEmpty())
        assertEquals(0, reading.skipped)
    }

    @Test
    fun `a full round is complete and ignores anything after it`() {
        val calibration = LatencyCalibration(warmUp = 0, capacity = 8)
        repeat(8) { calibration.add(100f) }
        assertTrue(calibration.reading().complete)
        // The round used to slide its window forward for ever, so the counter never
        // filled up and the screen asked for strokes until the learner gave up
        // (decision 157). Now the extra strokes are ignored.
        assertEquals(
            LatencyCalibration.Outcome.Full,
            calibration.add(300f),
        )
        val reading = calibration.reading()
        assertEquals(8, reading.samples.size)
        assertEquals(100f, reading.medianMs!!, 0.01f)
    }

    @Test
    fun `an even number of samples averages the middle pair`() {
        assertEquals(
            205f,
            LatencyCalibration.medianOf(
                listOf(190f, 195f, 200f, 200f, 210f, 215f, 220f, 230f),
            )!!,
            0.01f,
        )
    }

    @Test
    fun `reset drops the count-in as well as the samples`() {
        val calibration = LatencyCalibration()
        repeat(LatencyCalibration.WARM_UP_SAMPLES + LatencyCalibration.MIN_SAMPLES) {
            calibration.add(208f)
        }
        assertTrue(calibration.reading().ready)
        calibration.reset()
        val reading = calibration.reading()
        assertEquals(0, reading.skipped)
        assertTrue(reading.samples.isEmpty())
        assertFalse(reading.ready)
    }
}
