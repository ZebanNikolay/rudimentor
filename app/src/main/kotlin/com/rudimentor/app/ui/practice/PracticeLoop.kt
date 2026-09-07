package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PracticeRank
import kotlin.math.abs
import kotlin.math.floor

/** One authored sequence, repeated on the engine's uninterrupted clock. No score or misses. */
class PracticeLoop(level: Level, rank: PracticeRank, bpm: Int) {
    private val beatBpms = attemptBeatBpms(level, rank, bpm)
    private val template = buildPracticeNotes(level, rank, bpm)
    private val beatStarts: DoubleArray
    private val noteTimes: DoubleArray
    private val countInBeats: Int
    val countInMs: Double
    val cycleDurationMs: Double
    val windows: AttemptWindows

    private data class Played(val keepUntilMs: Double, val judgement: NoteJudgement)

    private val played = LinkedHashMap<LoopNoteId, Played>()
    private val extras = ArrayDeque<Double>()
    private var recentOffsets: List<Float> = emptyList()
    private var lastHitMs = Double.NEGATIVE_INFINITY
    private var advancedMs = Double.NEGATIVE_INFINITY
    private val eligibleFromMs: Double

    val offsets: List<Float> get() = recentOffsets
    var hitCount: Long = 0
        private set

    init {
        require(beatBpms.isNotEmpty() && beatBpms.all { it > 0 } && template.isNotEmpty()) {
            "A practice loop needs a playable musical sequence"
        }
        countInBeats = PracticeScoring.countInBeats(beatBpms.first())
        countInMs = countInBeats * (60_000.0 / beatBpms.first())
        beatStarts = DoubleArray(beatBpms.size + 1)
        beatBpms.indices.forEach { beat ->
            beatStarts[beat + 1] = beatStarts[beat] + 60_000.0 / beatBpms[beat]
        }
        cycleDurationMs = beatStarts.last()
        // Reuse the finite builder's note identities and styling, but never its Float clock.
        noteTimes = buildLoopNoteTimes(level, rank, bpm, beatStarts, beatBpms, template.size)
        windows = AttemptWindows.forIntervals(noteTimes.indices.map { index ->
            val before = if (index > 0) noteTimes[index - 1] else noteTimes.last() - cycleDurationMs
            val after = if (index < noteTimes.lastIndex) noteTimes[index + 1] else noteTimes.first() + cycleDurationMs
            minOf(noteTimes[index] - before, after - noteTimes[index]).toFloat()
        })
        eligibleFromMs = minOf(countInMs, countInMs + noteTimes.first() - windows.forNote(0).okMs)
            .coerceAtLeast(0.0)
    }

    /** Pre-count-in and stale events are ignored; unmatched strokes are dots, not penalties. */
    fun registerHit(atMs: Double): HitOutcome? {
        if (!atMs.isFinite() || atMs < eligibleFromMs || atMs < advancedMs - HISTORY_MS) return null
        val gap = atMs - lastHitMs
        if (gap < PracticeScoring.DEBOUNCE_MS) return HitOutcome.Debounced(gap.toFloat())
        lastHitMs = atMs
        advance(atMs)
        hitCount += 1
        val id = nearestOpenNote(atMs)
        if (id == null) {
            extras.addLast(atMs)
            while (extras.size > MAX_RECENT_HITS) extras.removeFirst()
            return HitOutcome.Extra(atMs.toFloat())
        }
        val dueMs = noteTimeMs(id)
        val offset = (atMs - dueMs).toFloat()
        val judgement = NoteJudgement(offset, windows.forNote(id.localIndex).window(offset))
        played[id] = Played(
            keepUntilMs = maxOf(atMs + HISTORY_MS, dueMs + windows.forNote(id.localIndex).okMs),
            judgement = judgement,
        )
        while (played.size > MAX_RECENT_HITS) played.remove(played.keys.first())
        // A new bounded snapshot also invalidates the deviation scale under Compose skipping.
        recentOffsets = (recentOffsets + offset).takeLast(PracticeScoring.RECENT_OFFSETS)
        return HitOutcome.Judged(id.localIndex, judgement, rawOffsetMs = offset)
    }

