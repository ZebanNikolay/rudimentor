package com.rudimentor.app.data.levels

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.levelProgressDataStore by preferencesDataStore(
    name = "level_progress",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

interface LevelProgressRepository {
    val progress: Flow<LearningProgress>

    suspend fun saveLevel(levelId: String, progress: LevelProgress)
}

class DataStoreLevelProgressRepository(
    private val context: Context,
    catalog: LevelCatalog,
    private val initialProgress: LearningProgress = LearningProgress.placeholder(),
) : LevelProgressRepository {
    private val levelIds = catalog.levels.map(Level::id).toSet()

    override val progress: Flow<LearningProgress> = context.levelProgressDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map(::toLearningProgress)

    override suspend fun saveLevel(levelId: String, progress: LevelProgress) {
        require(levelId in levelIds) { "Unknown level: $levelId" }
        context.levelProgressDataStore.edit { preferences ->
            preferences.write(levelId, progress)
        }
    }

    private fun toLearningProgress(preferences: Preferences): LearningProgress = LearningProgress(
        streakDays = preferences[Keys.StreakDays] ?: initialProgress.streakDays,
        levels = levelIds.associateWith { levelId ->
            val initial = initialProgress.forLevel(levelId)
            val storedBestBpm = preferences[Keys.bestBpm(levelId)]
            val storedBestScore = preferences[Keys.bestScore(levelId)]
            LevelProgress(
                completed = preferences[Keys.completed(levelId)] ?: initial.completed,
                rankStars = decodeStars(preferences[Keys.rankStars(levelId)]) ?: initial.rankStars,
                bestBpm = storedBestBpm?.takeUnless { it == NO_VALUE } ?: if (storedBestBpm == null) {
                    initial.bestBpm
                } else {
                    null
                },
                bestScore = storedBestScore?.takeUnless { it == NO_VALUE } ?: if (storedBestScore == null) {
                    initial.bestScore
                } else {
                    null
                },
            )
        },
    )

    private fun MutablePreferences.write(levelId: String, progress: LevelProgress) {
        this[Keys.completed(levelId)] = progress.completed
        this[Keys.rankStars(levelId)] = PracticeRank.entries.joinToString(",") { progress.stars(it).toString() }
        this[Keys.bestBpm(levelId)] = progress.bestBpm ?: NO_VALUE
        this[Keys.bestScore(levelId)] = progress.bestScore ?: NO_VALUE
    }

    private fun decodeStars(value: String?): Map<PracticeRank, Int>? {
        val stars = value?.split(',')?.mapNotNull(String::toIntOrNull) ?: return null
        if (stars.size != PracticeRank.entries.size || stars.any { it !in 0..MAX_STARS }) return null
        return PracticeRank.entries.zip(stars).toMap()
    }

    private object Keys {
        val StreakDays = intPreferencesKey("streak_days")

        fun completed(levelId: String) = booleanPreferencesKey("level.$levelId.completed")
        fun rankStars(levelId: String) = stringPreferencesKey("level.$levelId.rank_stars")
        fun bestBpm(levelId: String) = intPreferencesKey("level.$levelId.best_bpm")
        fun bestScore(levelId: String) = intPreferencesKey("level.$levelId.best_score")
    }

    companion object {
        private const val MAX_STARS = 3
        private const val NO_VALUE = -1
    }
}
