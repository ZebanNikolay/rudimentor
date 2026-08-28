package com.rudimentor.app.ui.practice

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A measured quantity together with the error of the measurement itself, in milliseconds.
 *
 * Nothing derived from a handful of strokes is exact, and the app used to hide that: it
 * printed a mean offset of "-62 ms" from 64 strokes that scattered by 33 ms and let the
 * player read it as a fact. [errorMs] is the standard error of the estimate, so a number
 * can be shown next to how wrong it may be, and — more importantly — so the app can stay
 * quiet when the number is not big enough to mean anything (decision 168).
 */
data class Measured(val valueMs: Float, val errorMs: Float) {
    /**
     * How many of its own errors the value is worth. Three of them is the bar for saying
     * anything out loud: for a normal spread that is a false alarm in well under 1 % of
     * clean runs, which is the rate an unprompted hint has to earn.
     */
    val sigmas: Float get() = if (errorMs <= 0f) 0f else abs(valueMs) / errorMs

    /** True when the value clears both its statistical bar and a practical one. */
    fun significant(minimumMs: Float): Boolean =
        sigmas >= SIGNIFICANT_SIGMAS && abs(valueMs) >= minimumMs

    /**
     * How far past the bar the value is, as a multiple. Comparable across quantities that
     * have different bars, which is what lets one hint win over another without a hand-made
     * priority list.
     */
    fun severity(minimumMs: Float): Float {
        if (!significant(minimumMs)) return 0f
        val statistical = sigmas / SIGNIFICANT_SIGMAS
        val practical = if (minimumMs <= 0f) statistical else abs(valueMs) / minimumMs
        return minOf(statistical, practical)
    }

    companion object {
        const val SIGNIFICANT_SIGMAS = 3f
        val Zero = Measured(0f, 0f)
    }
}

/**
 * What one attempt says about the player and about the measurement, with the error of
 * every number attached.
 *
 * Built from the offsets in the order they were played, so the drift is the run's own
 * before-and-after and not a comparison with anybody else.
 */
