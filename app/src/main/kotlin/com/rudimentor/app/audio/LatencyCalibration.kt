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

    /** What became of one detected stroke. The screen logs this, nothing else. */
    enum class Outcome {
        /** Counted towards the median. */
        Accepted,

        /** One of the first strokes of the round, dropped as count-in. */
        WarmUp,

        /** Outside anything an audio path can produce: noise, or a double trigger. */
        Rejected,

        /** The round already holds every stroke it needs, so this one is ignored. */
        Full,
    }

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
        /**
         * True once the round holds [MAX_SAMPLES] strokes. The round ends there instead of
         * running forever behind a sliding window: the counter on the screen said "of 32"
         * and then kept going, so the learner had no way of knowing when to stop
         * (decision 157).
         */
        val complete: Boolean,
    ) {
        val ready: Boolean = medianMs != null

        /**
         * True once the median cannot meaningfully move any more: enough strokes, and they
         * all land within [SETTLED_SPREAD_MS] of each other.
         *
         * This is what makes the objective measurement short. With the earcup held against
         * the microphone the app hears its own click and the samples repeat to a fraction of
         * a millisecond, so waiting for all [MAX_SAMPLES] of them buys nothing and costs the
         * learner half a minute of standing still. Strokes played by hand scatter by ~20 ms
         * and never settle, so that round still runs its full length, where the extra
         * samples do narrow the median.
         */
        val settled: Boolean = medianMs != null &&
            samples.size >= MIN_SAMPLES &&
            spreadMs <= SETTLED_SPREAD_MS

        /** Nothing more to measure: either it settled, or the round ran out of strokes. */
        val finished: Boolean = complete || settled
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
    fun add(roundTripMs: Float): Outcome {
        if (samples.size >= capacity) return Outcome.Full
        if (!roundTripMs.isFinite()) return Outcome.Rejected
        if (roundTripMs < MIN_PLAUSIBLE_MS || roundTripMs > MAX_PLAUSIBLE_MS) {
            return Outcome.Rejected
        }
        if (skipped < warmUp) {
            skipped += 1
            return Outcome.WarmUp
        }
        samples.add(roundTripMs)
        return Outcome.Accepted
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
            complete = samples.size >= capacity,
        )
    }

    companion object {
        /** Strokes ignored at the start of a round: the count-in of the click. */
        const val WARM_UP_SAMPLES = 4

        /** Accepted strokes needed before a median is offered. */
        const val MIN_SAMPLES = 8

        /** Strokes one round measures at most. The round is over once it has them. */
        const val MAX_SAMPLES = 32

        /**
         * Scatter under which the round stops early, as [Reading.settled] describes. The
         * objective measurement scatters by a fraction of a millisecond and clears this at
         * once; strokes played by hand scatter by tens of milliseconds and never do.
         */
        const val SETTLED_SPREAD_MS = 5f

        /**
         * Tempo of the calibration click. At 60 bpm a beat is a full second, so a round
         * trip anywhere inside [MAX_PLAUSIBLE_MS] is still unambiguously "late", never
         * "early for the next beat".
         */
        const val CLICK_BPM = 60

        /**
         * Range a real round trip can fall in. Outside it the stroke is noise.
         *
         * The ceiling is not academic: these headphones measured 300…307 ms over seven
         * runs, so a slower Bluetooth pair would have been thrown away as noise at the
         * old 400 ms limit and calibration would simply never complete.
         */
        const val MIN_PLAUSIBLE_MS = -60f
        const val MAX_PLAUSIBLE_MS = 600f

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
