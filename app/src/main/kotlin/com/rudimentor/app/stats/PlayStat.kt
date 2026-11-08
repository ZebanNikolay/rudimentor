package com.rudimentor.app.stats

/**
 * One counter of the play-stats ledger (decision 257).
 *
 * [key] is the storage name and never changes once shipped: a renamed key is a counter that
 * silently restarts from zero. [daily] counters are also kept per local day, so habits, weeks
 * and "per day" thresholds can be worked out later under rules nobody has written yet.
 * [max] counters keep the largest value seen instead of a sum.
 *
 * Raw facts only. Where a future badge needs an anti-farm filter that depends on the take
 * itself (accuracy, note count), both the raw and the filtered count are stored, because the
 * take is gone afterwards and the filter cannot be applied retroactively.
 */
enum class PlayStat(val key: String, val daily: Boolean = false, val max: Boolean = false) {
    // Challenge ("Play level"): every take that reached the engine.
    ChallengeStarted("ch.started", daily = true),
    /** Reached its own finish -- the only takes that move the record (decision 215). */
    ChallengeCompleted("ch.completed", daily = true),
    /** Closed with Stop after at least one judged note. */
    ChallengeStopped("ch.stopped", daily = true),
    /** Left by Back, backgrounding, Repeat, a lost stream, or Stop before the first note. */
    ChallengeAbandoned("ch.abandoned"),
    ChallengePassed("ch.passed", daily = true),
    /** Completed takes with three stars (the same state as FULL COMBO, decision 126). */
    ChallengeThreeStars("ch.threeStars"),
    ChallengeCrowned("ch.crowned"),
    /** Notes that received a verdict, in any take. */
    ChallengeNotes("ch.notes"),
    ChallengePerfect("ch.perfect", daily = true),
    ChallengeGood("ch.good", daily = true),
    ChallengeOk("ch.ok", daily = true),
    /** Every MISS verdict, in any take. */
    ChallengeMiss("ch.miss", daily = true),
    /** MISS verdicts of completed takes with accuracy of at least 30 % (Learning curve). */
    ChallengeMissCounted("ch.miss.counted"),
    ChallengeExtra("ch.extra", daily = true),
    /** First note to the end of the take, microphone listening. Any take. */
    ChallengePadMs("ch.padMs", daily = true),
    /** The same, only for takes where at least 30 % of the judged notes were hit. */
    ChallengePadMsCounted("ch.padMs.counted"),
    /** Completed, no MISS and no extra: any length / at least 16 notes. */
    CleanTakesRaw("ch.clean.raw"),
    CleanTakes("ch.clean"),
    /** Completed, every note PERFECT: any length / at least 16 notes. */
    FlawlessTakesRaw("ch.flawless.raw"),
    FlawlessTakes("ch.flawless"),
    /** Completed, median offset within 5 ms: any take / passed with at least 32 notes. */
    DeadCenterTakesRaw("ch.deadCenter.raw"),
    DeadCenterTakes("ch.deadCenter"),
    /** Completed takes finished between 00:00 and 05:00 local time. */
    ChallengeNightTakes("ch.night"),

    // Practice: the endless loop of a level.
    PracticeStarted("pr.started", daily = true),
    /** Closed with Stop, which is how a practice session normally ends. */
    PracticeFinished("pr.finished", daily = true),
    PracticeAbandoned("pr.abandoned"),
    /** Strokes the loop matched to a note: no extras, no echoes. */
    PracticeHits("pr.hits", daily = true),
    PracticePerfect("pr.perfect"),
    PracticeGood("pr.good"),
    PracticeOk("pr.ok"),
    PracticeExtra("pr.extra"),
    /** End of the count-in to the end of the session. */
    PracticePadMs("pr.padMs", daily = true),
    /**
     * Only the time the learner was actually playing: gaps between strokes, each capped at
     * [PlayStatsRules.ACTIVE_GAP_CAP_MS]. A loop left running over a silent pad adds nothing.
     */
    PracticeActiveMs("pr.activeMs", daily = true),
    PracticeLongestMs("pr.longestMs", max = true),
    /** Sessions with at least 15 minutes of active playing (a "Long haul" candidate). */
    PracticeLongSessions("pr.long15"),
    PracticeNightSessions("pr.night"),

    // Metronome: never counts as time on the pad (§4 of the achievements draft).
    MetronomeRuns("mt.runs", daily = true),
    MetronomeMs("mt.ms", daily = true),

    // The app itself.
    /** Every time the app came to the foreground. */
    ForegroundEntries("app.foreground", daily = true),
    /** A fresh process: the system had killed the app, or it was never running. */
    ColdStarts("app.coldStarts", daily = true),
    ForegroundMs("app.ms", daily = true),
    SoundCheckOpened("sc.opened"),
    SoundCheckCompleted("sc.completed"),
    CalibrationOpened("cal.opened"),
    ;

    companion object {
        private val byKey = entries.associateBy(PlayStat::key)

        fun forKey(key: String): PlayStat? = byKey[key]
    }
}
