package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.stats.PlayEvent
import com.rudimentor.app.stats.PlayStatsRecorder
import com.rudimentor.app.stats.StatsInstant
import com.rudimentor.app.stats.TakeEnd

/**
 * The play-stats side of one take (decision 257). Opened when the engine starts, closed once
 * by whichever way out of the take comes first -- finish, Stop, Back, backgrounding, Repeat,
 * a lost stream, the screen leaving -- and turned into a single ledger event. Everything it
 * reads is already in memory in the attempt or the loop; nothing is counted twice and nothing
 * touches the disk while the take runs.
 */
internal class TakeTally(
    private val levelId: String,
    private val rank: PracticeRank,
    private val attempt: PracticeAttempt,
    private val loop: PracticeLoop?,
    private val variantPlayedBefore: Boolean,
) {
    private var closed = false

    /**
     * Where the take stands on its own clock, kept by the poll loop: the render clock of a
     * Challenge attempt, the exact engine clock of a practice loop.
     */
    var positionMs: Double = 0.0

    /** The event for this take, or null if it was closed already. */
    fun close(
        end: TakeEnd,
        result: PracticeResult? = null,
        at: StatsInstant = StatsInstant.now(),
    ): PlayEvent? {
        if (closed) return null
        closed = true
        return if (loop != null) {
            practiceTakeEvent(at, levelId, loop, end, positionMs)
        } else {
            challengeTakeEvent(at, levelId, rank, attempt, end, positionMs, result, variantPlayedBefore)
        }
    }
}

/** The take a practice screen has open, if any. Remembered once per screen. */
internal class OpenTake {
    var tally: TakeTally? = null

    /** Closes the open take, if there is one, and hands it to the ledger. */
    fun close(end: TakeEnd, result: PracticeResult? = null) {
        val open = tally ?: return
        tally = null
        open.close(end, result)?.let(PlayStatsRecorder::record)
    }
}

internal fun challengeTakeEvent(
    at: StatsInstant,
    levelId: String,
    rank: PracticeRank,
    attempt: PracticeAttempt,
    end: TakeEnd,
    positionMs: Double,
    result: PracticeResult?,
    variantPlayedBefore: Boolean,
): PlayEvent.ChallengeTake {
    val scored = result ?: attempt.result()
    val completed = end == TakeEnd.Completed
    val firstNoteMs = attempt.notes.firstOrNull()?.timeMs?.toDouble() ?: 0.0
    val lastNoteMs = attempt.notes.lastOrNull()?.timeMs?.toDouble() ?: 0.0
    // First note to the end of the take; never longer than the take itself plus its tail,
    // whatever the clock did.
    val padMs = (positionMs - firstNoteMs)
        .coerceIn(0.0, (lastNoteMs - firstNoteMs).coerceAtLeast(0.0) + MAX_TAIL_MS)
        .toLong()
    return PlayEvent.ChallengeTake(
        at = at,
        levelId = levelId,
        rank = rank.storageName,
        end = end,
        noteCount = scored.noteCount,
        perfect = scored.perfect,
        good = scored.good,
        ok = scored.ok,
        // Only verdicts actually given: a stopped take has unplayed notes, not missed ones.
        misses = attempt.misses,
        extras = scored.extras,
        accuracy = scored.accuracy,
        passed = completed && scored.passed,
        stars = if (completed) scored.stars else 0,
        crown = completed && scored.crown,
        medianOffsetMs = median(scored.offsets),
        padMs = padMs,
        variantPlayedBefore = variantPlayedBefore,
    )
}

internal fun practiceTakeEvent(
    at: StatsInstant,
    levelId: String,
    loop: PracticeLoop,
    end: TakeEnd,
    positionMs: Double,
): PlayEvent.PracticeTake = PlayEvent.PracticeTake(
    at = at,
    levelId = levelId,
    // Stop is how a practice session ends; there is no "stopped early".
    end = if (end == TakeEnd.Abandoned) TakeEnd.Abandoned else TakeEnd.Completed,
    perfect = loop.perfectCount.toInt(),
    good = loop.goodCount.toInt(),
    ok = loop.okCount.toInt(),
    extras = loop.extraCount.toInt(),
    padMs = (positionMs - loop.countInMs).coerceAtLeast(0.0).toLong(),
    activeMs = loop.activeMs.toLong(),
)

internal fun median(values: List<Float>): Float? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
}

/** The longest the take may run past its last note: finish gap, hold and tail, rounded up. */
private const val MAX_TAIL_MS = 5_000.0
