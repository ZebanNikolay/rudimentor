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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val mapVersions = course.catalogs.mapValues { (_, catalog) -> catalog.mapVersion }
    private val migrationMutex = Mutex()
    private var migrationsApplied = false

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
            preferences[LevelProgressKeys.ActiveFamily] = familyId
        }
    }

    override suspend fun selectRank(rank: PracticeRank) {
        context.levelProgressDataStore.edit { preferences ->
            preferences[LevelProgressKeys.ActiveRank] = rank.storageName
        }
    }

    private fun preferences(): Flow<Preferences> = flow {
        ensureMapMigrations()
        emitAll(
            context.levelProgressDataStore.data.catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            },
        )
    }

    private suspend fun ensureMapMigrations() {
        migrationMutex.withLock {
            if (migrationsApplied) return
            context.levelProgressDataStore.edit { preferences ->
                mapVersions.forEach { (familyId, currentVersion) ->
                    val versionKey = LevelProgressKeys.mapVersion(familyId)
                    val appliedVersion = preferences[versionKey]
                        ?: CourseMapMigrations.INITIAL_MAP_VERSION
                    val migration = CourseMapMigrations.plan(
                        familyId = familyId,
                        fromVersion = appliedVersion,
                        toVersion = currentVersion,
                    )
                    preferences.applyMapMigration(familyId, migration)
                }
            }
            migrationsApplied = true
        }
    }

    private fun toLearningProgress(preferences: Preferences): LearningProgress = LearningProgress(
        streakDays = preferences[LevelProgressKeys.StreakDays] ?: 0,
        levels = levelIds.associateWith { levelId ->
            LevelProgress(
                ranks = PracticeRank.entries.associateWith { rank ->
                    RankProgress(
                        completed = preferences[LevelProgressKeys.completed(levelId, rank)] ?: false,
                        stars = preferences[LevelProgressKeys.stars(levelId, rank)] ?: 0,
                        bestAccuracy = preferences[LevelProgressKeys.bestAccuracy(levelId, rank)]
                            ?.takeUnless { it < 0f },
                        crown = preferences[LevelProgressKeys.crown(levelId, rank)] ?: false,
                    )
                },
            )
        },
    )

    private fun toUiState(preferences: Preferences): LevelsUiState = LevelsUiState(
        familyId = preferences[LevelProgressKeys.ActiveFamily]?.takeIf { it in familyIds },
        rank = preferences[LevelProgressKeys.ActiveRank]
            ?.let { stored -> PracticeRank.entries.firstOrNull { it.storageName == stored } }
            ?: PracticeRank.Practice,
    )

    private fun MutablePreferences.write(levelId: String, rank: PracticeRank, progress: RankProgress) {
        this[LevelProgressKeys.completed(levelId, rank)] = progress.completed
        this[LevelProgressKeys.stars(levelId, rank)] = progress.clampedStars
        this[LevelProgressKeys.bestAccuracy(levelId, rank)] = progress.bestAccuracy ?: NO_ACCURACY
        this[LevelProgressKeys.crown(levelId, rank)] = progress.crown
    }

    companion object {
        private const val NO_ACCURACY = -1f
    }
}

internal object LevelProgressKeys {
    val StreakDays = intPreferencesKey("streak_days")
    val ActiveFamily = stringPreferencesKey("levels.active_family")
    val ActiveRank = stringPreferencesKey("levels.active_rank")

    fun mapVersion(familyId: String) = intPreferencesKey("map.$familyId.applied_version")

    fun completed(levelId: String, rank: PracticeRank) =
        booleanPreferencesKey("level.$levelId.${rank.storageName}.completed")

    fun stars(levelId: String, rank: PracticeRank) =
        intPreferencesKey("level.$levelId.${rank.storageName}.stars")

    fun bestAccuracy(levelId: String, rank: PracticeRank) =
        floatPreferencesKey("level.$levelId.${rank.storageName}.best_accuracy")

    fun crown(levelId: String, rank: PracticeRank) =
        booleanPreferencesKey("level.$levelId.${rank.storageName}.crown")
}

/**
 * Applies only the progress fields a map migration is allowed to invalidate.
 * Accuracy is historical evidence and intentionally survives completion resets.
 */
internal fun MutablePreferences.applyMapMigration(
    familyId: String,
    migration: MapMigrationPlan,
) {
    migration.completionResets.forEach { reset ->
        this[LevelProgressKeys.completed(reset.levelId, reset.rank)] = false
        this[LevelProgressKeys.stars(reset.levelId, reset.rank)] = 0
        this[LevelProgressKeys.crown(reset.levelId, reset.rank)] = false
    }
    this[LevelProgressKeys.mapVersion(familyId)] = migration.appliedVersion
}
