package com.rudimentor.app.data.levels

data class LevelProgress(
    val completed: Boolean = false,
    val rankStars: Map<PracticeRank, Int> = emptyMap(),
    val bestBpm: Int? = null,
    val bestScore: Int? = null,
) {
    fun stars(rank: PracticeRank): Int = (rankStars[rank] ?: 0).coerceIn(0, MAX_STARS)

    companion object {
        private const val MAX_STARS = 3
    }
}

data class LearningProgress(
    val streakDays: Int = 0,
    val levels: Map<String, LevelProgress> = emptyMap(),
) {
    fun forLevel(levelId: String): LevelProgress = levels[levelId] ?: LevelProgress()

    fun isTierUnlocked(catalog: LevelCatalog, tierIndex: Int): Boolean {
        if (tierIndex == 0) return true
        val previousTier = catalog.tiers[tierIndex - 1]
        return previousTier.levels.isNotEmpty() && previousTier.levels
            .filter { it.column.required }
            .all { forLevel(it.id).completed }
    }

    fun currentLevel(tier: LevelTier, tierUnlocked: Boolean): Level? {
        if (!tierUnlocked) return null
        return tier.levels
            .asSequence()
            .filter { it.column.required && !forLevel(it.id).completed }
            .filter(::prerequisitesComplete)
            .minByOrNull(Level::row)
    }

    fun stateOf(level: Level, tier: LevelTier, tierUnlocked: Boolean): LevelNodeState = when {
        forLevel(level.id).completed -> LevelNodeState.Completed
        !tierUnlocked || !prerequisitesComplete(level) -> LevelNodeState.Locked
        level == currentLevel(tier, tierUnlocked) -> LevelNodeState.Current
        else -> LevelNodeState.Available
    }

    private fun prerequisitesComplete(level: Level): Boolean =
        level.prerequisiteIds.all { forLevel(it).completed }

    companion object {
        /** Temporary local progress used while scoring and the real curriculum are still in development. */
        fun placeholder(): LearningProgress = LearningProgress(
            streakDays = 12,
            levels = mapOf(
                "I-01" to completedProgress(listOf(3, 3, 2, 1), bestBpm = 124, bestScore = 9180),
                "I-02" to completedProgress(listOf(3, 2, 1, 0), bestBpm = 96, bestScore = 8420),
                "I-03" to completedProgress(listOf(3, 3, 2, 0), bestBpm = 112, bestScore = 9010),
                "I-04" to completedProgress(listOf(3, 2, 1, 0), bestBpm = 101, bestScore = 8560),
                "I-05" to completedProgress(listOf(3, 2, 2, 0), bestBpm = 108, bestScore = 8740),
                "I-06" to completedProgress(listOf(3, 2, 1, 0), bestBpm = 118, bestScore = 8260),
                "I-07" to completedProgress(listOf(3, 2, 1, 0), bestBpm = 92, bestScore = 8180),
                "I-08" to completedProgress(listOf(3, 3, 1, 0), bestBpm = 110, bestScore = 8930),
                "I-09" to completedProgress(listOf(3, 2, 1, 0), bestBpm = 106, bestScore = 8610),
                "I-10" to completedProgress(listOf(3, 2, 1, 0), bestBpm = 104, bestScore = 8840),
                "I-11" to completedProgress(listOf(3, 2, 1, 0), bestBpm = 112, bestScore = 8420),
            ),
        )

        private fun completedProgress(
            stars: List<Int>,
            bestBpm: Int,
            bestScore: Int,
        ): LevelProgress = LevelProgress(
            completed = true,
            rankStars = PracticeRank.entries.zip(stars).toMap(),
            bestBpm = bestBpm,
            bestScore = bestScore,
        )
    }
}
