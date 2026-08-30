package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamClockDriftTest {

    private val rate = 48_000

    private fun probe(
        seconds: Double,
        inputRate: Double = rate.toDouble(),
        outputRate: Double = rate.toDouble(),
    ): NativeMicLab.ClockProbe {
        val nanos = (seconds * 1.0e9).toLong() + 1_000_000L
        return NativeMicLab.ClockProbe(
            outputFrame = (seconds * outputRate).toLong(),
            outputNanos = nanos,
            inputFrame = (seconds * inputRate).toLong(),
            inputNanos = nanos,
            outputCallbacks = (seconds * 100).toLong(),
            inputCallbacks = (seconds * 100).toLong(),
        )
    }

    @Test
    fun `first usable probe only sets the baseline`() {
        val drift = StreamClockDrift()
        assertNull(drift.sample(probe(0.0), rate))
    }

    @Test
    fun `a stream that has not run yet is ignored`() {
        val drift = StreamClockDrift()
        val silent = NativeMicLab.ClockProbe(0, 0, 0, 0, 0, 0)
        assertNull(drift.sample(silent, rate))
    }

    @Test
    fun `nothing is reported before the interval elapses`() {
        val drift = StreamClockDrift()
        drift.sample(probe(0.0), rate)
        assertNull(drift.sample(probe(0.2), rate))
    }

    @Test
    fun `identical clocks read as no drift`() {
        val drift = StreamClockDrift()
        drift.sample(probe(0.0), rate)
        val reading = drift.sample(probe(10.0), rate)!!
        assertEquals(10f, reading.elapsedSec, 0.01f)
        assertEquals(48_000f, reading.inputRateHz, 1f)
        assertEquals(48_000f, reading.outputRateHz, 1f)
        assertEquals(0f, reading.driftPpm, 1f)
        assertEquals(0f, reading.divergenceMs, 0.5f)
    }

    @Test
    fun `a slow output stream shows up as positive drift and growing divergence`() {
        val drift = StreamClockDrift()
        // The field logs show the offset sliding 0.68 ms per second, which is 680 ppm.
        val slow = rate * (1.0 - 680.0 / 1.0e6)
        drift.sample(probe(0.0, outputRate = slow), rate)
        val reading = drift.sample(probe(60.0, outputRate = slow), rate)!!
        assertEquals(680f, reading.driftPpm, 5f)
        // Over a level's worth of playing the two grids end up ~41 ms apart.
        assertEquals(60_000f * 680f / 1.0e6f, reading.divergenceMs, 1f)
        assertEquals(0f, reading.inputLagMs, 1f)
    }

    @Test
    fun `reset drops the baseline`() {
        val drift = StreamClockDrift()
        drift.sample(probe(0.0), rate)
        drift.reset()
        assertNull(drift.sample(probe(10.0), rate))
    }

    @Test
    fun `the log line carries both rates and the divergence`() {
        val drift = StreamClockDrift()
        val slow = rate * (1.0 - 680.0 / 1.0e6)
        drift.sample(probe(0.0, outputRate = slow), rate)
        val text = drift.sample(probe(30.0, outputRate = slow), rate)!!.text()
        assertEquals(true, text.startsWith("clock 30s in 48000 fps out 47967 fps"))
        // Rounding of the rate ratio can land a part per million either way.
        assertEquals(true, text.contains("drift +68"))
        assertEquals(true, text.contains("grids apart +20 ms"))
        assertEquals(true, text.contains("out +20 ms behind"))
    }
}
