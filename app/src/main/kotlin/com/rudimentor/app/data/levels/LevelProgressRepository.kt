package com.rudimentor.app.data.levels

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
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

/** What the levels screen remembers between launches besides progress itself. */
data class LevelsUiState(
    val familyId: String? = null,
    val rank: PracticeRank = PracticeRank.Practice,
)

interface LevelProgressRepository {
    val progress: Flow<LearningProgress>

    /** The selected map and the global rank, restored on the next launch. */
    val uiState: Flow<LevelsUiState>

    suspend fun saveLevel(levelId: String, rank: PracticeRank, progress: RankProgress)

    suspend fun selectFamily(familyId: String)

    suspend fun selectRank(rank: PracticeRank)
}

/**
 * Stores progress per level *and* per rank. Keys of the previous single-rank layout are not
 * read: three passes of a level cannot be reconstructed from one completion flag, so early
 * local progress is dropped instead of being guessed at.
 */
class DataStoreLevelProgressRepository(
    private val context: Context,
    course: LevelCourse,
) : LevelProgressRepository {
    private val levelIds = course.levelIds

    // Every tab of the curriculum, not only the ones with a package: a planned tab can be
    // opened on the map to read what it will contain, and that choice is worth remembering.
    private val familyIds = course.tabs.map(CurriculumTab::id).toSet()

    override val progress: Flow<LearningProgress> = preferences().map(::toLearningProgress)

    override val uiState: Flow<LevelsUiState> = preferences().map(::toUiState)

    override suspend fun saveLevel(levelId: String, rank: PracticeRank, progress: RankProgress) {
        require(levelId in levelIds) { "Unknown level: $levelId" }
        context.levelProgressDataStore.edit { preferences ->
            preferences.write(levelId, rank, progress)
        }
    }

    override suspend fun selectFamily(familyId: String) {
        require(familyId in familyIds) { "Unknown family: $familyId" }
        context.levelProgressDataStore.edit { preferences ->
            preferences[Keys.ActiveFamily] = familyId
        }
    }

    override suspend fun selectRank(rank: PracticeRank) {
        context.levelProgressDataStore.edit { preferences ->
            preferences[Keys.ActiveRank] = rank.storageName
        }
    }

    private fun preferences(): Flow<Preferences> = context.levelProgressDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }

    private fun toLearningProgress(preferences: Preferences): LearningProgress = LearningProgress(
        streakDays = preferences[Keys.StreakDays] ?: 0,
        levels = levelIds.associateWith { levelId ->
            LevelProgress(
                ranks = PracticeRank.entries.associateWith { rank ->
                    RankProgress(
                        completed = preferences[Keys.completed(levelId, rank)] ?: false,
                        stars = preferences[Keys.stars(levelId, rank)] ?: 0,
                        bestAccuracy = preferences[Keys.bestAccuracy(levelId, rank)]
                            ?.takeUnless { it < 0f },
                        crown = preferences[Keys.crown(levelId, rank)] ?: false,
                    )
                },
            )
        },
    )

    private fun toUiState(preferences: Preferences): LevelsUiState = LevelsUiState(
        familyId = preferences[Keys.ActiveFamily]?.takeIf { it in familyIds },
        rank = preferences[Keys.ActiveRank]
            ?.let { stored -> PracticeRank.entries.firstOrNull { it.storageName == stored } }
            ?: PracticeRank.Practice,
    )

    private fun MutablePreferences.write(levelId: String, rank: PracticeRank, progress: RankProgress) {
        this[Keys.completed(levelId, rank)] = progress.completed
        this[Keys.stars(levelId, rank)] = progress.clampedStars
        this[Keys.bestAccuracy(levelId, rank)] = progress.bestAccuracy ?: NO_ACCURACY
        this[Keys.crown(levelId, rank)] = progress.crown
    }

    private object Keys {
        val StreakDays = intPreferencesKey("streak_days")
        val ActiveFamily = stringPreferencesKey("levels.active_family")
        val ActiveRank = stringPreferencesKey("levels.active_rank")

        fun completed(levelId: String, rank: PracticeRank) =
            booleanPreferencesKey("level.$levelId.${rank.storageName}.completed")

        fun stars(levelId: String, rank: PracticeRank) =
            intPreferencesKey("level.$levelId.${rank.storageName}.stars")

        fun bestAccuracy(levelId: String, rank: PracticeRank) =
            floatPreferencesKey("level.$levelId.${rank.storageName}.best_accuracy")

        fun crown(levelId: String, rank: PracticeRank) =
            booleanPreferencesKey("level.$levelId.${rank.storageName}.crown")
    }

    companion object {
        private const val NO_ACCURACY = -1f
    }
}
