package com.rudimentor.app.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    private fun toAppSettings(preferences: Preferences): AppSettings = parseSettings(
        trackerStyle = preferences[Keys.TrackerStyle],
        paletteId = preferences[Keys.PaletteId],
        mode = preferences[Keys.Mode],
        patternLength = preferences[Keys.PatternLength],
        accents = preferences[Keys.Accents],
        hands = preferences[Keys.Hands],
    )

    private fun MutablePreferences.write(settings: AppSettings) {
        this[Keys.TrackerStyle] = settings.trackerStyle.name
        this[Keys.PaletteId] = settings.paletteId.name
        this[Keys.Mode] = settings.mode.name
        this[Keys.PatternLength] = settings.pattern.size
        this[Keys.Accents] = settings.pattern.serializedAccents()
        this[Keys.Hands] = settings.pattern.serializedHands()
    }

    private object Keys {
        val TrackerStyle = stringPreferencesKey("tracker_style")
        val PaletteId = stringPreferencesKey("palette_id")
        val Mode = stringPreferencesKey("mode")
        val PatternLength = intPreferencesKey("pattern_length")
        val Accents = stringPreferencesKey("beat_accents")
        val Hands = stringPreferencesKey("beat_hands")
    }
}
