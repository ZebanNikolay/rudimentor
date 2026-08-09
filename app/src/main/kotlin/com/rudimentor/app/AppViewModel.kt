package com.rudimentor.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.PatternMode
import com.rudimentor.app.data.SettingsRepository
import com.rudimentor.app.ui.BeatIndicatorStyle
import com.rudimentor.app.ui.theme.PaletteId
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

    fun selectTrackerStyle(style: BeatIndicatorStyle) = update { copy(trackerStyle = style) }

    fun selectPalette(paletteId: PaletteId) = update { copy(paletteId = paletteId) }

    fun selectMode(mode: PatternMode) = update { copy(mode = mode) }

    fun addBeat() = update { copy(pattern = pattern.addBeat()) }

    fun removeBeat() = update { copy(pattern = pattern.removeBeat()) }

    fun toggleBeat(index: Int) = update {
        copy(
            pattern = when (mode) {
                PatternMode.Abstract -> pattern.toggleAccent(index)
                PatternMode.RightLeft -> pattern.toggleHand(index)
            },
        )
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
