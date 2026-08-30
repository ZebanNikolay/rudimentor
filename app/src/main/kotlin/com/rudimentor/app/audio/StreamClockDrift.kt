package com.rudimentor.app.audio

/**
 * Measures whether the two audio streams really run on one clock.
 *
 * Notes and the click are placed on output frames, strokes are stamped on input frames.
 * The dev.48 field logs show the offset of a run sliding by 0.68 ms/s (680 ppm) and the
 * calibrated round trip overstated by ~93 ms, which is what two clocks ticking at slightly
 * different real rates would look like. This is the instrument that says whether that is
 * what happens: pure arithmetic over [NativeMicLab.ClockProbe] readings, no compensation
 * anywhere (decision 188).
 *
 * Each stream's frame counter is compared against wall time. A stream that has served
 * fewer frames than the nominal sample rate would predict is "behind" wall time, and the
 * difference of the two lags is the divergence between the note grid and the stroke grid.
 * Everything is measured from the first usable reading, so the numbers are cumulative and
 * the trend matters more than a single line.
 */
class StreamClockDrift(private val intervalMs: Long = DEFAULT_INTERVAL_MS) {

    /** One line of the diagnostic, ready for the log. */
    data class Reading(
        /** Wall seconds since the first usable reading. */
        val elapsedSec: Float,
        /** Frames per second of wall time each stream actually served. */
        val inputRateHz: Float,
        val outputRateHz: Float,
        /** How much faster the input clock runs than the output one, in parts per million. */
        val driftPpm: Float,
        /** Milliseconds each stream fell behind wall time, at the nominal sample rate. */
        val inputLagMs: Float,
        val outputLagMs: Float,
        /**
         * Milliseconds the note grid has moved away from the stroke grid since the start.
         * This is the number the field logs see as the run "drifting": if it grows to tens
         * of milliseconds over a level, the two clocks are the cause.
         */
        val divergenceMs: Float,
        val inputCallbacks: Long,
        val outputCallbacks: Long,
    ) {
        /** Compact one-liner for the dev log. */
        fun text(): String = buildString {
            append("clock ")
            append(elapsedSec.toInt())
            append("s in ")
            append(inputRateHz.toInt())
            append(" fps out ")
            append(outputRateHz.toInt())
            append(" fps drift ")
            append(signed(driftPpm))
            append(" ppm, grids apart ")
            append(signed(divergenceMs))
            append(" ms (in ")
            append(signed(inputLagMs))
            append(", out ")
            append(signed(outputLagMs))
            append(" ms behind), cb ")
            append(inputCallbacks)
            append('/')
            append(outputCallbacks)
        }

        private fun signed(value: Float): String {
            val rounded = kotlin.math.round(value).toInt()
            return if (rounded > 0) "+$rounded" else rounded.toString()
        }
    }

    private var base: NativeMicLab.ClockProbe? = null
    private var lastEmitNanos = 0L

    fun reset() {
        base = null
        lastEmitNanos = 0L
    }

    /**
     * Folds one probe in. Returns a reading at most once per [intervalMs] of wall time, and
     * null the rest of the time, so the caller can poll at any rate it likes.
     */
    fun sample(probe: NativeMicLab.ClockProbe, sampleRate: Int): Reading? {
        if (sampleRate <= 0) return null
        // Both streams have to have run at least once, otherwise there is no clock to read.
        if (probe.inputNanos <= 0L || probe.outputNanos <= 0L) return null
        val start = base
        if (start == null) {
            base = probe
            lastEmitNanos = probe.inputNanos
            return null
        }
        if (probe.inputNanos - lastEmitNanos < intervalMs * 1_000_000L) return null

        val inputSec = (probe.inputNanos - start.inputNanos) / 1.0e9
        val outputSec = (probe.outputNanos - start.outputNanos) / 1.0e9
        if (inputSec < MIN_SPAN_SEC || outputSec < MIN_SPAN_SEC) return null
        lastEmitNanos = probe.inputNanos

        val inputFrames = (probe.inputFrame - start.inputFrame).toDouble()
        val outputFrames = (probe.outputFrame - start.outputFrame).toDouble()
        val inputRate = inputFrames / inputSec
        val outputRate = outputFrames / outputSec
        val ppm = if (outputRate > 0.0) (inputRate / outputRate - 1.0) * 1.0e6 else 0.0
        val nominal = sampleRate / 1000.0
        val inputLag = inputSec * 1000.0 - inputFrames / nominal
        val outputLag = outputSec * 1000.0 - outputFrames / nominal
        return Reading(
            elapsedSec = inputSec.toFloat(),
            inputRateHz = inputRate.toFloat(),
            outputRateHz = outputRate.toFloat(),
            driftPpm = ppm.toFloat(),
            inputLagMs = inputLag.toFloat(),
            outputLagMs = outputLag.toFloat(),
            divergenceMs = (outputLag - inputLag).toFloat(),
            inputCallbacks = probe.inputCallbacks,
            outputCallbacks = probe.outputCallbacks,
        )
    }

    companion object {
        /** One line per second: enough to see a 0.68 ms/s slope, short enough to read. */
        const val DEFAULT_INTERVAL_MS = 1000L

        /** Below this span the rates are dominated by a single buffer of jitter. */
        private const val MIN_SPAN_SEC = 0.5
    }
}