data class PracticeMetrics(
    /** Strokes that were matched to a note; the sample every number below rests on. */
    val judged: Int,
    /** Mean signed offset: negative is ahead of the click, positive behind it. */
    val offset: Measured,
    /** Spread of the offsets around their own mean — the run's evenness, in milliseconds. */
    val spreadMs: Float,
    /** Last quarter of the run minus its first quarter: negative means speeding up. */
    val drift: Measured,
    /** The PERFECT window of the attempt, the yardstick every quantity is compared to. */
    val perfectMs: Float,
    /**
     * Accuracy the same strokes would have scored with the mean offset taken out: what is
     * left once the constant lateness is gone. The honest half of an offset hint — it says
     * how much of the score the offset is actually holding, instead of promising 100 %.
     */
    val accuracyWithoutOffset: Float,
) {
    /** The offset is worth mentioning: proven, and larger than half a PERFECT window. */
    val offsetSignificant: Boolean get() = offset.significant(perfectMs / 2f)

    /** The drift is worth mentioning: proven, and larger than a whole PERFECT window. */
    val driftSignificant: Boolean get() = drift.significant(perfectMs)

    /** The spread has no error of its own to beat; it only has to be worth acting on. */
    val spreadSignificant: Boolean get() = spreadMs > perfectMs

    companion object {
        /** Strokes needed before any number is trusted at all. */
        const val MIN_JUDGED = 8

        val Empty = PracticeMetrics(
            judged = 0,
            offset = Measured.Zero,
            spreadMs = 0f,
            drift = Measured.Zero,
            perfectMs = HitWindows.Default.perfectMs,
            accuracyWithoutOffset = 0f,
        )

        /**
         * Measures one attempt.
         *
         * [offsets] are the graded offsets in playing order. The spread is the sample
         * standard deviation, the error of the mean is that spread over the root of the
         * count, and the drift compares the last quarter of the run with the first — the
         * two quarters are independent samples, so their difference carries the errors of
         * both.
         */
        fun of(result: PracticeResult): PracticeMetrics {
            val offsets = result.offsets.filter { !it.isNaN() }
            val windows = result.windows
            if (offsets.size < MIN_JUDGED) {
                return Empty.copy(judged = offsets.size, perfectMs = windows.perfectMs)
            }
            val mean = offsets.average().toFloat()
            val spread = spreadMs(offsets, mean)
            val standardError = spread / sqrt(offsets.size.toFloat())
            val quarter = maxOf(offsets.size / 4, 1)
            val first = offsets.take(quarter)
            val last = offsets.takeLast(quarter)
            val driftValue = last.average().toFloat() - first.average().toFloat()
            // Two quarters, each with its own error of the mean: the difference is worth
            // spread * sqrt(2 / quarter). A group of eight strokes at a 33 ms spread wanders
            // by 35 ms on its own, which is exactly why eyeballing group medians invented a
            // "speeding up to 100 ms" that the same logs do not support.
            val driftError = spread * sqrt(2f / quarter.toFloat())
            return PracticeMetrics(
                judged = offsets.size,
                offset = Measured(valueMs = mean, errorMs = standardError),
                spreadMs = spread,
                drift = Measured(valueMs = driftValue, errorMs = driftError),
                perfectMs = windows.perfectMs,
                accuracyWithoutOffset = accuracyWithout(result, mean),
            )
        }

        /** Sample standard deviation of [offsets] around [mean]. */
        private fun spreadMs(offsets: List<Float>, mean: Float): Float {
            if (offsets.size < 2) return 0f
            val sum = offsets.sumOf { offset ->
                val d = (offset - mean).toDouble()
                d * d
            }
            return sqrt(sum / (offsets.size - 1)).toFloat()
        }

        /** The accuracy of the same run with [biasMs] removed from every graded stroke. */
        private fun accuracyWithout(result: PracticeResult, biasMs: Float): Float {
            val windows = result.windows
            var weighted = 0f
            result.offsets.forEach { offset ->
                if (!offset.isNaN()) {
                    weighted += PracticeScoring.weight(windows.window(offset - biasMs))
                }
            }
            return PracticeScoring.accuracy(
                weightedNotes = weighted,
                noteCount = result.noteCount,
                extras = result.extras,
            )
        }
    }
}

/** The one thing the result screen says about a run, or nothing at all. */
enum class AdviceKind {
    /**
     * The strokes land nowhere near the notes and most notes were never matched: the click
     * is reaching the player late, not the player playing badly. Sends them to the sound
     * check rather than talking about timing.
     */
    SoundCheck,

    /** A large share of the onsets was too quiet to count, or arrived as detector ringing. */
    Detector,

    /** Strokes that matched no note: stick rebounds, or a noisy room. */
    ExtraHits,

    /** A proven constant offset from the click. */
    Offset,

    /** A proven difference between the end of the run and its beginning. */
    Drift,

    /** The strokes wander more than a PERFECT window wide. */
    Spread,

    /** Nothing to fix at this tempo. */
    AllGood,
}

/**
 * The advice shown under the score, or null when the run says nothing that can be proven.
 *
 * Silence is the normal case, not a fallback: a hint on every screen is noise, and a hint
 * built out of a number smaller than its own error is a lie. [severity] is how far past
 * its own bar the winning quantity is, which is how one hint is chosen over another —
 * there is no hand-written priority order between the quantities of the same group.
 *
 * The technical group (sound check, detector, extra hits) is settled before the playing
 * group: while the measurement itself is broken, numbers about the playing are made up.
 */
