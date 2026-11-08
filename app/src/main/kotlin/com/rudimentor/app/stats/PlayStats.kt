package com.rudimentor.app.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * What one level at one rank has been through in Challenge, for the badges that care about
 * the order of takes ("Comeback", "First take"). Counts completed takes only: a stopped take is
 * neither a failure nor an attempt (catalog, Mastery).
 */
data class VariantRecord(
    val completed: Int = 0,
    /** Completed takes that did not pass, counted until the first pass and frozen after. */
    val failedBeforePass: Int = 0,
    /** 1-based number of the completed take that first passed; 0 while not passed. */
    val passedOnTake: Int = 0,
    /** 1-based number of the completed take that first earned the crown; 0 while none. */
    val crownedOnTake: Int = 0,
    /**
     * False when the level progress already had a completed take of this variant the first
     * time the ledger saw one: the numbers above then start mid-history and prove nothing
     * about the first take.
     */
    val historyComplete: Boolean = true,
)

/**
 * The play-stats ledger (decision 257): lifetime counters, compact per-day buckets and a few
 * per-level facts that would otherwise be lost. Nothing that can be derived from
 * `LearningProgress` (passes, stars, crowns, top speed) is stored here.
 *
 * Days are local dates as `LocalDate.toEpochDay()`.
 */
data class PlayStats(
    val schema: Int = SCHEMA,
    /** When this build first started counting; null until the first launch that tracks. */
    val trackingSinceMs: Long? = null,
    val trackingSinceDay: Long? = null,
    val trackingSinceBuild: Int? = null,
    val totals: Map<PlayStat, Long> = emptyMap(),
    val days: Map<Long, Map<PlayStat, Long>> = emptyMap(),
    /** Keyed `levelId:rank`. */
    val variants: Map<String, VariantRecord> = emptyMap(),
    /** Which modes each level has been played in, as [PlayStatsRules] mode bits. */
    val levelModes: Map<String, Int> = emptyMap(),
    /** The latest day anything was filed under: the guard against a clock set back. */
    val lastDay: Long? = null,
) {
    fun total(stat: PlayStat): Long = totals[stat] ?: 0L

    fun day(epochDay: Long): Map<PlayStat, Long> = days[epochDay] ?: emptyMap()

    companion object {
        const val SCHEMA = 1
    }
}

/** Thresholds of the achievements draft (§4 and the catalog), in one place. */
object PlayStatsRules {
    /** Instant badges (Flawless, Clean take) need this many notes. */
    const val INSTANT_MIN_NOTES = 16

    /** Dead center: a passed take of this many notes, median offset within [DEAD_CENTER_MS]. */
    const val DEAD_CENTER_MIN_NOTES = 32
    const val DEAD_CENTER_MS = 5f

    /** Misses count for "Learning curve" only from a completed take this accurate. */
    const val MISS_MIN_ACCURACY = 0.30f

    /** Challenge time counts only when this share of the judged notes was hit. */
    const val PAD_MIN_HIT_RATIO = 0.30f

    /** Longest silence between two practice strokes still counted as playing. */
    const val ACTIVE_GAP_CAP_MS = 3_000L

    const val LONG_SESSION_MS = 15 * 60_000L

    /** "A day with practice": a completed Challenge take or this much active Practice. */
    const val PRACTICE_DAY_MS = 5 * 60_000L

    /** Night shift: 00:00 up to (not including) this hour. */
    const val NIGHT_END_HOUR = 5

    /** A level's metronome run shorter than this does not mark the level as listened to. */
    const val METRONOME_MODE_MIN_MS = 5_000L

    /**
     * How far back the local date may move and still be treated as noise (a flight west, a
     * clock nudged back): events are then filed under the latest day already used. A larger
     * jump is taken as a real correction of a clock that was wrong.
     */
    const val MAX_BACKWARD_DAYS = 2L

    const val MODE_CHALLENGE = 1
    const val MODE_PRACTICE = 2
    const val MODE_METRONOME = 4
    const val ALL_MODES = MODE_CHALLENGE or MODE_PRACTICE or MODE_METRONOME
}

/** Pure folding of events into the ledger. No clock, no storage. */
object PlayStatsLedger {

