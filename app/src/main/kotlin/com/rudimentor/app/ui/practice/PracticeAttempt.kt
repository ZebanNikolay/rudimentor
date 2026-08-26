package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.RankTarget
import com.rudimentor.app.audio.MicLab
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
    /** True on the first note played at a new density, so the track can mark that switch too. */
    val densityStart: Boolean = false,
)

/**
 * Builds the note list of one attempt.
 *
 * The attempt walks the blocks of the level ([Level.phases]) and plays that chain
 * `phaseRepeats * attemptRepeats` times: a one-pattern level is a single block repeated once,
 * a transition level is a chain of sticking blocks, and a tempo ramp plays its pass several
 * times per attempt (decision 141). Every block starts its sticking from the top — the
 * package authors each phase to fit its own beats, so carrying a cycle across a switch would
 * shift the hands of the next block.
 *
 * The beat is the unit of the walk, not the note: a subdivision switch plays the same beat
 * grid at a density that changes between blocks, so the pulse survives the switch and only
 * the number of notes on a beat changes ([RankTarget.hitsPerBeatAtBeat], decision 98). The
 * sticking runs through such a switch without restarting — a level whose block ends inside
 * its sticking cycle declares `midCycleSwitch`, which only means anything if the cycle
 * carries on.
 *
 * The tempo is per beat too: a tempo ramp changes it between phases ([attemptBeatBpms],
 * decision 148), so beat starts are accumulated instead of multiplied out of one beat
 * length. The count-in beats come before note 0, so `timeMs` is already the time on the
 * shared clock the audio engine reports.
 */
fun buildPracticeNotes(level: Level, rank: PracticeRank, bpm: Int): List<PracticeNote> {
    val target = practiceTarget(level, rank) ?: return emptyList()
    if (bpm <= 0) return emptyList()
    val beatBpms = attemptBeatBpms(level, rank, bpm)
    val beatTimes = buildBeatTimesMs(level, rank, bpm)
    val notes = ArrayList<PracticeNote>()
    // Beats since the start of the attempt: it places the note in time and it is what the
    // subdivision plan is read with, so both stay in step across phases and repeats.
    var beat = 0
    var previousHitsPerBeat = 0
    repeat(level.phaseRepeats * target.attemptRepeats) {
        level.phases.forEach { phase ->
            val steps = phase.steps
            if (steps.isEmpty()) return@forEach
            var position = 0
            repeat(phase.beatCount) { beatInPhase ->
                val hitsPerBeat = target.hitsPerBeatAtBeat(beat)
                val beatMs = 60_000f / (beatBpms.getOrNull(beat) ?: bpm)
                val beatStartMs = beatTimes.getOrNull(PracticeScoring.COUNT_IN_BEATS + beat)
                    ?: return notes
                val noteMs = beatMs / hitsPerBeat
                for (hit in 0 until hitsPerBeat) {
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
                            timeMs = beatStartMs + hit * noteMs,
                            phaseIndex = phase.index,
                            phaseStart = level.phased && beatInPhase == 0 && hit == 0,
                            densityStart = hit == 0 &&
                                previousHitsPerBeat != 0 &&
                                hitsPerBeat != previousHitsPerBeat,
                        ),
                    )
                    position += 1
                }
                previousHitsPerBeat = hitsPerBeat
                beat += 1
            }
        }
    }
    return notes
}

/**
 * Tempo of every beat of one attempt, in beat order.
 *
 * A level without a tempo ramp plays the whole attempt at [bpm]. A ramp holds authored
 * tempos, and the player can still pick a tempo, so the authored shape is scaled by the
 * ratio between [bpm] and the entry tempo of the rank: picking 110 on a 95 → 105 ramp
 * keeps the same climb, moved up. Every beat is clamped to the tempo range the engine
 * accepts, which also stops a fast rank from ramping past it.
 */
fun attemptBeatBpms(level: Level, rank: PracticeRank, bpm: Int): IntArray {
    val target = practiceTarget(level, rank) ?: return IntArray(0)
    val beats = level.beatsPerAttempt(target)
    if (beats <= 0 || bpm <= 0) return IntArray(0)
    val plan = target.tempoRampPlan
    if (plan == null || target.bpm <= 0) return IntArray(beats) { bpm }
    val scale = bpm.toFloat() / target.bpm.toFloat()
    return IntArray(beats) { beat ->
        Math.round(target.bpmAtBeat(beat) * scale).coerceIn(MicLab.MIN_BPM, MicLab.MAX_BPM)
    }
}

