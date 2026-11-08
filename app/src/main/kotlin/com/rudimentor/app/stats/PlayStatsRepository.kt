package com.rudimentor.app.stats

import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.rudimentor.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Its own file next to the progress and the settings, under `files/datastore/`: in release
 * builds too, and in Android backup exactly like the level progress (backup_rules.xml and
 * data_extraction_rules.xml exclude only the diagnostic log and `files/telemetry/`). It does
 * not live in `files/telemetry/`, which a release build deletes (decision 251).
 */
private val Context.playStatsDataStore by preferencesDataStore(
    name = "play_stats",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Reads and updates the ledger. Every update is one read-modify-write of the whole file. */
class PlayStatsRepository(private val context: Context) {
    val stats: Flow<PlayStats> = context.playStatsDataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .map { preferences -> PlayStatsCodec.decode(preferences.statsValues()) }

    /** Marks the start of tracking on the first launch of a build that has the ledger. */
    suspend fun start(at: StatsInstant, build: Int) {
        context.playStatsDataStore.edit { preferences ->
            preferences.updateStats { PlayStatsLedger.start(it, at, build) }
        }
    }

    suspend fun record(event: PlayEvent) {
        context.playStatsDataStore.edit { preferences ->
            preferences.updateStats { PlayStatsLedger.apply(it, event) }
        }
    }
}

/**
 * Applies [transform] to the ledger held in these preferences. A ledger written by a newer
 * schema than this build understands is left alone: an older build installed over a newer one
 * must not rewrite counters it cannot read.
 *
 * Schema 0 -- no ledger at all -- needs no migration: it decodes as an empty ledger, and the
 * first write stamps schema 1.
 */
internal fun MutablePreferences.updateStats(transform: (PlayStats) -> PlayStats) {
    val stored = PlayStatsCodec.decode(statsValues())
    if (stored.schema > PlayStats.SCHEMA) return
    val next = transform(stored.copy(schema = PlayStats.SCHEMA))
    if (next == stored) return
    PlayStatsCodec.encode(next).forEach { (name, value) ->
        when (value) {
            is Int -> this[intPreferencesKey(name)] = value
            is Long -> this[longPreferencesKey(name)] = value
            is String -> this[stringPreferencesKey(name)] = value
        }
    }
}

private fun Preferences.statsValues(): Map<String, Any?> = asMap().entries
    .filter { it.key.name.startsWith(PlayStatsCodec.PREFIX) }
    .associate { it.key.name to it.value }

/**
 * The one place the app hands play facts to the ledger. Every call returns at once; the write
 * happens on a single background writer, in order, after the take or the session is over --
 * never from the audio path or while a take is being judged.
 *
 * A no-op until [install] runs, so previews and tests that render screens write nothing.
 */
object PlayStatsRecorder {
    @Volatile
    private var repository: PlayStatsRepository? = null

    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Set for the first foreground session of this process. */
    private var coldStartPending = false

    /** Elapsed-realtime start of the current foreground session, or null in the background. */
    private var foregroundSinceMs: Long? = null

    fun install(context: Context, build: Int) {
        if (repository != null) return
        val created = PlayStatsRepository(context.applicationContext)
        repository = created
        coldStartPending = true
        val at = StatsInstant.now()
        writer.launch {
            runCatching { created.start(at, build) }
                .onFailure { AppLog.error("stats", "tracking start failed", it) }
        }
    }

    fun stats(): Flow<PlayStats>? = repository?.stats

    fun record(event: PlayEvent) {
        val target = repository ?: return
        writer.launch {
            runCatching { target.record(event) }
                .onFailure { AppLog.error("stats", "write failed for ${event::class.simpleName}", it) }
        }
    }

    /**
     * Time in the app is the activity being started, measured on the monotonic clock so a
     * clock change or DST cannot stretch it. Flushed on every stop: if the process dies in the
     * background nothing is lost, if it dies in the foreground only the current session is.
     * A stop for a configuration change is not leaving the app.
     */
    fun observe(activity: ComponentActivity) {
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (foregroundSinceMs == null) foregroundSinceMs = SystemClock.elapsedRealtime()
                }

                override fun onStop(owner: LifecycleOwner) {
                    if (activity.isChangingConfigurations) return
                    val since = foregroundSinceMs ?: return
                    foregroundSinceMs = null
                    val coldStart = coldStartPending
                    coldStartPending = false
                    record(
                        PlayEvent.ForegroundSession(
                            at = StatsInstant.now(),
                            durationMs = SystemClock.elapsedRealtime() - since,
                            coldStart = coldStart,
                        ),
                    )
                }
            },
        )
    }
}