    /** Marks the start of tracking once; later calls change nothing. */
    fun start(stats: PlayStats, at: StatsInstant, build: Int): PlayStats {
        if (stats.trackingSinceMs != null) return stats
        return stats.copy(
            trackingSinceMs = at.wallMs,
            trackingSinceDay = localDate(at).toEpochDay(),
            trackingSinceBuild = build,
        )
    }

    fun apply(stats: PlayStats, event: PlayEvent): PlayStats {
        val (day, lastDay) = resolveDay(stats.lastDay, localDate(event.at).toEpochDay())
        val acc = Accumulator(stats, day)
        when (event) {
            is PlayEvent.ChallengeTake -> challenge(acc, event)
            is PlayEvent.PracticeTake -> practice(acc, event)
            is PlayEvent.MetronomeRun -> {
                if (event.durationMs > 0) {
                    acc.add(PlayStat.MetronomeRuns, 1)
                    acc.add(PlayStat.MetronomeMs, event.durationMs)
                    if (event.levelId != null && event.durationMs >= PlayStatsRules.METRONOME_MODE_MIN_MS) {
                        acc.markMode(event.levelId, PlayStatsRules.MODE_METRONOME)
                    }
                }
            }
            is PlayEvent.ForegroundSession -> {
                acc.add(PlayStat.ForegroundEntries, 1)
                if (event.coldStart) acc.add(PlayStat.ColdStarts, 1)
                acc.add(PlayStat.ForegroundMs, event.durationMs.coerceAtLeast(0))
            }
            is PlayEvent.SoundCheckOpened -> acc.add(PlayStat.SoundCheckOpened, 1)
            is PlayEvent.SoundCheckCompleted -> acc.add(PlayStat.SoundCheckCompleted, 1)
            is PlayEvent.CalibrationOpened -> acc.add(PlayStat.CalibrationOpened, 1)
        }
        val started = if (stats.trackingSinceMs == null) {
            // An event before start(): tracking began with it.
            start(stats, event.at, build = 0)
        } else {
            stats
        }
        return acc.build(started).copy(lastDay = lastDay)
    }

    private fun challenge(acc: Accumulator, take: PlayEvent.ChallengeTake) {
        acc.add(PlayStat.ChallengeStarted, 1)
        acc.add(
            when (take.end) {
                TakeEnd.Completed -> PlayStat.ChallengeCompleted
                TakeEnd.Stopped -> PlayStat.ChallengeStopped
                TakeEnd.Abandoned -> PlayStat.ChallengeAbandoned
            },
            1,
        )
        acc.add(PlayStat.ChallengeNotes, take.judgedNotes.toLong())
        acc.add(PlayStat.ChallengePerfect, take.perfect.toLong())
        acc.add(PlayStat.ChallengeGood, take.good.toLong())
        acc.add(PlayStat.ChallengeOk, take.ok.toLong())
        acc.add(PlayStat.ChallengeMiss, take.misses.toLong())
        acc.add(PlayStat.ChallengeExtra, take.extras.toLong())
        val padMs = take.padMs.coerceAtLeast(0)
        acc.add(PlayStat.ChallengePadMs, padMs)
        if (take.judgedNotes > 0 &&
            take.hits >= take.judgedNotes * PlayStatsRules.PAD_MIN_HIT_RATIO
        ) {
            acc.add(PlayStat.ChallengePadMsCounted, padMs)
        }
        if (take.end != TakeEnd.Completed) return

        acc.markMode(take.levelId, PlayStatsRules.MODE_CHALLENGE)
        if (take.passed) acc.add(PlayStat.ChallengePassed, 1)
        if (take.stars >= 3) acc.add(PlayStat.ChallengeThreeStars, 1)
        if (take.crown) acc.add(PlayStat.ChallengeCrowned, 1)
        if (take.accuracy >= PlayStatsRules.MISS_MIN_ACCURACY) {
            acc.add(PlayStat.ChallengeMissCounted, take.misses.toLong())
        }
        val longEnough = take.noteCount >= PlayStatsRules.INSTANT_MIN_NOTES
        if (take.noteCount > 0 && take.misses == 0 && take.extras == 0 && take.hits == take.noteCount) {
            acc.add(PlayStat.CleanTakesRaw, 1)
            if (longEnough) acc.add(PlayStat.CleanTakes, 1)
        }
        if (take.noteCount > 0 && take.perfect == take.noteCount) {
            acc.add(PlayStat.FlawlessTakesRaw, 1)
            if (longEnough) acc.add(PlayStat.FlawlessTakes, 1)
        }
        val median = take.medianOffsetMs
        if (median != null && abs(median) <= PlayStatsRules.DEAD_CENTER_MS) {
            acc.add(PlayStat.DeadCenterTakesRaw, 1)
            if (take.passed && take.noteCount >= PlayStatsRules.DEAD_CENTER_MIN_NOTES) {
                acc.add(PlayStat.DeadCenterTakes, 1)
            }
        }
        if (isNight(take.at)) acc.add(PlayStat.ChallengeNightTakes, 1)

        val before = acc.variant(take.variantKey)
            ?: VariantRecord(historyComplete = !take.variantPlayedBefore)
        val number = before.completed + 1
        acc.putVariant(
            take.variantKey,
            before.copy(
                completed = number,
                failedBeforePass = if (before.passedOnTake == 0 && !take.passed) {
                    before.failedBeforePass + 1
                } else {
                    before.failedBeforePass
                },
                passedOnTake = if (before.passedOnTake == 0 && take.passed) number else before.passedOnTake,
                crownedOnTake = if (before.crownedOnTake == 0 && take.crown) number else before.crownedOnTake,
            ),
        )
    }

