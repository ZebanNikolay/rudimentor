package com.rudimentor.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PlayStatsLedgerTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun at(text: String, zone: ZoneId = madrid): StatsInstant =
        StatsInstant(LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli(), zone)

    private fun day(text: String): Long = LocalDate.parse(text).toEpochDay()

    private fun take(
        at: StatsInstant,
        end: TakeEnd = TakeEnd.Completed,
        noteCount: Int = 32,
        perfect: Int = 32,
        good: Int = 0,
        ok: Int = 0,
        misses: Int = noteCount - perfect - good - ok,
        extras: Int = 0,
        accuracy: Float = (perfect + good * 0.7f + ok * 0.4f) / noteCount,
        passed: Boolean = accuracy >= 0.7f,
        stars: Int = if (passed) 3 else 0,
        crown: Boolean = passed && perfect == noteCount,
        median: Float? = if (perfect + good + ok > 0) 2f else null,
        padMs: Long = 20_000,
        levelId: String = "singles.SI-01",
        rank: String = "practice",
        playedBefore: Boolean = false,
    ) = PlayEvent.ChallengeTake(
        at = at, levelId = levelId, rank = rank, end = end, noteCount = noteCount,
        perfect = perfect, good = good, ok = ok, misses = misses, extras = extras,
        accuracy = accuracy, passed = passed, stars = stars, crown = crown,
        medianOffsetMs = median, padMs = padMs, variantPlayedBefore = playedBefore,
    )

    private fun practice(
        at: StatsInstant,
        hits: Int = 100,
        padMs: Long = 60_000,
        activeMs: Long = 55_000,
        end: TakeEnd = TakeEnd.Completed,
        levelId: String = "singles.SI-01",
    ) = PlayEvent.PracticeTake(
        at = at, levelId = levelId, end = end, perfect = hits, good = 0, ok = 0, extras = 3,
        padMs = padMs, activeMs = activeMs,
    )

    private fun fold(vararg events: PlayEvent, from: PlayStats = PlayStats()): PlayStats =
        events.fold(from, PlayStatsLedger::apply)

    @Test
    fun `a completed take adds its verdicts, time and instant badges`() {
        val stats = fold(take(at("2026-11-08T18:00:00")))

        assertEquals(1L, stats.total(PlayStat.ChallengeStarted))
        assertEquals(1L, stats.total(PlayStat.ChallengeCompleted))
        assertEquals(1L, stats.total(PlayStat.ChallengePassed))
        assertEquals(32L, stats.total(PlayStat.ChallengePerfect))
        assertEquals(32L, stats.total(PlayStat.ChallengeNotes))
        assertEquals(20_000L, stats.total(PlayStat.ChallengePadMs))
        assertEquals(20_000L, stats.total(PlayStat.ChallengePadMsCounted))
        assertEquals(1L, stats.total(PlayStat.FlawlessTakes))
        assertEquals(1L, stats.total(PlayStat.CleanTakes))
        assertEquals(1L, stats.total(PlayStat.DeadCenterTakes))
        assertEquals(1L, stats.total(PlayStat.ChallengeCrowned))
        assertEquals(0L, stats.total(PlayStat.ChallengeNightTakes))
        val bucket = stats.day(day("2026-11-08"))
        assertEquals(32L, bucket[PlayStat.ChallengePerfect])
        assertEquals(1L, bucket[PlayStat.ChallengeCompleted])
        assertNull("not a daily counter", bucket[PlayStat.FlawlessTakes])
    }

    @Test
    fun `short takes keep the raw count but not the filtered one`() {
        val stats = fold(take(at("2026-11-08T18:00:00"), noteCount = 8, perfect = 8))

        assertEquals(1L, stats.total(PlayStat.FlawlessTakesRaw))
        assertEquals(0L, stats.total(PlayStat.FlawlessTakes))
        assertEquals(1L, stats.total(PlayStat.CleanTakesRaw))
        assertEquals(0L, stats.total(PlayStat.CleanTakes))
        assertEquals(1L, stats.total(PlayStat.DeadCenterTakesRaw))
        assertEquals(0L, stats.total(PlayStat.DeadCenterTakes))
    }

    @Test
    fun `a stopped take counts strokes and time but no take and no badge`() {
        val stats = fold(
            take(
                at("2026-11-08T18:00:00"),
                end = TakeEnd.Stopped,
                noteCount = 64, perfect = 20, misses = 0,
                passed = false, stars = 0, crown = false, padMs = 9_000,
            ),
        )

        assertEquals(1L, stats.total(PlayStat.ChallengeStarted))
        assertEquals(1L, stats.total(PlayStat.ChallengeStopped))
        assertEquals(0L, stats.total(PlayStat.ChallengeCompleted))
        assertEquals(20L, stats.total(PlayStat.ChallengePerfect))
        assertEquals(9_000L, stats.total(PlayStat.ChallengePadMs))
        assertEquals(0L, stats.total(PlayStat.FlawlessTakesRaw))
        assertEquals(0L, stats.total(PlayStat.CleanTakesRaw))
        assertEquals(0L, stats.total(PlayStat.DeadCenterTakesRaw))
        assertEquals(0L, stats.total(PlayStat.ChallengePassed))
        assertTrue("a stopped take is not an attempt of the variant", stats.variants.isEmpty())
        assertTrue(stats.levelModes.isEmpty())
        assertFalse(PlayStatsQueries.isPracticeDay(stats.day(day("2026-11-08"))))
    }

    @Test
    fun `empty and silent takes cannot be farmed`() {
        val silence = take(
            at("2026-11-08T18:00:00"),
            noteCount = 32, perfect = 0, misses = 32, accuracy = 0f, passed = false, stars = 0,
            crown = false, median = null,
        )
        val empty = take(
            at("2026-11-08T18:01:00"),
            end = TakeEnd.Abandoned, noteCount = 32, perfect = 0, misses = 0, accuracy = 0f,
            passed = false, stars = 0, crown = false, median = null, padMs = 0,
        )
        val stats = fold(silence, silence, empty, empty)

        assertEquals(0L, stats.total(PlayStat.ChallengePerfect))
        assertEquals("raw misses are kept", 64L, stats.total(PlayStat.ChallengeMiss))
        assertEquals("Learning curve ignores silence", 0L, stats.total(PlayStat.ChallengeMissCounted))
        assertEquals("time over a silent pad", 0L, stats.total(PlayStat.ChallengePadMsCounted))
        assertEquals(0L, stats.total(PlayStat.CleanTakesRaw))
        assertEquals(0L, stats.total(PlayStat.FlawlessTakesRaw))
        assertEquals(0L, stats.total(PlayStat.DeadCenterTakesRaw))
        assertEquals(2L, stats.total(PlayStat.ChallengeAbandoned))
        assertEquals(4L, stats.total(PlayStat.ChallengeStarted))
    }

    @Test
    fun `misses of an honest failed take count for Learning curve`() {
        val stats = fold(
            take(at("2026-11-08T18:00:00"), noteCount = 32, perfect = 16, misses = 16, accuracy = 0.5f, passed = false),
        )
        assertEquals(16L, stats.total(PlayStat.ChallengeMissCounted))
    }

    @Test
    fun `variant keeps failures before the first pass and the take of the first crown`() {
        val fail = take(at("2026-11-08T18:00:00"), perfect = 10, misses = 22, accuracy = 0.31f, passed = false)
        val stop = take(at("2026-11-08T18:01:00"), end = TakeEnd.Stopped, perfect = 4, misses = 0, passed = false)
        val pass = take(at("2026-11-08T18:02:00"), perfect = 28, good = 4, crown = false)
        val crown = take(at("2026-11-08T18:03:00"))
        val stats = fold(fail, stop, fail, fail, fail, fail, pass, fail, crown)

        val record = stats.variants.getValue("singles.SI-01:practice")
        assertEquals(8, record.completed)
        assertEquals(5, record.failedBeforePass)
        assertEquals(6, record.passedOnTake)
        assertEquals(8, record.crownedOnTake)
        assertTrue(record.historyComplete)
    }

    @Test
    fun `a variant already played before tracking can never be a first take`() {
        val stats = fold(take(at("2026-11-08T18:00:00"), playedBefore = true))
        val record = stats.variants.getValue("singles.SI-01:practice")
        assertEquals(1, record.crownedOnTake)
        assertFalse(record.historyComplete)
    }

    @Test
    fun `practice counts matched strokes, raw and active time`() {
        val stats = fold(
            practice(at("2026-11-08T10:00:00"), hits = 900, padMs = 20 * 60_000L, activeMs = 16 * 60_000L),
            practice(at("2026-11-08T11:00:00"), hits = 0, padMs = 60 * 60_000L, activeMs = 0, end = TakeEnd.Abandoned),
        )

        assertEquals(2L, stats.total(PlayStat.PracticeStarted))
        assertEquals(1L, stats.total(PlayStat.PracticeFinished))
        assertEquals(1L, stats.total(PlayStat.PracticeAbandoned))
        assertEquals(900L, stats.total(PlayStat.PracticeHits))
        assertEquals(80 * 60_000L, stats.total(PlayStat.PracticePadMs))
        assertEquals("the silent hour adds nothing", 16 * 60_000L, stats.total(PlayStat.PracticeActiveMs))
        assertEquals(16 * 60_000L, stats.total(PlayStat.PracticeLongestMs))
        assertEquals(1L, stats.total(PlayStat.PracticeLongSessions))
        assertTrue(PlayStatsQueries.isPracticeDay(stats.day(day("2026-11-08"))))
    }

    @Test
    fun `active practice time never exceeds the session`() {
        val stats = fold(practice(at("2026-11-08T10:00:00"), padMs = 1_000, activeMs = 50_000))
        assertEquals(1_000L, stats.total(PlayStat.PracticeActiveMs))
    }

    @Test
    fun `full toolkit modes are collected per level`() {
        val stats = fold(
            take(at("2026-11-08T10:00:00")),
            practice(at("2026-11-08T10:05:00")),
            PlayEvent.MetronomeRun(at("2026-11-08T10:10:00"), durationMs = 3_000, levelId = "singles.SI-01"),
        )
        assertEquals(
            PlayStatsRules.MODE_CHALLENGE or PlayStatsRules.MODE_PRACTICE,
            stats.levelModes["singles.SI-01"],
        )
        val after = fold(
            PlayEvent.MetronomeRun(at("2026-11-08T10:12:00"), durationMs = 30_000, levelId = "singles.SI-01"),
            from = stats,
        )
        assertEquals(PlayStatsRules.ALL_MODES, after.levelModes["singles.SI-01"])
        assertEquals(2L, after.total(PlayStat.MetronomeRuns))
        assertEquals(33_000L, after.total(PlayStat.MetronomeMs))
        assertEquals("the metronome is not time on the pad", 20_000L, after.total(PlayStat.ChallengePadMs))
    }

    @Test
    fun `foreground sessions count entries, cold starts and time`() {
        val stats = fold(
            PlayEvent.ForegroundSession(at("2026-11-08T09:00:00"), durationMs = 120_000, coldStart = true),
            PlayEvent.ForegroundSession(at("2026-11-08T21:00:00"), durationMs = 30_000, coldStart = false),
            PlayEvent.SoundCheckOpened(at("2026-11-08T21:00:00")),
            PlayEvent.SoundCheckCompleted(at("2026-11-08T21:00:00")),
            PlayEvent.CalibrationOpened(at("2026-11-08T21:00:00")),
        )
        assertEquals(2L, stats.total(PlayStat.ForegroundEntries))
        assertEquals(1L, stats.total(PlayStat.ColdStarts))
        assertEquals(150_000L, stats.total(PlayStat.ForegroundMs))
        assertEquals(150_000L, stats.day(day("2026-11-08"))[PlayStat.ForegroundMs])
        assertEquals(1L, stats.total(PlayStat.SoundCheckOpened))
        assertEquals(1L, stats.total(PlayStat.SoundCheckCompleted))
        assertEquals(1L, stats.total(PlayStat.CalibrationOpened))
        assertFalse("opening the app is not playing", PlayStatsQueries.isActiveDay(stats.day(day("2026-11-08"))))
    }

    @Test
    fun `a take over midnight belongs to the day it finished on and is a night take`() {
        val stats = fold(take(at("2026-11-09T00:00:30"), padMs = 60_000))
        assertEquals(1L, stats.day(day("2026-11-09"))[PlayStat.ChallengeCompleted])
        assertTrue(stats.day(day("2026-11-08")).isEmpty())
        assertEquals(1L, stats.total(PlayStat.ChallengeNightTakes))
        val late = fold(take(at("2026-11-09T05:00:00")))
        assertEquals("05:00 is morning", 0L, late.total(PlayStat.ChallengeNightTakes))
    }

    @Test
    fun `daily streak runs across midnight and across both DST switches`() {
        // Europe/Madrid: clocks forward on 29 March 2026, back on 25 October 2026.
        val stats = fold(
            take(at("2026-03-27T23:59:00")),
            take(at("2026-03-28T23:30:00")),
            take(at("2026-03-29T03:30:00")), // first hour after the jump
            take(at("2026-03-30T00:10:00")),
            take(at("2026-10-24T22:00:00")),
            take(at("2026-10-25T02:30:00")), // the repeated hour
            take(at("2026-10-26T01:00:00")),
        )
        val days = PlayStatsQueries.days(stats, PlayStatsQueries::isPracticeDay)
        assertEquals(
            listOf("2026-03-27", "2026-03-28", "2026-03-29", "2026-03-30", "2026-10-24", "2026-10-25", "2026-10-26")
                .map(::day),
            days,
        )
        val streak = PlayStatsQueries.dailyStreak(days, today = day("2026-10-26"))
        assertEquals(4, streak.best)
        assertEquals(3, streak.current)
        assertEquals("yesterday keeps it alive", 3, PlayStatsQueries.dailyStreak(days, day("2026-10-27")).current)
        assertEquals(0, PlayStatsQueries.dailyStreak(days, day("2026-10-28")).current)
    }

    @Test
    fun `a clock set back neither fills a gap nor opens a new day`() {
        val stats = fold(
            take(at("2026-11-08T18:00:00")),
            take(at("2026-11-10T18:00:00")),
            // Set back to the missing 9th: filed under the 10th, the gap stays a gap.
            take(at("2026-11-09T18:00:00")),
        )
        assertEquals(listOf(day("2026-11-08"), day("2026-11-10")), PlayStatsQueries.days(stats))
        assertEquals(2L, stats.day(day("2026-11-10"))[PlayStat.ChallengeCompleted])
        assertEquals(day("2026-11-10"), stats.lastDay)
        val streak = PlayStatsQueries.dailyStreak(PlayStatsQueries.days(stats), today = day("2026-11-10"))
        assertEquals(1, streak.current)
    }

    @Test
    fun `flying west over the date line does not break the streak`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val la = ZoneId.of("America/Los_Angeles")
        val stats = fold(
            take(at("2026-11-08T20:00:00", tokyo)),
            take(at("2026-11-09T08:00:00", tokyo)),
            // Landed: the local date is the 8th again.
            take(at("2026-11-08T18:00:00", la)),
            take(at("2026-11-10T09:00:00", la)),
        )
        val days = PlayStatsQueries.days(stats)
        assertEquals(listOf(day("2026-11-08"), day("2026-11-09"), day("2026-11-10")), days)
        assertEquals(3, PlayStatsQueries.dailyStreak(days, day("2026-11-10")).current)
    }

    @Test
    fun `a big jump back is a corrected clock, and the wrong future days end no streak`() {
        val stats = fold(
            take(at("2027-11-08T18:00:00")), // the clock was a year ahead
            take(at("2026-11-08T18:00:00")),
            take(at("2026-11-09T18:00:00")),
        )
        assertEquals(day("2026-11-09"), stats.lastDay)
        val days = PlayStatsQueries.days(stats)
        assertEquals(2, PlayStatsQueries.dailyStreak(days, day("2026-11-09")).current)
    }

    @Test
    fun `weekly habit counts ISO weeks with three days`() {
        val stats = fold(
            take(at("2026-11-02T18:00:00")), // Monday
            take(at("2026-11-04T18:00:00")),
            take(at("2026-11-08T18:00:00")), // Sunday
            take(at("2026-11-09T18:00:00")), // next Monday
            take(at("2026-11-10T18:00:00")),
            take(at("2026-11-11T18:00:00")),
            take(at("2026-11-17T18:00:00")), // only one day that week
        )
        val weeks = PlayStatsQueries.weeks(PlayStatsQueries.days(stats, PlayStatsQueries::isPracticeDay))
        assertEquals(listOf(day("2026-11-02"), day("2026-11-09")), weeks)
        assertEquals(2, PlayStatsQueries.weeklyStreak(weeks, today = day("2026-11-17")).current)
        assertEquals(0, PlayStatsQueries.weeklyStreak(weeks, today = day("2026-11-24")).current)
    }

    @Test
    fun `tracking starts once and first day falls back to it`() {
        val started = PlayStatsLedger.start(PlayStats(), at("2026-11-08T08:00:00"), build = 69)
        val again = PlayStatsLedger.start(started, at("2026-12-01T08:00:00"), build = 70)
        assertEquals(started, again)
        assertEquals(day("2026-11-08"), started.trackingSinceDay)
        assertEquals(69, started.trackingSinceBuild)
        assertEquals(day("2026-11-08"), PlayStatsQueries.firstDay(started))
        val played = fold(take(at("2026-11-10T18:00:00")), from = started)
        assertEquals(day("2026-11-10"), PlayStatsQueries.firstDay(played))
    }

    @Test
    fun `an event before start stamps tracking itself`() {
        val stats = fold(take(at("2026-11-08T18:00:00")))
        assertEquals(day("2026-11-08"), stats.trackingSinceDay)
    }
}
