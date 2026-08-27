package com.rudimentor.app.ui.practice

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
 * The three timing windows of one note, in milliseconds.
 *
 * The windows follow the tempo of the level instead of standing still: a human floats
 * more when the notes are far apart, so the window has to be wider there. They are
 * derived from the spacing around the note, computed once when the note list is built
 * and never per hit -- the windows must not breathe inside a track.
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
        /** Fallback windows: quarter notes at 120 BPM, used when a level has no notes yet. */
        val Default = forMinInterval(PracticeScoring.DEFAULT_INTERVAL_MS)

        /**
         * Windows for a level whose shortest gap between notes is [minIntervalMs].
         *
         * `sigma` is the timing spread a human is expected to have at that spacing; the
         * windows are multiples of it, widest first, each one clamped to the next so the
         * order PERFECT <= GOOD <= OK always holds. The OK window is additionally kept
         * under [OK_INTERVAL_SHARE] of the interval, otherwise the windows of neighbouring
         * notes would overlap and a hit could no longer be attributed to one note.
         */
        fun forMinInterval(minIntervalMs: Float): HitWindows {
            if (minIntervalMs <= 0f) return Default
            val sigma = PracticeScoring.sigmaMs(minIntervalMs)
            val ok = minOf(
                PracticeScoring.OK_SIGMAS * sigma,
                PracticeScoring.OK_INTERVAL_SHARE * minIntervalMs,
            )
            val good = minOf(PracticeScoring.GOOD_SIGMAS * sigma, ok)
            val perfect = minOf(PracticeScoring.PERFECT_SIGMAS * sigma, good)
            return HitWindows(perfectMs = perfect, goodMs = good, okMs = ok)
        }
    }
}

/**
 * The windows of every note of one attempt.
 *
 * A level of one density has one set of windows, but a subdivision switch or a tempo ramp
 * changes the spacing inside the attempt, and a single set derived from the shortest gap
 * would judge the sparse beats by the standard of the densest block: `SS-01` lost ~17
 * accuracy points that way (decision 151). So every note carries the windows of *its own*
 * spacing, computed once here and never per hit.
 *
 * [widest] and [tightest] are the two ends of the range, for the readouts that need one
 * set of numbers: the deviation scale draws the widest so every offset of the attempt fits
 * inside it, while the telemetry header records the tightest next to the shortest interval
 * it belongs to.
 */
class AttemptWindows private constructor(private val perNote: List<HitWindows>) {
    val widest: HitWindows = perNote.maxByOrNull { it.okMs } ?: HitWindows.Default
    val tightest: HitWindows = perNote.minByOrNull { it.okMs } ?: HitWindows.Default

    /** Windows of the note at [index]; the widest for an index outside the attempt. */
    fun forNote(index: Int): HitWindows = perNote.getOrElse(index) { widest }

    companion object {
        /** One set of windows for every note, as a level of a single density has. */
        fun uniform(windows: HitWindows): AttemptWindows = AttemptWindows(listOf(windows))

        /** Windows built per note from the local spacing [intervalsMs] of each one. */
        fun forIntervals(intervalsMs: List<Float>): AttemptWindows =
            if (intervalsMs.isEmpty()) {
                uniform(HitWindows.Default)
            } else {
                AttemptWindows(intervalsMs.map { HitWindows.forMinInterval(it) })
            }
    }
}

/**
 * Timing windows, accuracy and stars of a practice attempt.
 *
 * One number carries the whole result: accuracy is the weighted share of notes played,
 * and an extra hit grows the denominator instead of subtracting a penalty, so the result
 * cannot leave 0…100 % and the cost of a mistake scales with the length of the level.
 * There are no points, no combo multiplier and no letter ranks (decision 132,
 * `docs/scoring-spec.md`).
 */
object PracticeScoring {
    /**
     * Expected human timing spread at a note spacing of [intervalMs]: it grows with the
     * interval, but never drops under a floor set by the audio path itself -- the onset
     * detector and the audio buffers add 10-15 ms of noise of their own, and a window
     * narrower than that would grade the phone instead of the player.
     */
    fun sigmaMs(intervalMs: Float): Float =
        sqrt((SIGMA_SLOPE * intervalMs) * (SIGMA_SLOPE * intervalMs) + SIGMA_FLOOR_MS * SIGMA_FLOOR_MS)

    /** Share of the interval that ends up as spread: professional asynchrony, ~1.4 %. */
    const val SIGMA_SLOPE = 0.014f
    const val SIGMA_FLOOR_MS = 10f