    private fun practice(acc: Accumulator, take: PlayEvent.PracticeTake) {
        acc.add(PlayStat.PracticeStarted, 1)
        acc.add(
            if (take.end == TakeEnd.Abandoned) PlayStat.PracticeAbandoned else PlayStat.PracticeFinished,
            1,
        )
        acc.add(PlayStat.PracticeHits, take.hits.toLong())
        acc.add(PlayStat.PracticePerfect, take.perfect.toLong())
        acc.add(PlayStat.PracticeGood, take.good.toLong())
        acc.add(PlayStat.PracticeOk, take.ok.toLong())
        acc.add(PlayStat.PracticeExtra, take.extras.toLong())
        val padMs = take.padMs.coerceAtLeast(0)
        // Active time cannot exceed the session itself, whatever the caller measured.
        val activeMs = take.activeMs.coerceIn(0, padMs)
        acc.add(PlayStat.PracticePadMs, padMs)
        acc.add(PlayStat.PracticeActiveMs, activeMs)
        acc.max(PlayStat.PracticeLongestMs, activeMs)
        if (activeMs >= PlayStatsRules.LONG_SESSION_MS) acc.add(PlayStat.PracticeLongSessions, 1)
        if (take.hits > 0) {
            acc.markMode(take.levelId, PlayStatsRules.MODE_PRACTICE)
            if (isNight(take.at)) acc.add(PlayStat.PracticeNightSessions, 1)
        }
    }

    /**
     * The day an event is filed under, and the new high-water day.
     *
     * The rule is the local date at the moment the event is committed -- the finish of a take,
     * the end of a foreground session. A take that runs over midnight belongs to the day it
     * ended on. A date that moved back by up to [PlayStatsRules.MAX_BACKWARD_DAYS] (a time zone
     * west, a clock nudged back) is clamped to the latest day already used, so going back in
     * time can neither fill an old gap in a streak nor open a new one. A bigger jump back is a
     * clock being corrected and is taken as it is.
     */
    internal fun resolveDay(lastDay: Long?, today: Long): Pair<Long, Long> = when {
        lastDay == null || today >= lastDay -> today to today
        lastDay - today <= PlayStatsRules.MAX_BACKWARD_DAYS -> lastDay to lastDay
        else -> today to today
    }

    internal fun localDate(at: StatsInstant): LocalDate =
        Instant.ofEpochMilli(at.wallMs).atZone(at.zone).toLocalDate()

    internal fun isNight(at: StatsInstant): Boolean =
        Instant.ofEpochMilli(at.wallMs).atZone(at.zone).hour < PlayStatsRules.NIGHT_END_HOUR

    private class Accumulator(private val base: PlayStats, private val day: Long) {
        private val totals = base.totals.toMutableMap()
        private val bucket = base.day(day).toMutableMap()
        private var variants: MutableMap<String, VariantRecord>? = null
        private var modes: MutableMap<String, Int>? = null

