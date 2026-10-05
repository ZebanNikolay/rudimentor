package com.rudimentor.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.MetronomeEdits
import com.rudimentor.app.data.OutputDevice
import com.rudimentor.app.data.OutputProfile
import com.rudimentor.app.data.SettingsDraft
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

    fun adjustBpm(delta: Int) = update { MetronomeEdits.adjustBpm(this, delta) }

    fun selectRow(rowIndex: Int) = update { MetronomeEdits.selectRow(this, rowIndex) }

    fun addRow() = update { MetronomeEdits.addRow(this) }

    fun removeRow() = update { MetronomeEdits.removeRow(this) }

    fun addBeat(rowIndex: Int) = update { MetronomeEdits.addBeat(this, rowIndex) }

    fun removeBeat(rowIndex: Int) = update { MetronomeEdits.removeBeat(this, rowIndex) }

    fun cycleBeat(rowIndex: Int, beatIndex: Int) = update { MetronomeEdits.cycleBeat(this, rowIndex, beatIndex) }

    fun toggleHand(rowIndex: Int, beatIndex: Int) = update { MetronomeEdits.toggleHand(this, rowIndex, beatIndex) }

    fun setShowHandLetters(show: Boolean) = update { copy(showHandLetters = show) }

    /** The click switch of the output in use (decision 172). */
    fun setClickAudible(audible: Boolean) = update { withClickAudible(audible) }

    /**
     * The millisecond numbers on the verdict floater. Lives next to the click and the
     * latency for now and will move to the app settings screen with them (decision 130).
     */
    fun setShowOffsetMs(show: Boolean) = update { copy(showOffsetMs = show) }

    /**
     * The latency compensation, plus whether the number came from the calibration screen.
     * A measured value is the whole round trip, so the engine must not add the output
     * latency on top of it again (decision 154). A hand-typed value keeps the old
     * behaviour, so nothing changes for a user who never calibrates.
     */
    fun setInputLatencyMs(latencyMs: Float, calibrated: Boolean = false) = update {
        copy(
            inputLatencyMs = latencyMs.coerceIn(
                AppSettings.LATENCY_MIN_MS,
                AppSettings.LATENCY_MAX_MS,
            ),
            latencyCalibrated = calibrated,
        )
    }

    /**
     * Commits a whole settings draft at once. Only Save reaches this, so a switch the
     * user flipped and then backed out of never lands in storage (decision 154).
     */
    fun applyDraft(draft: SettingsDraft) = update { draft.applyTo(this) }

    /**
     * Remembers that the sound check has been walked once, which is all the map needs to stop
     * calling for it. Never unset: the node stays open for a change of headphones.
     */
    /** Closes the plate that calls for the sound check; the node on the map stays. */
    fun hideSoundCheckPlate() = update {
        if (soundCheckPlateHidden) this else copy(soundCheckPlateHidden = true)
    }

    fun markSoundCheckDone() = update {
        if (soundCheckDone) this else copy(soundCheckDone = true)
    }

    /**
     * Selects the profile saved for the output that just became active (decision 161).
     *
     * This one writes straight to the settings rather than through a draft: it is the app
     * following the hardware, not the learner editing anything. An output with no profile
     * changes nothing -- the latency in force stays in force, and the screens say it may be
     * the wrong one.
     */
    fun selectProfileForOutput(device: OutputDevice?) = update {
        // No private output routed means the built-in speaker: pulling the headphones out
        // used to leave their latency in force on the speaker path (decision 212).
        val match = if (device == null) {
            outputProfiles.firstOrNull { it.id == OutputProfile.DEFAULT_ID }
        } else {
            profileFor(device)
        } ?: return@update this
        if (match.id == selectedProfileId) this else withSelectedProfile(match.id, System.currentTimeMillis())
    }

    /**
     * Stores the outcome of one practice attempt at one rank. Only improvements are kept:
     * a worse run never takes stars or a personal best away, and a rank once passed stays
     * passed. Other ranks of the same level are untouched (decision 111).
     */
    fun recordAttempt(
        levelId: String,
        rank: PracticeRank,
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
                    bestAccuracy = maxOf(current.bestAccuracy ?: 0f, accuracy),
                    crown = current.crown || crown,
                ),
            )
        }
    }

    fun selectFamily(familyId: String) {
        viewModelScope.launch { progressRepository.selectFamily(familyId) }
    }

    fun selectRank(familyId: String, rank: PracticeRank) {
        viewModelScope.launch { progressRepository.selectRank(familyId, rank) }
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
