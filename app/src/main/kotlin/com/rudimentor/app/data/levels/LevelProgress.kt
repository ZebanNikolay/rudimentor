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

    fun currentLevel(catalog: LevelCatalog): Level? = catalog.levels
        .asSequence()
        .filter { it.column.required && !forLevel(it.id).completed }
        .filter(::prerequisitesComplete)
        .minByOrNull(Level::row)

    fun stateOf(level: Level, catalog: LevelCatalog): LevelNodeState = when {
        forLevel(level.id).completed -> LevelNodeState.Completed
        !prerequisitesComplete(level) -> LevelNodeState.Locked
        level == currentLevel(catalog) -> LevelNodeState.Current
        else -> LevelNodeState.Available
    }

    fun isFamilyComplete(catalog: LevelCatalog): Boolean = catalog.levels
        .filter { it.column.required }
        .let { required -> required.isNotEmpty() && required.all { forLevel(it.id).completed } }

    private fun prerequisitesComplete(level: Level): Boolean =
        level.prerequisiteIds.all { forLevel(it).completed }

    companion object {
        /** Temporary local progress used while scoring is still in development. */
        fun placeholder(): LearningProgress = LearningProgress(
            streakDays = 12,
            levels = mapOf(
                "singles.ST-01" to completedProgress(listOf(3, 3, 2), bestBpm = 60, bestScore = 9180),
                "singles.ST-02" to completedProgress(listOf(3, 2, 1), bestBpm = 70, bestScore = 8420),
                "singles.ST-03" to completedProgress(listOf(3, 3, 2), bestBpm = 80, bestScore = 9010),
                "singles.ST-04" to completedProgress(listOf(3, 2, 1), bestBpm = 90, bestScore = 8560),
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