    /** Pruning never walks silent notes or skipped cycles. Early hits survive their seam. */
    fun advance(positionMs: Double) {
        if (!positionMs.isFinite() || positionMs < advancedMs) return
        advancedMs = positionMs
        val iterator = played.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.keepUntilMs < positionMs) iterator.remove()
        }
        while (extras.isNotEmpty() && extras.first() < positionMs - HISTORY_MS) extras.removeFirst()
    }

    private fun nearestOpenNote(atMs: Double): LoopNoteId? {
        val cycle = cycleAt(atMs)
        val localMs = atMs - cycleStartMs(cycle)
        val next = noteTimes.lowerBound(localMs, template.size)
        val before = when {
            next > 0 -> LoopNoteId(cycle, next - 1)
            cycle > 0 -> LoopNoteId(cycle - 1, template.lastIndex)
            else -> null
        }
        val after = if (next < template.size) LoopNoteId(cycle, next) else LoopNoteId(cycle + 1, 0)
        var best: LoopNoteId? = null
        var distance = Double.POSITIVE_INFINITY
        for (id in listOfNotNull(before, after)) {
            val delta = abs(atMs - noteTimeMs(id))
            if (id !in played && delta <= windows.forNote(id.localIndex).okMs && delta < distance) {
                best = id
                distance = delta
            }
        }
        return best
    }

    private fun cycleAt(atMs: Double): Long =
        floor((atMs - countInMs) / cycleDurationMs).toLong().coerceAtLeast(0L)

    private fun cycleStartMs(cycle: Long): Double = countInMs + cycle * cycleDurationMs

    internal fun noteTimeMs(id: LoopNoteId): Double = cycleStartMs(id.cycle) + noteTimes[id.localIndex]

    internal fun judgementAt(id: LoopNoteId): NoteJudgement? = played[id]?.judgement

    internal val retainedHitCount: Int get() = played.size + extras.size

    /** Only visible events; Float times are relative to this frame, never to engine start. */
    internal fun renderView(
        positionMs: Double,
        beforeMs: Double,
        afterMs: Double,
    ): LoopTrackView {
        if (!positionMs.isFinite()) return LoopTrackView()
        val fromMs = positionMs - beforeMs.coerceIn(0.0, HISTORY_MS)
        val toMs = positionMs + afterMs.coerceIn(0.0, HISTORY_MS)
        val notes = ArrayList<LoopTrackNote>()
        val beats = ArrayList<LoopTrackBeat>()
        val countInTimes = if (fromMs <= countInMs) {
            FloatArray(countInBeats) { (it * (60_000.0 / beatBpms.first()) - positionMs).toFloat() }
        } else {
            FloatArray(0)
        }
        for (beat in countInTimes.indices) {
            val timeMs = beat * (60_000.0 / beatBpms.first())
            if (timeMs in fromMs..toMs) beats.add(LoopTrackBeat(countInTimes[beat], beat.toLong(), true))
        }
        var cycle = cycleAt(fromMs)
        while (cycleStartMs(cycle) <= toMs) {
            val startMs = cycleStartMs(cycle)
            val fromLocal = fromMs - startMs
            val toLocal = toMs - startMs
            var index = noteTimes.lowerBound(fromLocal, template.size)
            while (index < template.size && noteTimes[index] <= toLocal) {
                val id = LoopNoteId(cycle, index)
                notes.add(
                    LoopTrackNote(
                        id,
                        template[index].copy(timeMs = (startMs - positionMs + noteTimes[index]).toFloat()),
                        judgementAt(id),
                    ),
                )
                index += 1
            }
            var beat = beatStarts.lowerBound(fromLocal, beatBpms.size)
            while (beat < beatBpms.size && beatStarts[beat] <= toLocal) {
                beats.add(
                    LoopTrackBeat(
                        timeMs = (startMs - positionMs + beatStarts[beat]).toFloat(),
                        index = cycle * beatBpms.size + beat,
                    ),
                )
                beat += 1
            }
            cycle += 1
        }
        return LoopTrackView(
            notes = notes,
            beats = beats,
            countInTimesMs = countInTimes,
            extrasMs = extras.filter { it in fromMs..toMs }.map { (it - positionMs).toFloat() },
        )
    }

    internal companion object {
        const val HISTORY_MS = 8_000.0
        const val MAX_RECENT_HITS = 512
    }
}

internal data class LoopNoteId(val cycle: Long, val localIndex: Int)

internal data class LoopTrackNote(
    val id: LoopNoteId,
    val note: PracticeNote,
    val judgement: NoteJudgement?,
)

internal data class LoopTrackBeat(val timeMs: Float, val index: Long, val countIn: Boolean = false) {
    val strong: Boolean get() = index % PracticeScoring.COUNT_IN_BAR == 0L
}

internal data class LoopTrackView(
    val notes: List<LoopTrackNote> = emptyList(),
    val beats: List<LoopTrackBeat> = emptyList(),
    val countInTimesMs: FloatArray = FloatArray(0),
    val extrasMs: List<Float> = emptyList(),
)

/** Retiming follows the same authored slots, including rests and per-phase sticking resets. */
private fun buildLoopNoteTimes(
    level: Level,
    rank: PracticeRank,
    bpm: Int,
    beatStarts: DoubleArray,
    beatBpms: IntArray,
    noteCount: Int,
): DoubleArray {
    val target = requireNotNull(practiceTarget(level, rank))
    val times = DoubleArray(noteCount)
    var beat = 0
    var note = 0
    repeat(level.phaseRepeats * target.attemptRepeats) {
        level.attemptPhases(target, bpm).forEach { phase ->
            if (phase.steps.isEmpty()) return@forEach
            var step = 0
            repeat(phase.beatCount) {
                val density = target.hitsPerBeatAtBeat(beat)
                for (hit in 0 until density) {
                    if (!phase.steps[step % phase.steps.size].rest) {
                        times[note++] = beatStarts[beat] + hit * (60_000.0 / beatBpms[beat] / density)
                    }
                    step += 1
                }
                beat += 1
            }
        }
    }
    check(note == noteCount)
    return times
}

private fun DoubleArray.lowerBound(value: Double, end: Int): Int {
    var low = 0
    var high = end
    while (low < high) {
        val mid = low + (high - low) / 2
        if (this[mid] < value) low = mid + 1 else high = mid
    }
    return low
}
