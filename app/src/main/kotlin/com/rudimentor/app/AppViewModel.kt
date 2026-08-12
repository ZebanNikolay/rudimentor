package com.rudimentor.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.SettingsRepository
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LearningProgress
import com.rudimentor.app.data.levels.LevelProgressRepository
import com.rudimentor.app.data.levels.toPracticeGrid
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val repository: SettingsRepository,
    progressRepository: LevelProgressRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    val learningProgress: StateFlow<LearningProgress> = progressRepository.progress.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LearningProgress.placeholder(),
    )

    fun setBpm(bpm: Int) = update { copy(bpm = Bpm.clamp(bpm)) }

    fun adjustBpm(delta: Int) = update { copy(bpm = Bpm.adjust(bpm, delta)) }

    fun selectRow(rowIndex: Int) = update {
        copy(activeRow = rowIndex.mod(grid.rowCount))
    }

    fun addRow() = update { setRowCountInternal(grid.rowCount + 1) }

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

    fun configureLevel(level: Level, bpm: Int) = update {
        copy(
            grid = level.toPracticeGrid(),
            bpm = Bpm.clamp(bpm),
            activeRow = 0,
            showHandLetters = true,
        )
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
