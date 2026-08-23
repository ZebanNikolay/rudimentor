package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.RankTarget
import kotlin.math.abs

/**
 * The rank target of a level, or null when the package does not define that rank.
 * `Level.target` throws, and an attempt must never crash on catalogue data.
 */
internal fun practiceTarget(level: Level, rank: PracticeRank): RankTarget? =
    level.rankTargets.firstOrNull { it.rank == rank } ?: level.rankTargets.firstOrNull()

/** One note of an attempt: which hand plays it and when it is due. */
data class PracticeNote(
    val index: Int,
    val hand: PatternHand,
    val timeMs: Float,
)

/**
 * Builds the note list of one attempt.
 *
 * The pattern repeats until `beatCount * attemptRepeats * hitsPerBeat` notes are laid out
 * (a tempo ramp plays its pass several times per attempt), and the
 * count-in beats sit before note 0, so `timeMs` is already the time on the shared
 * clock the audio engine reports.
 */
fun buildPracticeNotes(level: Level, rank: PracticeRank, bpm: Int): List<PracticeNote> {
    val target = practiceTarget(level, rank) ?: return emptyList()
    val steps = level.pattern
    if (steps.isEmpty() || bpm <= 0) return emptyList()
    val beatMs = 60_000f / bpm
    val noteMs = beatMs / target.hitsPerBeat
    val countInMs = PracticeScoring.COUNT_IN_BEATS * beatMs
    val total = level.beatCount * target.attemptRepeats * target.hitsPerBeat
    return List(total) { index ->
        val step = steps[index % steps.size]
        PracticeNote(
            index = index,
            // Multi-hand steps (unison) are drawn as the lead hand: one pad, one hit.
            hand = if (step.hands.contains(PatternHand.Right)) {
                PatternHand.Right
            } else {
                PatternHand.Left
            },
            timeMs = countInMs + index * noteMs,
        )
    }
}

/**
 * Windows of an attempt, derived from the shortest gap between its notes.
 *
 * Called once next to [buildPracticeNotes]: the clamp exists for the fast ranks, where
 * the interval between notes drops under twice the OK window (Stage at 180 BPM leaves
 * 83 ms), and is a no-op at ordinary tempos.
 */
fun hitWindowsFor(notes: List<PracticeNote>): HitWindows {
    if (notes.size < 2) return HitWindows.Default
    var minInterval = Float.MAX_VALUE
    for (index in 1 until notes.size) {
        val gap = notes[index].timeMs - notes[index - 1].timeMs
        if (gap > 0f && gap < minInterval) minInterval = gap
    }
    if (minInterval == Float.MAX_VALUE) return HitWindows.Default
    return HitWindows.forMinInterval(minInterval)
}

/**
 * The judged state of one attempt.
 *
 * Pure logic on purpose: the audio session only feeds it a position and stick
 * hits in milliseconds, so the whole scoring path is unit-testable without a
 * device.
 */
