package com.rudimentor.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setBpm(bpm: Int) = update { copy(bpm = Bpm.clamp(bpm)) }

    fun adjustBpm(delta: Int) = update { copy(bpm = Bpm.adjust(bpm, delta)) }

    fun selectRow(rowIndex: Int) = update {
        copy(activeRow = rowIndex.mod(grid.rowCount))
    }

    fun addRow() = update { setRowCountInternal(grid.rowCount + 1) }

    fun removeRow() = update { setRowCountInternal(grid.rowCount - 1) }

    fun addBeat() = update { withActiveRowLength(+1) }

    fun removeBeat() = update { withActiveRowLength(-1) }

    fun cycleBeat(beatIndex: Int) = update {
        copy(grid = grid.cycleState(safeActiveRow, beatIndex))
    }

    fun toggleHand(beatIndex: Int) = update {
        copy(grid = grid.toggleHand(safeActiveRow, beatIndex))
    }

    fun setShowHandLetters(show: Boolean) = update { copy(showHandLetters = show) }

    private fun AppSettings.setRowCountInternal(count: Int): AppSettings {
        val target = count.coerceIn(BeatGrid.MIN_ROWS, BeatGrid.MAX_ROWS)
        val resized = grid.withRowCount(target)
        return copy(grid = resized, activeRow = safeActiveRow.coerceIn(0, resized.rowCount - 1))
    }

    private fun AppSettings.withActiveRowLength(delta: Int): AppSettings {
        val row = grid.rows[safeActiveRow]
        val target = (row.size + delta).coerceIn(BeatRow.MIN_BEATS, BeatRow.MAX_BEATS)
        return copy(grid = grid.withRowLength(safeActiveRow, target))
    }

    private fun update(transform: AppSettings.() -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    companion object {
        fun factory(repository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(repository) as T
            }
    }
}
