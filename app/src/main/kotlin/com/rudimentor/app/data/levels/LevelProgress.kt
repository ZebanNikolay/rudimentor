package com.rudimentor.app.data.levels

/**
 * Progress of one level at one rank. Each rank is a separate pass of the same level
 * (decision 111), so a level completed at Practice is still open at Groove and Stage.
 */
data class RankProgress(
    val completed: Boolean = false,
    val stars: Int = 0,
    val bestBpm: Int? = null,
    val bestAccuracy: Float? = null,
    /** The crown: three stars with most notes dead on and next to no extra hits. */
    val crown: Boolean = false,
) {
    val clampedStars: Int get() = stars.coerceIn(0, MAX_STARS)

    companion object {
        const val MAX_STARS = 3
    }
}

data class LevelProgress(
    val ranks: Map<PracticeRank, RankProgress> = emptyMap(),
) {
    fun forRank(rank: PracticeRank): RankProgress = ranks[rank] ?: RankProgress()

    fun stars(rank: PracticeRank): Int = forRank(rank).clampedStars

    fun isCompleted(rank: PracticeRank): Boolean = forRank(rank).completed

    /** Whether the level was cleared at any rank at all: used for "seen it before" hints. */
    val startedAnyRank: Boolean get() = ranks.values.any { it.completed || it.bestAccuracy != null }
}

/**
 * Everything the levels screen knows about the learner. All questions are asked per rank,
 * because the map is walked once per rank.
 */
data class LearningProgress(
    val streakDays: Int = 0,
    val levels: Map<String, LevelProgress> = emptyMap(),
) {
    fun forLevel(levelId: String): LevelProgress = levels[levelId] ?: LevelProgress()

    fun forLevel(levelId: String, rank: PracticeRank): RankProgress = forLevel(levelId).forRank(rank)

    fun isCompleted(levelId: String, rank: PracticeRank): Boolean = forLevel(levelId).isCompleted(rank)

    fun currentLevel(catalog: LevelCatalog, rank: PracticeRank): Level? = catalog.levels
        .asSequence()
        .filter { it.column.required && !isCompleted(it.id, rank) }
        .filter { prerequisitesComplete(it, rank) }
        .minByOrNull(Level::row)

    fun stateOf(level: Level, catalog: LevelCatalog, rank: PracticeRank): LevelNodeState = when {
        isCompleted(level.id, rank) -> LevelNodeState.Completed
        !prerequisitesComplete(level, rank) -> LevelNodeState.Locked
        level == currentLevel(catalog, rank) -> LevelNodeState.Current
        else -> LevelNodeState.Available
    }

    fun isFamilyComplete(catalog: LevelCatalog, rank: PracticeRank): Boolean = catalog.levels
        .filter { it.column.required }
        .let { required -> required.isNotEmpty() && required.all { isCompleted(it.id, rank) } }

    /**
     * A map opens when the gate lesson of its tab is completed at the rank the curriculum
     * names — completing that lesson at another rank does not open the next map.
     */
    fun isTabUnlocked(tab: CurriculumTab): Boolean = when (val unlock = tab.unlock) {
        UnlockRule.Always -> true
        UnlockRule.Never -> false
        is UnlockRule.LessonRank -> isCompleted(unlock.lessonId, unlock.rank)
    }

    private fun prerequisitesComplete(level: Level, rank: PracticeRank): Boolean =
        level.prerequisiteIds.all { isCompleted(it, rank) }
}
