package com.rudimentor.app.data.levels

/**
 * A progress reset required when a map version changes the meaning of an existing rank.
 * Historical accuracy stays intact; only completion state is invalidated.
 */
internal data class RankCompletionReset(
    val levelId: String,
    val rank: PracticeRank,
)

internal object CourseMapMigrations {
    const val INITIAL_MAP_VERSION = 1

    fun completionResets(
        familyId: String,
        fromVersion: Int,
        toVersion: Int,
    ): Set<RankCompletionReset> = buildSet {
        if (familyId == "paradiddles" && fromVersion < 2 && toVersion >= 2) {
            listOf("paradiddles.TS-01", "paradiddles.SS-04").forEach { levelId ->
                add(RankCompletionReset(levelId, PracticeRank.Groove))
                add(RankCompletionReset(levelId, PracticeRank.Stage))
            }
        }
    }
}
