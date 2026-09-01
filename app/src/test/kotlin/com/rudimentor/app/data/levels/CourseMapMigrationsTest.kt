package com.rudimentor.app.data.levels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseMapMigrationsTest {
    @Test
    fun `paradiddles v2 resets only changed triplet ranks`() {
        val resets = CourseMapMigrations.completionResets(
            familyId = "paradiddles",
            fromVersion = 1,
            toVersion = 2,
        )

        assertEquals(
            setOf(
                RankCompletionReset("paradiddles.TS-01", PracticeRank.Groove),
                RankCompletionReset("paradiddles.TS-01", PracticeRank.Stage),
                RankCompletionReset("paradiddles.SS-04", PracticeRank.Groove),
                RankCompletionReset("paradiddles.SS-04", PracticeRank.Stage),
            ),
            resets,
        )
    }

    @Test
    fun `migration is empty after v2 has been applied`() {
        assertTrue(
            CourseMapMigrations.completionResets(
                familyId = "paradiddles",
                fromVersion = 2,
                toVersion = 2,
            ).isEmpty(),
        )
    }

    @Test
    fun `singles v2 preserves completion`() {
        assertTrue(
            CourseMapMigrations.completionResets(
                familyId = "singles",
                fromVersion = 1,
                toVersion = 2,
            ).isEmpty(),
        )
    }
}
