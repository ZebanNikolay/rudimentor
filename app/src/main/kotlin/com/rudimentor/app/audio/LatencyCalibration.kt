package com.rudimentor.app.audio

import kotlin.math.abs

/**
 * The measurement behind the latency setting: the learner plays along with a slow click
 * and every stroke is timed against the beat it belongs to.
 *
 * `calculateLatencyMillis()` on this device returns nothing usable over A2DP, and the
 * guessed 24 ms residual left real strokes 190-230 ms late in the dev.36 field log, so the
 * only honest number is one the learner produces themselves (decision 154).
 *
 * The offsets it is fed are whole round trips: click written -> heard in the headphones ->
 * stick hits the pad -> onset detected. That is exactly the delay a played attempt has to
 * be compensated by, which is why the median of these samples goes straight into the
 * setting without any arithmetic on top.
 *
 * The class is deliberately free of Android and of the audio layer, so the whole rule --
 * what is dropped, how many are needed, how the number is picked -- is unit-testable.
 */
class LatencyCalibration(
    private val warmUp: Int = WARM_UP_SAMPLES,
    private val capacity: Int = MAX_SAMPLES,
) {

    /** What one round of calibration has produced so far. */
    data class Reading(
        /** Round trips accepted, oldest first. Diagnostics for the screen's own list. */
        val samples: List<Float>,
        /** Strokes dropped as warm-up, so the screen can say what it ignored. */
        val skipped: Int,
        /** Median of [samples], or null while there are not enough of them. */
        val medianMs: Float?,
        /** How far the samples scatter: the widest deviation from the median. */
        val spreadMs: Float,
    ) {
        val ready: Boolean = medianMs != null
    }

    private val samples = ArrayList<Float>(MAX_SAMPLES)
    private var skipped = 0

    /**
     * Feeds one detected stroke, as a round trip in milliseconds.
     *
     * The first [warmUp] strokes are thrown away: they are the count-in, played before
     * the learner has locked onto a click they are hearing for the first time. Values far
     * outside anything an audio path can produce are dropped too -- a chair creak or a
     * double trigger must not drag the median.
     */
    fun add(roundTripMs: Float) {
        if (!roundTripMs.isFinite()) return
        if (roundTripMs < MIN_PLAUSIBLE_MS || roundTripMs > MAX_PLAUSIBLE_MS) return
        if (skipped < warmUp) {
            skipped += 1
            return
        }
        if (samples.size >= capacity) samples.removeAt(0)
        samples.add(roundTripMs)
    }

    fun reset() {
        samples.clear()
        skipped = 0
    }

    fun reading(): Reading {
        val median = medianOf(samples)
        return Reading(
            samples = ArrayList(samples),
            skipped = skipped,
            medianMs = median,
            spreadMs = if (median == null) 0f else samples.maxOf { abs(it - median) },
        )
    }

    companion object {
        /** Strokes ignored at the start of a round: the count-in of the click. */
        const val WARM_UP_SAMPLES = 4

        /** Accepted strokes needed before a median is offered. */
        const val MIN_SAMPLES = 8

        /** Strokes kept; beyond this the oldest fall off, so a long round stays honest. */
        const val MAX_SAMPLES = 32

        /**
         * Tempo of the calibration click. At 60 bpm a beat is a full second, so a round
         * trip of up to half a second is still unambiguously "late", never "early for the
         * next beat".
         */
        const val CLICK_BPM = 60

        /** Range a real round trip can fall in. Outside it the stroke is noise. */
        const val MIN_PLAUSIBLE_MS = -60f
        const val MAX_PLAUSIBLE_MS = 400f

        /**
         * Median of the samples, or null below [MIN_SAMPLES]. The median and not the mean:
         * one stroke the learner rushed should not move the setting.
         */
        fun medianOf(values: List<Float>): Float? {
            if (values.size < MIN_SAMPLES) return null
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[middle]
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2f
            }
        }
    }
}
