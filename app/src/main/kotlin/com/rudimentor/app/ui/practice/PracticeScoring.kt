package com.rudimentor.app.ui.practice

import kotlin.math.abs
import kotlin.math.roundToInt

/** How close a stick hit landed to its note. */
enum class HitWindow {
    Perfect,
    Good,
    Ok,
    Miss,
}

/** One judged note: the signed offset in milliseconds and the window it fell into. */
data class NoteJudgement(
    val offsetMs: Float,
    val window: HitWindow,
)

/**
 * The three timing windows of one attempt, in milliseconds.
 *
 * The base numbers never change (25 / 60 / 120, decision 125). What changes is the
 * ceiling: on a level whose notes sit closer together than twice the OK window the
 * windows of neighbouring notes would overlap, so every window is clamped to half the
 * shortest interval. Computed once when the note list is built, never per hit.
 */
data class HitWindows(
    val perfectMs: Float,
    val goodMs: Float,
    val okMs: Float,
) {
    fun window(offsetMs: Float): HitWindow {
        val magnitude = abs(offsetMs)
        return when {
            magnitude <= perfectMs -> HitWindow.Perfect
            magnitude <= goodMs -> HitWindow.Good
            magnitude <= okMs -> HitWindow.Ok
            else -> HitWindow.Miss
        }
    }

    companion object {
        val Default = HitWindows(
            perfectMs = PracticeScoring.PERFECT_MS,
            goodMs = PracticeScoring.GOOD_MS,
            okMs = PracticeScoring.OK_MS,
        )

        /**
         * Windows for a level whose shortest gap between notes is [minIntervalMs].
         * `OK = min(120, I / 2)`, and the tighter windows follow it down so the order
         * PERFECT <= GOOD <= OK always holds.
         */
        fun forMinInterval(minIntervalMs: Float): HitWindows {
            if (minIntervalMs <= 0f) return Default
            val ok = minOf(PracticeScoring.OK_MS, minIntervalMs / 2f)
            val good = minOf(PracticeScoring.GOOD_MS, ok)
            val perfect = minOf(PracticeScoring.PERFECT_MS, good)
            return HitWindows(perfectMs = perfect, goodMs = good, okMs = ok)
        }
    }
}

/**
 * Timing windows, accuracy and stars of a practice attempt.
 *
 * One number carries the whole result: `accuracy = Σ weight / (N + E)`. There are no
 * points, no combo multiplier and no letter ranks -- an extra hit grows the
 * denominator instead of subtracting a penalty, so the result cannot leave 0…100 %
 * and the cost of a mistake scales with the length of the level (decision 125).
 */
object PracticeScoring {
    const val PERFECT_MS = 25f
    const val GOOD_MS = 60f
    const val OK_MS = 120f

    /**
     * Extra time a note waits before it is written off as a miss. The poll loop and
     * the audio buffers report a hit up to a buffer late, and without this grace the
     * note expired first and the stroke was counted as an extra (decision 101).
     */
    const val EXPIRE_GRACE_MS = 60f

    /**
     * A stroke this soon after an already counted one is a double trigger of the onset
     * detector, not a stroke: dropped before scoring. An input filter, not a handout --
     * a real extra hit is still charged, otherwise the single number would lie.
     */
    const val DEBOUNCE_MS = 30f

    /** Below this the level is not passed and the next node stays closed. */
    const val PASS_ACCURACY = 0.80f
    const val TWO_STAR_ACCURACY = 0.90f
    const val THREE_STAR_ACCURACY = 0.96f

    const val COUNT_IN_BEATS = 4

    /**
     * Deviation scale and result histogram share one fixed range: the plots always
     * read from -120 to +120 ms, whatever the windows of the current level are.
     */
    const val SCALE_MS = OK_MS
    const val HISTOGRAM_BINS = 30
    const val RECENT_OFFSETS = 24

    /** Window of an offset against the base windows -- used to colour the plots. */
    fun window(offsetMs: Float): HitWindow = HitWindows.Default.window(offsetMs)

    /** Accuracy weight of a window. A miss is worth nothing. */
    fun weight(window: HitWindow): Float = when (window) {
        HitWindow.Perfect -> 1f
        HitWindow.Good -> 0.8f
        HitWindow.Ok -> 0.4f
        HitWindow.Miss -> 0f
    }

    /**
     * Stars of an attempt. Four states in all (decision 126): under the pass bar
     * nothing, then one star for the pass, two for 90 %, and the third only for a
     * clean run -- the third star *is* FULL COMBO, there is no "3 stars with a miss".
     */
    fun stars(accuracy: Float, misses: Int, extras: Int): Int = when {
        accuracy < PASS_ACCURACY -> 0
        accuracy >= THREE_STAR_ACCURACY && misses == 0 && extras == 0 -> 3
        accuracy >= TWO_STAR_ACCURACY -> 2
        else -> 1
    }

    /** Bin index of an offset on the result histogram, or null when out of range. */
    fun histogramBin(offsetMs: Float, bins: Int = HISTOGRAM_BINS): Int? {
        if (abs(offsetMs) > SCALE_MS) return null
        val normalized = (offsetMs + SCALE_MS) / (SCALE_MS * 2f)
        return (normalized * bins).toInt().coerceIn(0, bins - 1)
    }

    /** The verdict is always milliseconds -- never EARLY / LATE (decision 86). */
    fun verdictLabel(offsetMs: Float): String {
        val rounded = offsetMs.roundToInt()
        return if (rounded > 0) "+$rounded ms" else "$rounded ms"
    }
}

/** Everything the result screen shows. Pure data: no Android, no audio. */
data class PracticeResult(
    val noteCount: Int,
    val perfect: Int,
    val good: Int,
    val ok: Int,
    val misses: Int,
    val extras: Int,
    val maxCombo: Int,
    val accuracy: Float,
    val meanOffsetMs: Float,
    val offsets: List<Float>,
) {
    val hits: Int get() = perfect + good + ok
    val passed: Boolean get() = accuracy >= PracticeScoring.PASS_ACCURACY
    val stars: Int get() = PracticeScoring.stars(accuracy, misses, extras)

    /** The third star and FULL COMBO are one and the same state (decision 126). */
    val fullCombo: Boolean get() = stars == 3

    /** Every note in the PERFECT window and not one stroke too many. */
    val allPerfect: Boolean
        get() = noteCount > 0 && perfect == noteCount && extras == 0

    companion object {
        val Empty = PracticeResult(
            noteCount = 0,
            perfect = 0,
            good = 0,
            ok = 0,
            misses = 0,
            extras = 0,
            maxCombo = 0,
            accuracy = 0f,
            meanOffsetMs = 0f,
            offsets = emptyList(),
        )
    }
}