/**
 * Start time of every beat the attempt plays, count-in beats first: the grid the track
 * draws its beat bars and its count-in digits on, and what places the notes in time. Under
 * a tempo ramp the beats have different lengths, so the grid cannot be one multiple
 * (decision 148).
 */
fun buildBeatTimesMs(level: Level, rank: PracticeRank, bpm: Int): FloatArray {
    val beatBpms = attemptBeatBpms(level, rank, bpm)
    if (beatBpms.isEmpty()) return FloatArray(0)
    val countIn = PracticeScoring.COUNT_IN_BEATS
    val times = FloatArray(countIn + beatBpms.size)
    var timeMs = 0f
    val entryBeatMs = 60_000f / beatBpms.first()
    for (index in 0 until countIn) {
        times[index] = timeMs
        timeMs += entryBeatMs
    }
    beatBpms.forEachIndexed { index, beatBpm ->
        times[countIn + index] = timeMs
        timeMs += 60_000f / beatBpm
    }
    return times
}

/**
 * The tempo plan the audio engine is started with: the count-in beats at the entry tempo
 * of the attempt, then every beat of the attempt. Empty for a level that plays at one
 * tempo, which the engine reads as "keep the fixed tempo".
 */
fun buildTempoPlan(level: Level, rank: PracticeRank, bpm: Int): IntArray {
    val target = practiceTarget(level, rank) ?: return IntArray(0)
    if (target.tempoRampPlan == null) return IntArray(0)
    val beats = attemptBeatBpms(level, rank, bpm)
    if (beats.isEmpty()) return IntArray(0)
    val countIn = IntArray(PracticeScoring.COUNT_IN_BEATS) { beats.first() }
    return countIn + beats
}

/**
 * Windows of an attempt, one set per note, derived from the spacing around that note.
 *
 * Called once next to [buildPracticeNotes]. Judging every note by the shortest gap of the
 * whole attempt punished the sparse beats of a mixed level (decision 151), so each note is
 * graded at its own density instead.
 */
fun attemptWindowsFor(notes: List<PracticeNote>): AttemptWindows =
    AttemptWindows.forIntervals(noteIntervalsMs(notes))

/**
 * The spacing each note is judged at: the shorter of the gaps to its neighbours.
 *
 * The tighter of the two neighbours is what decides the window, not the gap that follows:
 * a window wider than half the distance to the nearer note would overlap it and a hit
 * could no longer be attributed to one note. On the beat where a density changes this
 * makes the note as strict as the denser side of the switch, which is the side that can
 * actually steal its hits.
 */
fun noteIntervalsMs(notes: List<PracticeNote>): List<Float> {
    if (notes.size < 2) {
        return List(notes.size) { PracticeScoring.DEFAULT_INTERVAL_MS }
    }
    return notes.indices.map { index ->
        val before = if (index > 0) notes[index].timeMs - notes[index - 1].timeMs else 0f
        val after = if (index < notes.size - 1) notes[index + 1].timeMs - notes[index].timeMs else 0f
        val nearest = listOf(before, after).filter { it > 0f }.minOrNull()
        nearest ?: PracticeScoring.DEFAULT_INTERVAL_MS
    }
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
    val windows: AttemptWindows = AttemptWindows.uniform(HitWindows.Default),
) {
    /** A level of one density: every note judged by the same windows. */
    constructor(notes: List<PracticeNote>, windows: HitWindows) :
        this(notes, AttemptWindows.uniform(windows))

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
        val window = windows.forNote(note.index).window(offset)
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
            //
            // Each note expires by its own window (decision 151), and stopping at the first
            // one still open is still correct: a window never reaches past the next note
            // (`OK_INTERVAL_SHARE`), so the notes expire in the order they are played.
            if (positionMs <= note.timeMs + windows.forNote(index).okMs +
                PracticeScoring.EXPIRE_GRACE_MS
            ) {
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
            // The scale of the result screen has to hold every offset of the attempt, so it
            // is drawn with the widest windows the attempt had (decision 151).
            windows = windows.widest,
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
        var bestDistance = Float.MAX_VALUE
        var index = cursor
        while (index < notes.size) {
            val note = notes[index]
            val distance = abs(positionMs - note.timeMs)
            // The widest window bounds the scan; whether a note is in reach is then decided
            // by its own window (decision 151).
            if (note.timeMs - positionMs > windows.widest.okMs) break
            if (judgementsInternal[index] == null &&
                distance <= windows.forNote(index).okMs &&
                distance < bestDistance
            ) {
                best = note
                bestDistance = distance
            }
            index += 1
        }
        return best
    }
}
