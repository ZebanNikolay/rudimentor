package com.rudimentor.app.data.levels

import org.junit.Assert.assertEquals
import org.junit.Test

class LevelsUiStateTest {
    @Test
    fun `every map keeps its own difficulty`() {
        val state = LevelsUiState(ranks = mapOf("singles" to PracticeRank.Groove))

        assertEquals(PracticeRank.Groove, state.rankFor("singles"))
        assertEquals(PracticeRank.Practice, state.rankFor("doubles"))
    }

    @Test
    fun `a map with no choice of its own keeps the course-wide rank of older builds`() {
        // An update must not drop anybody back to Practice: the one rank the app stored
        // before ranks were per map stays in force until a map is switched (decision 226).
        val state = LevelsUiState(
            ranks = mapOf("singles" to PracticeRank.Practice),
            defaultRank = PracticeRank.Stage,
        )

        assertEquals(PracticeRank.Practice, state.rankFor("singles"))
        assertEquals(PracticeRank.Stage, state.rankFor("paradiddles"))
    }
}
