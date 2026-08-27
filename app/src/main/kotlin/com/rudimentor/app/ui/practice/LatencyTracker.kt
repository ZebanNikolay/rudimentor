package com.rudimentor.app.ui.practice

import kotlin.math.abs

/**
 * Keeps the judging clock on the strokes instead of on one calibrated number.
 *
 * One calibration run cannot describe the round trip of the next one. The field logs say so
 * outright: dev.41 measured 167 ms in the calibration screen and then played a level where
 * every stroke sat +55…+65 ms late with a spread of only 25 ms, i.e. the player was steady
 * and the number was wrong. The stream-start skew was 548 ms while measuring and 200 ms
 * while playing, and the OS reported 4 ms of output latency on a Bluetooth path that is
 * plainly worth hundreds -- so the same phone, the same headphones and the same pad give a
 * different round trip on every start(), and no amount of care in the calibration screen can
 * fix that from the outside (decision 167).
 *
 * So the attempt measures it itself, from the strokes it is already judging:
 *
 *  * **Capture.** The first [captureHits] strokes are matched with an extra [captureWindowMs]
 *    of slack, and each one moves the bias to the median of the strokes seen so far -- so the
 *    opening is graded against its own median instead of against a number the phone got
 *    wrong. Before this the attempt cannot tell a late stroke from a late phone, which is how
 *    dev.41 turned a steady opening into 13 misses and 13 extras in seven seconds.
 *  * **Tracking.** After that the bias follows the strokes slowly ([alpha]), and only strokes
 *    that already land inside their window move it. Bluetooth latency drifts over a run --
 *    dev.41 went from +115 ms to +30 ms across 34 seconds -- and this is what follows it.
 *    The step is small on purpose: a player's own timing errors average out around zero, so
 *    a slow follower absorbs the phone and leaves the playing alone.
 *
 * Pure logic, no Android and no audio: the whole self-calibration path is unit-testable.
 */
class LatencyTracker(
    private val captureHits: Int = CAPTURE_HITS,
    val captureWindowMs: Float = CAPTURE_WINDOW_MS,
    private val maxBiasMs: Float = MAX_BIAS_MS,
    private val alpha: Float = TRACK_ALPHA,
) {
    /** Systematic offset of this run, in milliseconds: positive when strokes read late. */
    var biasMs: Float = 0f
        private set

    /** True once the capture phase is over and [biasMs] means something. */
    var captured: Boolean = captureHits <= 0
        private set

    /** Raw offsets the capture phase collected, kept for the log. */
    private val captureSamples = ArrayList<Float>(captureHits.coerceAtLeast(1))

    /** Strokes that have moved the bias, capture included: the log states the sample size. */
    var sampleCount: Int = 0
        private set

    /** Extra slack the note matching and the miss expiry get while the bias is unknown. */
    val slackMs: Float get() = if (captured) 0f else captureWindowMs

    /** Position of [positionMs] on the judging clock. */
    fun adjust(positionMs: Float): Float = positionMs - biasMs

    /**
     * Feeds one raw offset (stroke position minus note time, before any bias) and returns
     * true when the bias moved enough to be worth a log line.
     *
     * [windowMs] is the OK window of that note: in the tracking phase a stroke further out
     * than that is a real mistake by the player and must not drag the clock with it.
     */
    fun observe(rawOffsetMs: Float, windowMs: Float): Boolean {
        if (rawOffsetMs.isNaN()) return false
        sampleCount += 1
        if (!captured) {
            captureSamples.add(rawOffsetMs)
            biasMs = median(captureSamples).coerceIn(-maxBiasMs, maxBiasMs)
            captured = captureSamples.size >= captureHits
            return captured
        }
        val error = rawOffsetMs - biasMs
        if (abs(error) > windowMs) return false
        val before = biasMs
        biasMs = (biasMs + alpha * error).coerceIn(-maxBiasMs, maxBiasMs)
        return abs(biasMs - before) >= LOG_STEP_MS
    }

    /** Median of the capture samples, or null before the capture phase finished. */
    fun captureMedianMs(): Float? =
        if (captureSamples.size < captureHits) null else median(captureSamples)

    companion object {
        /**
         * A tracker that never moves: the judging clock is exactly the calibrated one.
         *
         * This is the default of [PracticeAttempt] so that scoring tests read what they
         * assert. The practice screen builds a live one.
         */
        fun disabled(): LatencyTracker = LatencyTracker(captureHits = 0, alpha = 0f)

        /**
         * Strokes the capture phase spends. Four is one bar of quarters: long enough for a
         * median to survive one bad stroke, short enough that the run is honest from bar two.
         */
        const val CAPTURE_HITS = 4

        /**
         * Slack the capture phase adds to every window. It has to cover the worst round trip
         * a calibrated run can still be wrong by -- Bluetooth on this device has been out by
         * 145 ms -- without reaching into the neighbouring note at the tempos the app plays.
         */
        const val CAPTURE_WINDOW_MS = 180f

        /** The bias never grows past this: beyond it the audio path is broken, not late. */
        const val MAX_BIAS_MS = 200f

        /**
         * How much of each stroke's error the bias takes. 6 % settles a drift of the size
         * dev.41 showed within a couple of bars and still ignores a player who rushes one
         * note in four.
         */
        const val TRACK_ALPHA = 0.06f

        /** Bias movement worth a log line, so the tracking phase does not flood the log. */
        const val LOG_STEP_MS = 8f

        private fun median(values: List<Float>): Float {
            if (values.isEmpty()) return 0f
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
