package com.rudimentor.app.data

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
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.audio.MicLab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map(::toAppSettings)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences.write(transform(toAppSettings(preferences)))
        }
    }

    private fun toAppSettings(preferences: Preferences): AppSettings {
        val grid = parseGrid(preferences[Keys.Grid])
        return AppSettings(
            grid = grid,
            bpm = Bpm.clamp(preferences[Keys.Bpm] ?: Bpm.DEFAULT),
            activeRow = (preferences[Keys.ActiveRow] ?: 0).coerceIn(0, grid.rowCount - 1),
            showHandLetters = preferences[Keys.ShowHandLetters] ?: true,
            clickAudible = preferences[Keys.ClickAudible] ?: false,
            inputLatencyMs = preferences[Keys.InputLatencyMs] ?: MicLab.DEFAULT_LATENCY_MS,
        ).sanitized()
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        val safe = settings.sanitized()
        this[Keys.Grid] = safe.grid.serialize()
        this[Keys.Bpm] = safe.bpm
        this[Keys.ActiveRow] = safe.activeRow
        this[Keys.ShowHandLetters] = safe.showHandLetters
        this[Keys.ClickAudible] = safe.clickAudible
        this[Keys.InputLatencyMs] = safe.inputLatencyMs
    }

    private object Keys {
        val Grid = stringPreferencesKey("beat_grid")
        val Bpm = intPreferencesKey("bpm")
        val ActiveRow = intPreferencesKey("active_row")
        val ShowHandLetters = booleanPreferencesKey("show_hand_letters")
        val ClickAudible = booleanPreferencesKey("practice_click_audible")
        val InputLatencyMs = floatPreferencesKey("practice_input_latency_ms")
    }
}
