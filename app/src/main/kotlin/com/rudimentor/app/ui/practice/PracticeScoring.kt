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
 * Timing windows, points and stars of a practice attempt.
 *
 * The windows are approved (decision 86); the point formula and the 80% pass bar
 * are deliberately a draft -- the scoring and star system is designed separately,
 * so everything tunable lives here and nowhere else.
 */
object PracticeScoring {
    const val PERFECT_MS = 25f
    const val GOOD_MS = 60f
    const val OK_MS = 120f

    /**
     * Extra time a note waits before it is written off as a miss. The poll loop and
     * the audio buffers report a hit up to a buffer late, and without this grace the
     * note expired first and the stroke was counted as an extra (decision 98).
     */
    const val EXPIRE_GRACE_MS = 60f

    /** Draft pass bar. */
    const val PASS_ACCURACY = 0.80f

    const val COUNT_IN_BEATS = 4

    /** Deviation scale and result histogram share one range. */
    const val SCALE_MS = OK_MS
    const val HISTOGRAM_BINS = 30
    const val RECENT_OFFSETS = 24

    private const val EXTRA_PENALTY = 20
    private const val COMBO_STEP = 50f
    private const val COMBO_MAX = 2f

    fun window(offsetMs: Float): HitWindow {
        val magnitude = abs(offsetMs)
        return when {
            magnitude <= PERFECT_MS -> HitWindow.Perfect
            magnitude <= GOOD_MS -> HitWindow.Good
            magnitude <= OK_MS -> HitWindow.Ok
            else -> HitWindow.Miss
        }
    }

    /** Accuracy weight of a window. A miss is worth nothing. */
    fun weight(window: HitWindow): Float = when (window) {
        HitWindow.Perfect -> 1f
        HitWindow.Good -> 0.8f
        HitWindow.Ok -> 0.5f
        HitWindow.Miss -> 0f
    }

    fun points(window: HitWindow): Int = when (window) {
        HitWindow.Perfect -> 100
        HitWindow.Good -> 70
        HitWindow.Ok -> 40
        HitWindow.Miss -> 0
    }

    fun extraHitPenalty(): Int = EXTRA_PENALTY

    /** Combo tops out at double points so a long streak cannot carry a sloppy run. */
    fun comboMultiplier(combo: Int): Float =
        (1f + combo / COMBO_STEP).coerceAtMost(COMBO_MAX)

    fun stars(accuracy: Float): Int = when {
        accuracy >= 0.95f -> 3
        accuracy >= 0.85f -> 2
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
    val score: Int,
    val maxCombo: Int,
    val accuracy: Float,
    val meanOffsetMs: Float,
    val offsets: List<Float>,
) {
    val hits: Int get() = perfect + good + ok
    val passed: Boolean get() = accuracy >= PracticeScoring.PASS_ACCURACY
    val stars: Int get() = PracticeScoring.stars(accuracy)

    companion object {
        val Empty = PracticeResult(
            noteCount = 0,
            perfect = 0,
            good = 0,
            ok = 0,
            misses = 0,
            extras = 0,
            score = 0,
            maxCombo = 0,
            accuracy = 0f,
            meanOffsetMs = 0f,
            offsets = emptyList(),
        )
    }
}