class PracticeAttempt(
    val notes: List<PracticeNote>,
    val windows: HitWindows = HitWindows.Default,
) {
    private val judgementsInternal = arrayOfNulls<NoteJudgement>(notes.size)

    /** Hits that matched no note, kept as positions in milliseconds. */
    private val extrasInternal = ArrayList<Float>()

    var combo: Int = 0
        private set
    var maxCombo: Int = 0
        private set
    var misses: Int = 0
        private set

    /** Index of the first note that has not been judged yet. */
    private var cursor: Int = 0

    /** The last judged note, so the HUD can show its verdict. */
    var lastJudged: NoteJudgement? = null
        private set

    /** Attempt time the last verdict appeared at, so the track can fade it out. */
    var lastJudgedAtMs: Float = Float.NEGATIVE_INFINITY
        private set

    /** Signed offsets in the order they were judged, for the scale and histogram. */
    private val offsetsInternal = ArrayList<Float>()

    private var weightedSum = 0f
    private var judgedCount = 0

    /** Position of the last stroke that was not dropped by the debounce filter. */
    private var lastHitMs = Float.NEGATIVE_INFINITY

    /**
     * Accuracy over what has happened so far, cheap enough to read every frame. Same
     * formula as the final number, just over the notes judged up to now: an extra hit
     * grows the denominator instead of subtracting a penalty (decision 125).
     */
    val liveAccuracy: Float
        get() {
            val denominator = judgedCount + extrasInternal.size
            return if (denominator == 0) 0f else (weightedSum / denominator).coerceIn(0f, 1f)
        }

    val judgements: List<NoteJudgement?> get() = judgementsInternal.asList()
    val extras: List<Float> get() = extrasInternal
    val offsets: List<Float> get() = offsetsInternal

    fun judgementAt(index: Int): NoteJudgement? =
        if (index in judgementsInternal.indices) judgementsInternal[index] else null

    /**
     * Registers a stick hit at [positionMs] and returns its judgement, or null when
     * the hit belongs to no note and is counted as an extra.
     */
    fun registerHit(positionMs: Float): NoteJudgement? {
        // A second trigger inside the debounce window is the onset detector ringing,
        // not a stroke: dropped before it can be judged or charged as an extra.
        if (positionMs - lastHitMs < PracticeScoring.DEBOUNCE_MS) return null
        lastHitMs = positionMs
        val note = nearestOpenNote(positionMs)
        if (note == null) {
            extrasInternal.add(positionMs)
            combo = 0
            return null
        }
        val offset = positionMs - note.timeMs
        val window = windows.window(offset)
        val judgement = NoteJudgement(offsetMs = offset, window = window)
        judgementsInternal[note.index] = judgement
        offsetsInternal.add(offset)
        weightedSum += PracticeScoring.weight(window)
        judgedCount += 1
        lastJudged = judgement
        lastJudgedAtMs = positionMs
        combo += 1
        maxCombo = maxOf(maxCombo, combo)
        advanceCursor()
        return judgement
    }

    /**
     * Marks every note whose window has fully passed as missed. Called from the poll
     * loop, so a miss appears the moment it can no longer be hit.
     *
     * Returns the indices that turned into a miss on this call, which is what starts
     * the falling animation.
     */
    fun expireMissedNotes(positionMs: Float): List<Int> {
        val missed = ArrayList<Int>()
        var index = cursor
        while (index < notes.size) {
            val note = notes[index]
            // The grace window keeps a hit that arrives in the next poll buffer from
            // losing its note to expiry: without it a late but valid stroke landed as
            // an extra while the note itself dropped as a miss (decision 101).
            if (positionMs <= note.timeMs + windows.okMs + PracticeScoring.EXPIRE_GRACE_MS) {
                break
            }
            if (judgementsInternal[index] == null) {
                judgementsInternal[index] =
                    NoteJudgement(offsetMs = Float.NaN, window = HitWindow.Miss)
                misses += 1
                judgedCount += 1
                combo = 0
                lastJudged = judgementsInternal[index]
                lastJudgedAtMs = positionMs
                missed.add(index)
            }
            index += 1
        }
        advanceCursor()
        return missed
    }

    fun result(): PracticeResult {
        var perfect = 0
        var good = 0
        var ok = 0
        var weighted = 0f
        judgementsInternal.forEach { judgement ->
            when (judgement?.window) {
                HitWindow.Perfect -> perfect += 1
                HitWindow.Good -> good += 1
                HitWindow.Ok -> ok += 1
                else -> Unit
            }
            weighted += PracticeScoring.weight(judgement?.window ?: HitWindow.Miss)
        }
        val noteCount = notes.size
        // Extra hits grow the denominator instead of subtracting points, so flamming
        // through a level cannot read as clean and the number stays inside 0…100 %.
        val denominator = noteCount + extrasInternal.size
        val accuracy = if (denominator == 0) {
            0f
        } else {
            (weighted / denominator).coerceIn(0f, 1f)
        }
        return PracticeResult(
            noteCount = noteCount,
            perfect = perfect,
            good = good,
            ok = ok,
            misses = noteCount - perfect - good - ok,
            extras = extrasInternal.size,
            maxCombo = maxCombo,
            accuracy = accuracy,
            meanOffsetMs = if (offsetsInternal.isEmpty()) {
                0f
            } else {
                offsetsInternal.average().toFloat()
            },
            offsets = offsetsInternal.toList(),
        )
    }

    private fun advanceCursor() {
        while (cursor < notes.size && judgementsInternal[cursor] != null) {
            cursor += 1
        }
    }

    /**
     * The nearest note inside the OK window that has not been judged yet. Scanning
     * forward from the cursor keeps a double stroke from stealing the same note twice.
     */
    private fun nearestOpenNote(positionMs: Float): PracticeNote? {
        var best: PracticeNote? = null
        var bestDistance = windows.okMs
        var index = cursor
        while (index < notes.size) {
            val note = notes[index]
            val distance = abs(positionMs - note.timeMs)
            if (note.timeMs - positionMs > windows.okMs) break
            if (judgementsInternal[index] == null && distance <= bestDistance) {
                best = note
                bestDistance = distance
            }
            index += 1
        }
        return best
    }
}
