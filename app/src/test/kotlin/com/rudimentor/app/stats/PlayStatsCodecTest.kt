package com.rudimentor.app.stats

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PlayStatsCodecTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val at = StatsInstant(LocalDateTime.parse("2026-11-08T18:00:00").atZone(zone).toInstant().toEpochMilli(), zone)

    private val take = PlayEvent.ChallengeTake(
        at = at, levelId = "singles.SI-01", rank = "practice", end = TakeEnd.Completed, noteCount = 32,
        perfect = 30, good = 2, ok = 0, misses = 0, extras = 1, accuracy = 0.98f, passed = true,
        stars = 3, crown = false, medianOffsetMs = -3f, padMs = 15_000, variantPlayedBefore = false,
    )

    private fun sample(): PlayStats {
        var stats = PlayStatsLedger.start(PlayStats(), at, build = 69)
        stats = PlayStatsLedger.apply(stats, take)
        stats = PlayStatsLedger.apply(stats, PlayEvent.MetronomeRun(at, 60_000, "singles.SI-01"))
        stats = PlayStatsLedger.apply(stats, PlayEvent.ForegroundSession(at, 90_000, coldStart = true))
        return stats
    }

    @Test
    fun `round trip keeps every field`() {
        val stats = sample()
        assertEquals(stats, PlayStatsCodec.decode(PlayStatsCodec.encode(stats)))
    }

    @Test
    fun `no data decodes as an empty ledger of the current schema`() {
        val stats = PlayStatsCodec.decode(emptyMap())
        assertEquals(PlayStats(), stats)
        assertNull(stats.trackingSinceMs)
    }

    @Test
    fun `malformed and unknown entries are skipped, the rest survives`() {
        val encoded = PlayStatsCodec.encode(sample()).toMutableMap<String, Any?>()
        encoded["stats.total.future.counter"] = 7L
        encoded["stats.total.ch.perfect"] = "not a number"
        encoded["stats.day.yesterday"] = "ch.perfect=1"
        encoded["stats.day.20000"] = "ch.perfect=x;new.key=3;=4;ch.good=2"
        encoded["stats.variant.broken:practice"] = "1,2"
        val stats = PlayStatsCodec.decode(encoded)

        assertEquals(0L, stats.total(PlayStat.ChallengePerfect))
        assertEquals(2L, stats.total(PlayStat.ChallengeGood))
        assertEquals(mapOf(PlayStat.ChallengeGood to 2L), stats.day(20_000))
        assertTrue("broken:practice" !in stats.variants)
        assertEquals(1, stats.variants.size)
    }

    @Test
    fun `first write into no data stamps the schema and keeps foreign keys`() {
        val preferences = mutablePreferencesOf(stringPreferencesKey("other.key") to "kept")
        preferences.updateStats { PlayStatsLedger.apply(it, take) }

        assertEquals(PlayStats.SCHEMA, preferences[intPreferencesKey(PlayStatsCodec.SCHEMA)])
        assertEquals(1L, preferences[longPreferencesKey("stats.total.ch.completed")])
        assertEquals("kept", preferences[stringPreferencesKey("other.key")])
        assertEquals("1,0,1,0,1", preferences[stringPreferencesKey("stats.variant.singles.SI-01:practice")])
    }

    @Test
    fun `updates accumulate and keep counters of a later build`() {
        val preferences = mutablePreferencesOf(longPreferencesKey("stats.total.future.counter") to 7L)
        repeat(3) { preferences.updateStats { PlayStatsLedger.apply(it, take) } }

        assertEquals(3L, preferences[longPreferencesKey("stats.total.ch.completed")])
        assertEquals(90L, preferences[longPreferencesKey("stats.total.ch.perfect")])
        assertEquals(7L, preferences[longPreferencesKey("stats.total.future.counter")])
    }

    @Test
    fun `a ledger of a newer schema is never rewritten by an older build`() {
        val preferences = mutablePreferencesOf(
            intPreferencesKey(PlayStatsCodec.SCHEMA) to PlayStats.SCHEMA + 1,
            longPreferencesKey("stats.total.ch.completed") to 5L,
        )
        preferences.updateStats { PlayStatsLedger.apply(it, take) }

        assertEquals(5L, preferences[longPreferencesKey("stats.total.ch.completed")])
        assertEquals(PlayStats.SCHEMA + 1, preferences[intPreferencesKey(PlayStatsCodec.SCHEMA)])
    }

    @Test
    fun `starting twice does not move the start of tracking`() {
        val preferences = mutablePreferencesOf()
        preferences.updateStats { PlayStatsLedger.start(it, at, 69) }
        val first = preferences[longPreferencesKey(PlayStatsCodec.SINCE_MS)]
        preferences.updateStats { PlayStatsLedger.start(it, StatsInstant(at.wallMs + 86_400_000, zone), 70) }

        assertEquals(first, preferences[longPreferencesKey(PlayStatsCodec.SINCE_MS)])
        assertEquals(69, preferences[intPreferencesKey(PlayStatsCodec.SINCE_BUILD)])
    }
}