data class PracticeAdvice(
    val kind: AdviceKind,
    val severity: Float,
    /** The number the text is about, in milliseconds, or NaN when the hint has none. */
    val valueMs: Float = Float.NaN,
    /** Error of [valueMs], for the expanded view. */
    val errorMs: Float = Float.NaN,
    /** A share the text is about (quiet strokes, extra strokes), or NaN. */
    val share: Float = Float.NaN,
) {
    companion object {
        /** Below this share of judged notes the run is treated as a broken measurement. */
        const val SOUND_CHECK_JUDGED_SHARE = 0.4f

        /** Share of onsets that may be dropped before the detector is called out. */
        const val DETECTOR_DROPPED_SHARE = 0.1f

        /** Share of notes that may arrive as extra strokes before it is called out. */
        const val EXTRA_SHARE = 0.05f

        /**
         * Picks the advice for one attempt, or returns null to stay quiet.
         *
         * [offsetsOutsideOk] is what separates "the audio path is broken" from "this level
         * is too fast for me": in the broken case the few strokes that did match sit far
         * outside the OK window, and the score is unusable either way.
         */
        fun of(result: PracticeResult, metrics: PracticeMetrics = PracticeMetrics.of(result)): PracticeAdvice? {
            technical(result, metrics)?.let { return it }
            playing(result, metrics)?.let { return it }
            // Praise is the last word, and only for a run that has nothing left to fix.
            if (result.accuracy >= PracticeScoring.THREE_STAR_ACCURACY) {
                return PracticeAdvice(kind = AdviceKind.AllGood, severity = 0f)
            }
            return null
        }

        private fun technical(result: PracticeResult, metrics: PracticeMetrics): PracticeAdvice? {
            val candidates = ArrayList<PracticeAdvice>(3)
            val notes = result.noteCount
            if (notes > 0) {
                val judgedShare = metrics.judged.toFloat() / notes
                val offsetOutsideOk = metrics.judged >= PracticeMetrics.MIN_JUDGED &&
                    abs(metrics.offset.valueMs) > result.windows.okMs
                if (judgedShare < SOUND_CHECK_JUDGED_SHARE && offsetOutsideOk) {
                    candidates += PracticeAdvice(
                        kind = AdviceKind.SoundCheck,
                        severity = SOUND_CHECK_JUDGED_SHARE / maxOf(judgedShare, 0.01f),
                        valueMs = metrics.offset.valueMs,
                        errorMs = metrics.offset.errorMs,
                    )
                }
                val extraShare = result.extras.toFloat() / notes
                if (extraShare > EXTRA_SHARE) {
                    candidates += PracticeAdvice(
                        kind = AdviceKind.ExtraHits,
                        severity = extraShare / EXTRA_SHARE,
                        share = extraShare,
                    )
                }
            }
            val onsets = result.hits + result.extras + result.droppedOnsets
            if (onsets > 0) {
                val droppedShare = result.droppedOnsets.toFloat() / onsets
                if (droppedShare > DETECTOR_DROPPED_SHARE) {
                    candidates += PracticeAdvice(
                        kind = AdviceKind.Detector,
                        severity = droppedShare / DETECTOR_DROPPED_SHARE,
                        share = droppedShare,
                    )
                }
            }
            return candidates.maxByOrNull { it.severity }
        }

        private fun playing(result: PracticeResult, metrics: PracticeMetrics): PracticeAdvice? {
            if (metrics.judged < PracticeMetrics.MIN_JUDGED) return null
            val candidates = ArrayList<PracticeAdvice>(3)
            if (metrics.offsetSignificant) {
                candidates += PracticeAdvice(
                    kind = AdviceKind.Offset,
                    severity = metrics.offset.severity(metrics.perfectMs / 2f),
                    valueMs = metrics.offset.valueMs,
                    errorMs = metrics.offset.errorMs,
                )
            }
            if (metrics.driftSignificant) {
                candidates += PracticeAdvice(
                    kind = AdviceKind.Drift,
                    severity = metrics.drift.severity(metrics.perfectMs),
                    valueMs = metrics.drift.valueMs,
                    errorMs = metrics.drift.errorMs,
                )
            }
            if (metrics.spreadSignificant) {
                candidates += PracticeAdvice(
                    kind = AdviceKind.Spread,
                    severity = metrics.spreadMs / metrics.perfectMs,
                    valueMs = metrics.spreadMs,
                )
            }
            return candidates.maxByOrNull { it.severity }
        }
    }
}
