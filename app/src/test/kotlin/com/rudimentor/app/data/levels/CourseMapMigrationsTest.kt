package com.rudimentor.app.data.levels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseMapMigrationsTest {
    @Test
    fun `paradiddles v2 resets only changed triplet ranks`() {
        val migration = CourseMapMigrations.plan(
            familyId = "paradiddles",
            fromVersion = 1,
            toVersion = 2,
        )

        assertTrue(migration.isComplete)
        assertEquals(2, migration.appliedVersion)
        assertEquals(
            setOf(
                RankCompletionReset("paradiddles.TS-01", PracticeRank.Groove),
                RankCompletionReset("paradiddles.TS-01", PracticeRank.Stage),
                RankCompletionReset("paradiddles.SS-04", PracticeRank.Groove),
                RankCompletionReset("paradiddles.SS-04", PracticeRank.Stage),
            ),
            migration.completionResets,
        )
    }

    @Test
    fun `migration is empty after v2 has been applied`() {
        val migration = CourseMapMigrations.plan(
            familyId = "paradiddles",
            fromVersion = 2,
            toVersion = 2,
        )

        assertTrue(migration.isComplete)
        assertEquals(2, migration.appliedVersion)
        assertTrue(migration.completionResets.isEmpty())
    }

    @Test
    fun `singles v2 preserves completion`() {
        val migration = CourseMapMigrations.plan(
            familyId = "singles",
            fromVersion = 1,
            toVersion = 2,
        )

        assertTrue(migration.isComplete)
        assertEquals(2, migration.appliedVersion)
        assertTrue(migration.completionResets.isEmpty())
    }

    @Test
    fun `v3 keeps completion in both families`() {
        listOf("singles", "paradiddles").forEach { familyId ->
            val migration = CourseMapMigrations.plan(
                familyId = familyId,
                fromVersion = 2,
                toVersion = 3,
            )

            assertTrue(migration.isComplete)
            assertEquals(3, migration.appliedVersion)
            assertTrue(migration.completionResets.isEmpty())
        }
    }

    @Test
    fun `skipped releases apply every intermediate migration exactly once`() {
        val migration = CourseMapMigrations.plan(
            familyId = "paradiddles",
            fromVersion = 1,
            toVersion = 3,
        )

        assertTrue(migration.isComplete)
        assertEquals(3, migration.appliedVersion)
        assertEquals(4, migration.completionResets.size)
    }

    @Test
    fun `missing migration does not advance stored version or reset progress`() {
        val migration = CourseMapMigrations.plan(
            familyId = "doubles",
            fromVersion = 1,
            toVersion = 2,
        )

        assertTrue(!migration.isComplete)
        assertEquals(1, migration.appliedVersion)
        assertTrue(migration.completionResets.isEmpty())
    }

    @Test
    fun `app rollback never downgrades stored map version`() {
        val migration = CourseMapMigrations.plan(
            familyId = "singles",
            fromVersion = 3,
            toVersion = 2,
        )

        assertTrue(migration.isComplete)
        assertEquals(3, migration.appliedVersion)
        assertTrue(migration.completionResets.isEmpty())
    }

    @Test
    fun `corrupted non-positive version is treated as legacy v1`() {
        val migration = CourseMapMigrations.plan(
            familyId = "singles",
            fromVersion = 0,
            toVersion = 3,
        )

        assertTrue(migration.isComplete)
        assertEquals(3, migration.appliedVersion)
        assertTrue(migration.completionResets.isEmpty())
    }
}