    /** Width of each window in sigmas. Roughly doubling, as in the rhythm games. */
    const val PERFECT_SIGMAS = 1.8f
    const val GOOD_SIGMAS = 3.5f
    const val OK_SIGMAS = 7.0f

    /** The OK window never eats more than this share of the gap between two notes. */
    const val OK_INTERVAL_SHARE = 0.45f

    /** Quarter notes at 120 BPM: the spacing the fallback windows are built for. */
    const val DEFAULT_INTERVAL_MS = 500f

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
    const val PASS_ACCURACY = 0.60f
    const val TWO_STAR_ACCURACY = 0.75f
    const val THREE_STAR_ACCURACY = 0.88f

    /** Share of notes that has to land in PERFECT for the crown. */
    const val CROWN_PERFECT_SHARE = 0.80f

    /** Extra hits the crown tolerates, as a share of the note count. */
    const val CROWN_EXTRA_SHARE = 0.01f

    /**
     * Extra hits charged to accuracy, as a share of the note count. The cap is a
     * technical fuse, not a handout: hits come in through the microphone, and before the
     * detector is calibrated a stick rattle can read as a stroke. A player who actually
     * plays clean never reaches it (decision 132).
     */
    const val EXTRA_CAP_SHARE = 0.02f

    const val COUNT_IN_BEATS = 4

    /**
     * Deviation scale and result histogram share one fixed range: the plots always
     * read from -120 to +120 ms, whatever the windows of the current level are.
     */
    const val SCALE_MS = 120f
    const val HISTOGRAM_BINS = 30
    const val RECENT_OFFSETS = 24

    /** Accuracy weight of a window. A miss is worth nothing. */
    fun weight(window: HitWindow): Float = when (window) {
        HitWindow.Perfect -> 1f
        HitWindow.Good -> 0.7f
        HitWindow.Ok -> 0.35f
        HitWindow.Miss -> 0f
    }

    /** Extra hits as they enter the accuracy denominator, capped by [EXTRA_CAP_SHARE]. */
    fun chargedExtras(extras: Int, noteCount: Int): Float =
        minOf(extras.toFloat(), EXTRA_CAP_SHARE * noteCount)

    /** Accuracy of an attempt: the weighted notes over the notes plus the charged extras. */
    fun accuracy(weightedNotes: Float, noteCount: Int, extras: Int): Float {
        val denominator = noteCount + chargedExtras(extras, noteCount)
        return if (denominator <= 0f) 0f else (weightedNotes / denominator).coerceIn(0f, 1f)
    }

    /**
     * Stars of an attempt. Four states in all (decision 126): under the pass bar
     * nothing, then one star for the pass, two for the middle bar, and the third only
     * for a run without a single miss -- the third star *is* FULL COMBO. Extra hits are
     * not asked about here: an absolute zero of them on a long level is a lottery on the
     * microphone, so they act through the accuracy number alone.
     */
    fun stars(accuracy: Float, misses: Int): Int = when {
        accuracy < PASS_ACCURACY -> 0
        accuracy >= THREE_STAR_ACCURACY && misses == 0 -> 3
        accuracy >= TWO_STAR_ACCURACY -> 2
        else -> 1
    }

    /**
     * The crown: a separate quality of the run rather than "a bit more accuracy". Three
     * stars, most of the notes dead on, and all but a rounding error of extra hits gone.
     */
    fun crown(stars: Int, perfect: Int, noteCount: Int, extras: Int): Boolean =
        stars == 3 &&
            noteCount > 0 &&
            perfect >= CROWN_PERFECT_SHARE * noteCount &&
            extras <= CROWN_EXTRA_SHARE * noteCount

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
    /**
     * How late the audio path reported the strokes of this run, after the attempt measured it
     * on itself ([LatencyTracker]). This is what the stored latency of the output profile is
     * retuned by once the attempt ends, so the next run starts where this one finished
     * (decision 167).
     */
    val latencyBiasMs: Float = 0f,
    /** Windows the attempt was judged against, so the plots can colour by them. */
    val windows: HitWindows = HitWindows.Default,
) {
    val hits: Int get() = perfect + good + ok
    val passed: Boolean get() = accuracy >= PracticeScoring.PASS_ACCURACY
    val stars: Int get() = PracticeScoring.stars(accuracy, misses)

    /** The third star and FULL COMBO are one and the same state (decision 126). */
    val fullCombo: Boolean get() = stars == 3

    /** The crown on the node: three stars plus a run that was mostly dead on. */
    val crown: Boolean
        get() = PracticeScoring.crown(
            stars = stars,
            perfect = perfect,
            noteCount = noteCount,
            extras = extras,
        )

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
            latencyBiasMs = 0f,
        )
    }
}
