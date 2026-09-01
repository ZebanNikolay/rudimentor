package com.rudimentor.app.data.levels

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapMigrationApplicationTest {
    @Test
    fun `reset preserves accuracy and unrelated progress`() {
        val resetLevel = "paradiddles.TS-01"
        val unrelatedLevel = "paradiddles.SP-01"
        val rank = PracticeRank.Groove
        val preferences = mutablePreferencesOf(
            LevelProgressKeys.mapVersion("paradiddles") to 1,
            LevelProgressKeys.completed(resetLevel, rank) to true,
            LevelProgressKeys.stars(resetLevel, rank) to 3,
            LevelProgressKeys.bestAccuracy(resetLevel, rank) to 0.97f,
            LevelProgressKeys.crown(resetLevel, rank) to true,
            LevelProgressKeys.completed(unrelatedLevel, rank) to true,
            LevelProgressKeys.stars(unrelatedLevel, rank) to 2,
        )

        preferences.applyMapMigration(
            familyId = "paradiddles",
            migration = CourseMapMigrations.plan("paradiddles", 1, 3),
        )

        assertFalse(preferences[LevelProgressKeys.completed(resetLevel, rank)]!!)
        assertEquals(0, preferences[LevelProgressKeys.stars(resetLevel, rank)])
        assertFalse(preferences[LevelProgressKeys.crown(resetLevel, rank)]!!)
        assertEquals(0.97f, preferences[LevelProgressKeys.bestAccuracy(resetLevel, rank)]!!)
        assertTrue(preferences[LevelProgressKeys.completed(unrelatedLevel, rank)]!!)
        assertEquals(2, preferences[LevelProgressKeys.stars(unrelatedLevel, rank)])
        assertEquals(3, preferences[LevelProgressKeys.mapVersion("paradiddles")])
    }

    @Test
    fun `missing migration step does not mark target version as applied`() {
        val preferences = mutablePreferencesOf(
            LevelProgressKeys.mapVersion("doubles") to 1,
        )

        preferences.applyMapMigration(
            familyId = "doubles",
            migration = CourseMapMigrations.plan("doubles", 1, 2),
        )

        assertEquals(1, preferences[LevelProgressKeys.mapVersion("doubles")])
    }
}
