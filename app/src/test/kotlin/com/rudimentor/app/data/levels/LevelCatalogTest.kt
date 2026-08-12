package com.rudimentor.app.data.levels

import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCatalogTest {
    @Test
    fun `progress unlocks the required path while optional columns stay independent`() {
        val first = level(id = "I-01", row = 0)
        val optional = level(
            id = "I-02",
            row = 1,
            column = LevelColumn.Left,
            prerequisites = setOf(first.id),
        )
        val current = level(id = "I-03", row = 1, prerequisites = setOf(first.id))
        val future = level(id = "I-04", row = 2, prerequisites = setOf(current.id))
        val firstTier = LevelTier("I", "Foundation", listOf(first, optional, current, future))
        val catalog = LevelCatalog(
            LevelCatalog.CURRENT_SCHEMA_VERSION,
            listOf(firstTier, LevelTier("II", "Control", emptyList())),
        )
        val progress = LearningProgress(
            levels = mapOf(first.id to LevelProgress(completed = true)),
        )

        assertEquals(current, progress.currentLevel(firstTier, tierUnlocked = true))
        assertEquals(LevelNodeState.Completed, progress.stateOf(first, firstTier, tierUnlocked = true))
        assertEquals(LevelNodeState.Available, progress.stateOf(optional, firstTier, tierUnlocked = true))
        assertEquals(LevelNodeState.Current, progress.stateOf(current, firstTier, tierUnlocked = true))
        assertEquals(LevelNodeState.Locked, progress.stateOf(future, firstTier, tierUnlocked = true))
        assertFalse(progress.isTierUnlocked(catalog, tierIndex = 1))

        val requiredPathComplete = progress.copy(
            levels = progress.levels +
                (current.id to LevelProgress(completed = true)) +
                (future.id to LevelProgress(completed = true)),
        )
        assertTrue(requiredPathComplete.isTierUnlocked(catalog, tierIndex = 1))
    }

    @Test
    fun `catalog rejects invalid map and rank definitions`() {
        val checkpoint = level(
            id = "I-01",
            row = 0,
            column = LevelColumn.Left,
            role = LevelRole.Checkpoint,
        )
        val center = level(id = "I-02", row = 0)
        val catalog = LevelCatalog(
            schemaVersion = LevelCatalog.CURRENT_SCHEMA_VERSION,
            tiers = listOf(LevelTier("I", "Foundation", listOf(checkpoint, center))),
        )

        assertThrows(IllegalArgumentException::class.java) { catalog.validated() }
    }

    @Test
    fun `optional level may branch from center on the same row`() {
        val previous = level(id = "I-01", row = 0)
        val center = level(id = "I-02", row = 1, prerequisites = setOf(previous.id))
        val optional = level(
            id = "I-03",
            row = 1,
            column = LevelColumn.Right,
            prerequisites = setOf(center.id),
        )
        val catalog = LevelCatalog(
            schemaVersion = LevelCatalog.CURRENT_SCHEMA_VERSION,
            tiers = listOf(LevelTier("I", "Foundation", listOf(previous, center, optional))),
        )

        catalog.validated()
    }

    @Test
    fun `practice grid keeps single-hand steps and accents`() {
        val level = level(
            id = "I-01",
            row = 0,
            labels = "RLRRLRLL",
            accents = setOf(0, 4),
        )

        val row = level.toPracticeGrid().rows.single()

        assertEquals("RLRRLRLL", row.beats.joinToString("") { it.hand.label })
        assertEquals(
            listOf(
                BeatState.Accent,
                BeatState.Normal,
                BeatState.Normal,
                BeatState.Normal,
                BeatState.Accent,
                BeatState.Normal,
                BeatState.Normal,
                BeatState.Normal,
            ),
            row.beats.map { it.state },
        )
        assertEquals(Hand.Left, row.beats.last().hand)
    }

    @Test
    fun `unison is one unsupported BeatGrid position with both hands`() {
        val unison = level(
            id = "I-01",
            row = 0,
            type = LevelType.Unison,
            pattern = listOf(PatternStep(setOf(PatternHand.Right, PatternHand.Left), accent = true)),
        )
        val catalog = LevelCatalog(
            schemaVersion = LevelCatalog.CURRENT_SCHEMA_VERSION,
            tiers = listOf(LevelTier("I", "Foundation", listOf(unison))),
        )

        catalog.validated()

        assertFalse(unison.supportsBeatGrid)
        assertEquals("RL", unison.pattern.single().label)
        assertThrows(IllegalArgumentException::class.java) { unison.toPracticeGrid() }
    }

    private fun level(
        id: String,
        row: Int,
        column: LevelColumn = LevelColumn.Center,
        role: LevelRole = LevelRole.Lesson,
        type: LevelType = LevelType.Steady,
        modifiers: Set<LevelModifier> = emptySet(),
        prerequisites: Set<String> = emptySet(),
        labels: String = "RLRL",
        accents: Set<Int> = emptySet(),
        pattern: List<PatternStep> = labels.mapIndexed { index, label ->
            PatternStep(
                hands = setOf(PatternHand.fromStorageName(label.toString())),
                accent = index in accents,
            )
        },
    ): Level = Level(
        id = id,
        row = row,
        column = column,
        role = role,
        type = type,
        modifiers = modifiers,
        title = "Test level",
        description = "Test description",
        pattern = pattern,
        leadHand = LeadHand.Right,
        rankTargets = PracticeRank.entries.mapIndexed { index, rank ->
            RankTarget(rank = rank, bpm = 60 + index * 20, repetitions = 10 + index * 2)
        },
        prerequisiteIds = prerequisites,
    )
}
