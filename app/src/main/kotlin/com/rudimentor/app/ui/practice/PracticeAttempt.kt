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
    /** Which block of a phase level the note belongs to; always 0 on a one-pattern level. */
    val phaseIndex: Int = 0,
    /** True on the first note of a block of a phase level, so the track can mark the switch. */
    val phaseStart: Boolean = false,
)

/**
 * Builds the note list of one attempt.
 *
 * The attempt walks the blocks of the level ([Level.phases]) and plays that chain
 * `phaseRepeats * attemptRepeats` times: a one-pattern level is a single block repeated once,
 * a transition level is a chain of sticking blocks, and a tempo ramp plays its pass several
 * times per attempt (decision 141). Inside a block the pattern repeats until the block has
 * `beatCount * hitsPerBeat` notes, and every block starts its sticking from the top — the
 * package authors each phase to fit its own beats, so carrying a cycle across a switch would
 * shift the hands of the next block.
 *
 * The notes sit on one even grid: the count-in beats come before note 0, so `timeMs` is
 * already the time on the shared clock the audio engine reports.
 */
fun buildPracticeNotes(level: Level, rank: PracticeRank, bpm: Int): List<PracticeNote> {
    val target = practiceTarget(level, rank) ?: return emptyList()
    if (bpm <= 0) return emptyList()
    val beatMs = 60_000f / bpm
    val noteMs = beatMs / target.hitsPerBeat
    val countInMs = PracticeScoring.COUNT_IN_BEATS * beatMs
    val notes = ArrayList<PracticeNote>()
    repeat(level.phaseRepeats * target.attemptRepeats) {
        level.phases.forEach { phase ->
            val steps = phase.steps
            if (steps.isEmpty()) return@forEach
            val count = phase.beatCount * target.hitsPerBeat
            for (position in 0 until count) {
                val step = steps[position % steps.size]
                notes.add(
                    PracticeNote(
                        index = notes.size,
                        // Multi-hand steps (unison) are drawn as the lead hand: one pad, one hit.
                        hand = if (step.hands.contains(PatternHand.Right)) {
                            PatternHand.Right
                        } else {
                            PatternHand.Left
                        },
                        timeMs = countInMs + notes.size * noteMs,
                        phaseIndex = phase.index,
                        phaseStart = level.phased && position == 0,
                    ),
                )
            }
        }
    }
    return notes
}

/**
 * Windows of an attempt, derived from the shortest gap between its notes.
 *
 * Called once next to [buildPracticeNotes]: the whole attempt is judged against one set
 * of windows, so the grading does not shift inside a track.
 */
fun hitWindowsFor(notes: List<PracticeNote>): HitWindows {
    val minInterval = minNoteIntervalMs(notes) ?: return HitWindows.Default
    return HitWindows.forMinInterval(minInterval)
}

/**
 * Shortest positive gap between two consecutive notes, or null when the list is too
 * short to have one. Shared by [hitWindowsFor] and the practice log, which records
 * the interval the windows were derived from.
 */
fun minNoteIntervalMs(notes: List<PracticeNote>): Float? {
    if (notes.size < 2) return null
    var minInterval = Float.MAX_VALUE
    for (index in 1 until notes.size) {
        val gap = notes[index].timeMs - notes[index - 1].timeMs
        if (gap > 0f && gap < minInterval) minInterval = gap
    }
    return if (minInterval == Float.MAX_VALUE) null else minInterval
}

/** What became of one detected stick hit. */
sealed interface HitOutcome {
    /** The hit was matched to a note and graded. */
    data class Judged(val noteIndex: Int, val judgement: NoteJudgement) : HitOutcome

    /** The hit landed too far from any open note and was charged as an extra. */
    data class Extra(val positionMs: Float) : HitOutcome

    /**
     * The hit arrived [gapMs] after the previous one and was dropped as detector
     * ringing, so it neither scored nor counted against the attempt.
     */
    data class Debounced(val gapMs: Float) : HitOutcome
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
     * grows the denominator instead of subtracting a penalty (decision 132).
     */
    val liveAccuracy: Float
        get() = PracticeScoring.accuracy(
            weightedNotes = weightedSum,
            noteCount = judgedCount,
            extras = extrasInternal.size,
        )

    val judgements: List<NoteJudgement?> get() = judgementsInternal.asList()
    val extras: List<Float> get() = extrasInternal
    val offsets: List<Float> get() = offsetsInternal

    fun judgementAt(index: Int): NoteJudgement? =
        if (index in judgementsInternal.indices) judgementsInternal[index] else null

    /** Hits dropped by the debounce filter, for diagnostics only. */
    var debounced: Int = 0
        private set

    /**
     * Registers a stick hit at [positionMs] and reports what became of it, so the
     * caller can log the hits the score itself never sees.
     */
    fun registerHit(positionMs: Float): HitOutcome {
        // A second trigger inside the debounce window is the onset detector ringing,
        // not a stroke: dropped before it can be judged or charged as an extra.
        val gap = positionMs - lastHitMs
        if (gap < PracticeScoring.DEBOUNCE_MS) {
            debounced += 1
            return HitOutcome.Debounced(gapMs = gap)
        }
        lastHitMs = positionMs
        val note = nearestOpenNote(positionMs)
        if (note == null) {
            extrasInternal.add(positionMs)
            combo = 0
            return HitOutcome.Extra(positionMs = positionMs)
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
        return HitOutcome.Judged(noteIndex = note.index, judgement = judgement)
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
        val accuracy = PracticeScoring.accuracy(
            weightedNotes = weighted,
            noteCount = noteCount,
            extras = extrasInternal.size,
        )
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
            windows = windows,
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