        fun add(stat: PlayStat, amount: Long) {
            if (amount <= 0) return
            totals[stat] = (totals[stat] ?: 0L) + amount
            if (stat.daily) bucket[stat] = (bucket[stat] ?: 0L) + amount
        }

        fun max(stat: PlayStat, value: Long) {
            if (value > (totals[stat] ?: 0L)) totals[stat] = value
        }

        fun variant(key: String): VariantRecord? = (variants ?: base.variants)[key]

        fun putVariant(key: String, record: VariantRecord) {
            val map = variants ?: base.variants.toMutableMap().also { variants = it }
            map[key] = record
        }

        fun markMode(levelId: String, bit: Int) {
            val map = modes ?: base.levelModes.toMutableMap().also { modes = it }
            map[levelId] = (map[levelId] ?: 0) or bit
        }

        fun build(into: PlayStats): PlayStats = into.copy(
            totals = totals,
            days = if (bucket.isEmpty()) into.days else into.days + (day to bucket.toMap()),
            variants = variants ?: into.variants,
            levelModes = modes ?: into.levelModes,
        )
    }
}

/** Questions a future achievements engine will ask; all derived, nothing stored. */
object PlayStatsQueries {

    /** Any playing at all: a note judged, a stroke matched, a metronome run. */
    fun isActiveDay(bucket: Map<PlayStat, Long>): Boolean =
        (bucket[PlayStat.ChallengePadMs] ?: 0L) > 0 ||
            (bucket[PlayStat.PracticeHits] ?: 0L) > 0 ||
            (bucket[PlayStat.MetronomeMs] ?: 0L) > 0

    /** The catalog's "day with practice": a completed take or five minutes of practice. */
    fun isPracticeDay(bucket: Map<PlayStat, Long>): Boolean =
        (bucket[PlayStat.ChallengeCompleted] ?: 0L) > 0 ||
            (bucket[PlayStat.PracticeActiveMs] ?: 0L) >= PlayStatsRules.PRACTICE_DAY_MS

    fun days(stats: PlayStats, rule: (Map<PlayStat, Long>) -> Boolean = ::isActiveDay): List<Long> =
        stats.days.filterValues(rule).keys.sorted()

    /** First day anything was played, falling back to the start of tracking. */
    fun firstDay(stats: PlayStats): Long? = days(stats).firstOrNull() ?: stats.trackingSinceDay

    data class Streak(val current: Int, val best: Int)

    /**
     * Daily streak over sorted [days]. The current streak is still alive on a day not yet
     * played, as long as yesterday was: nobody loses a streak at breakfast.
     */
    fun dailyStreak(days: List<Long>, today: Long): Streak = streak(days, today, step = 1)

    /** Monday of the ISO week [epochDay] falls in, as an epoch day. */
    fun weekOf(epochDay: Long): Long = LocalDate.ofEpochDay(epochDay)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .toEpochDay()

    /** Weeks (their Mondays) with at least [minDays] of the given days. */
    fun weeks(days: List<Long>, minDays: Int = 3): List<Long> =
        days.groupingBy(::weekOf).eachCount().filterValues { it >= minDays }.keys.sorted()

    /** Weekly habit: consecutive qualifying weeks; the running week keeps the streak alive. */
    fun weeklyStreak(weeks: List<Long>, today: Long): Streak = streak(weeks, weekOf(today), step = 7)

    /** Today as the ledger would file an event now. */
    fun today(stats: PlayStats, at: StatsInstant): Long =
        PlayStatsLedger.resolveDay(stats.lastDay, PlayStatsLedger.localDate(at).toEpochDay()).first

    private fun streak(points: List<Long>, now: Long, step: Long): Streak {
        var best = 0
        var run = 0
        var previous: Long? = null
        // The run ending at the latest point not after now. Points in the future (a clock that
        // was wrong for a while) count towards the best but never end the current streak.
        var runAtNow = 0
        var lastAtNow: Long? = null
        for (point in points) {
            run = if (previous != null && point - previous == step) run + 1 else 1
            previous = point
            if (run > best) best = run
            if (point <= now) {
                runAtNow = run
                lastAtNow = point
            }
        }
        val current = if (lastAtNow == now || lastAtNow == now - step) runAtNow else 0
        return Streak(current = current, best = best)
    }
}
