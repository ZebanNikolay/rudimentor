package com.rudimentor.app.stats

import java.time.ZoneId

/**
 * When something happened, as the phone saw it at that moment: the wall clock and the time
 * zone it was in. The zone is captured with the event, so a take finished before a flight is
 * filed under the day it was played on even if it reaches the store after landing.
 */
data class StatsInstant(val wallMs: Long, val zone: ZoneId) {
    companion object {
        fun now(): StatsInstant = StatsInstant(System.currentTimeMillis(), ZoneId.systemDefault())
    }
}

/** How a take ended. */
enum class TakeEnd {
    /** Reached its own finish (Challenge) or was closed with Stop (Practice). */
    Completed,

    /** Challenge only: Stop after at least one judged note (decision 215). */
    Stopped,

    /** Back, backgrounding, Repeat, a lost audio stream, Stop before anything was judged. */
    Abandoned,
}

/**
 * Something the ledger counts. Built off the audio path once the thing is over, and applied
 * to the stored ledger as one write: nothing here is ever written while a take is running.
 */
sealed interface PlayEvent {
    val at: StatsInstant

    /** One Challenge take, whatever way it ended. */
    data class ChallengeTake(
        override val at: StatsInstant,
        val levelId: String,
        /** `PracticeRank.storageName`. */
        val rank: String,
        val end: TakeEnd,
        val noteCount: Int,
        val perfect: Int,
        val good: Int,
        val ok: Int,
        /** MISS verdicts actually given: notes the take never reached are not misses. */
        val misses: Int,
        val extras: Int,
        val accuracy: Float,
        val passed: Boolean,
        val stars: Int,
        val crown: Boolean,
        /** Median signed offset of the hit notes, or null with no hit at all. */
        val medianOffsetMs: Float?,
        val padMs: Long,
        /**
         * The level progress already held a completed take of this level and rank before the
         * ledger saw one: what happened before tracking is unknown, so "first take" and
         * "comeback" can never be claimed for it.
         */
        val variantPlayedBefore: Boolean,
    ) : PlayEvent {
        val hits: Int get() = perfect + good + ok
        val judgedNotes: Int get() = hits + misses
        val variantKey: String get() = "$levelId:$rank"
    }

    /** One session of the endless practice loop. */
    data class PracticeTake(
        override val at: StatsInstant,
        val levelId: String,
        val end: TakeEnd,
        val perfect: Int,
        val good: Int,
        val ok: Int,
        val extras: Int,
        val padMs: Long,
        val activeMs: Long,
    ) : PlayEvent {
        val hits: Int get() = perfect + good + ok
    }

    /** One run of the metronome, from Play to Stop. [levelId] is set for a level's sticking. */
    data class MetronomeRun(
        override val at: StatsInstant,
        val durationMs: Long,
        val levelId: String? = null,
    ) : PlayEvent

    /** The app was in the foreground for [durationMs]; flushed when it leaves. */
    data class ForegroundSession(
        override val at: StatsInstant,
        val durationMs: Long,
        val coldStart: Boolean,
    ) : PlayEvent

    data class SoundCheckOpened(override val at: StatsInstant) : PlayEvent

    data class SoundCheckCompleted(override val at: StatsInstant) : PlayEvent

    data class CalibrationOpened(override val at: StatsInstant) : PlayEvent
}
