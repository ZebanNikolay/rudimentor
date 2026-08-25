package com.rudimentor.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.SettingsRepository
import com.rudimentor.app.data.levels.LearningProgress
import com.rudimentor.app.data.levels.LevelProgressRepository
import com.rudimentor.app.data.levels.LevelsUiState
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.RankProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val repository: SettingsRepository,
    private val progressRepository: LevelProgressRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    val learningProgress: StateFlow<LearningProgress> = progressRepository.progress.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // No demo progress: an empty course is the honest starting state, and the map
        // opens its first level from it.
        initialValue = LearningProgress(),
    )

    /** The map and the difficulty the learner left the levels screen on. */
    val levelsUi: StateFlow<LevelsUiState> = progressRepository.uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LevelsUiState(),
    )

    fun setBpm(bpm: Int) = update { copy(bpm = Bpm.clamp(bpm)) }

    fun adjustBpm(delta: Int) = update { copy(bpm = Bpm.adjust(bpm, delta)) }

    fun selectRow(rowIndex: Int) = update {
        copy(activeRow = rowIndex.mod(grid.rowCount))
    }

    // A new row duplicates the last one: the user usually wants a variation of what they
    // already have, not an empty row they must fill from scratch.
    fun addRow() = update {
        if (grid.rowCount >= BeatGrid.MAX_ROWS) {
            this
        } else {
            copy(grid = grid.withRowAppended(), activeRow = grid.rowCount)
        }
    }

    fun removeRow() = update { setRowCountInternal(grid.rowCount - 1) }

    fun addBeat(rowIndex: Int) = update { withRowLength(rowIndex, +1) }

    fun removeBeat(rowIndex: Int) = update { withRowLength(rowIndex, -1) }

    // The row always comes from the caller: the edit lands on the row the user touched,
    // never on a stale "active row".
    fun cycleBeat(rowIndex: Int, beatIndex: Int) = update {
        copy(grid = grid.cycleState(rowIndex.coerceIn(0, grid.rowCount - 1), beatIndex))
    }

    fun toggleHand(rowIndex: Int, beatIndex: Int) = update {
        copy(grid = grid.toggleHand(rowIndex.coerceIn(0, grid.rowCount - 1), beatIndex))
    }

    fun setShowHandLetters(show: Boolean) = update { copy(showHandLetters = show) }

    /**
     * A hand on the switch always wins: from here on the click stays where the
     * learner put it and stops following the headphones (decision 114).
     */
    fun setClickAudible(audible: Boolean) = update {
        copy(clickAudible = audible, clickFollowsHeadphones = false)
    }

    /** Hands the click back to the headphone detector. */
    fun setClickFollowsHeadphones(follow: Boolean) = update {
        copy(clickFollowsHeadphones = follow)
    }

    /**
     * The millisecond numbers on the verdict floater. Lives next to the click and the
     * latency for now and will move to the app settings screen with them (decision 130).
     */
    fun setShowOffsetMs(show: Boolean) = update { copy(showOffsetMs = show) }

    fun setInputLatencyMs(latencyMs: Float) = update {
        copy(
            inputLatencyMs = latencyMs.coerceIn(
                AppSettings.LATENCY_MIN_MS,
                AppSettings.LATENCY_MAX_MS,
            ),
        )
    }

    /**
     * Stores the outcome of one practice attempt at one rank. Only improvements are kept:
     * a worse run never takes stars or a personal best away, and a rank once passed stays
     * passed. Other ranks of the same level are untouched (decision 111).
     */
    fun recordAttempt(
        levelId: String,
        rank: PracticeRank,
        bpm: Int,
        accuracy: Float,
        stars: Int,
        passed: Boolean,
        crown: Boolean,
    ) {
        viewModelScope.launch {
            val current = learningProgress.value.forLevel(levelId, rank)
            progressRepository.saveLevel(
                levelId = levelId,
                rank = rank,
                progress = RankProgress(
                    completed = current.completed || passed,
                    stars = maxOf(current.clampedStars, if (passed) stars else 0),
                    bestBpm = maxOf(current.bestBpm ?: 0, if (passed) bpm else 0)
                        .takeIf { it > 0 } ?: current.bestBpm,
                    bestAccuracy = maxOf(current.bestAccuracy ?: 0f, accuracy),
                    crown = current.crown || crown,
                ),
            )
        }
    }

    fun selectFamily(familyId: String) {
        viewModelScope.launch { progressRepository.selectFamily(familyId) }
    }

    fun selectRank(rank: PracticeRank) {
        viewModelScope.launch { progressRepository.selectRank(rank) }
    }

    private fun AppSettings.setRowCountInternal(count: Int): AppSettings {
        val target = count.coerceIn(BeatGrid.MIN_ROWS, BeatGrid.MAX_ROWS)
        val resized = grid.withRowCount(target)
        return copy(grid = resized, activeRow = safeActiveRow.coerceIn(0, resized.rowCount - 1))
    }

    private fun AppSettings.withRowLength(rowIndex: Int, delta: Int): AppSettings {
        val index = rowIndex.coerceIn(0, grid.rowCount - 1)
        val target = (grid.rows[index].size + delta).coerceIn(BeatRow.MIN_BEATS, BeatRow.MAX_BEATS)
        return copy(grid = grid.withRowLength(index, target))
    }

    private fun update(transform: AppSettings.() -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    companion object {
        fun factory(
            repository: SettingsRepository,
            progressRepository: LevelProgressRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(repository, progressRepository) as T
            }
    }
}
