package com.rudimentor.app.data.levels

import com.rudimentor.app.audio.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCatalogTest {
    @Test
    fun `rows follow the prerequisite chain`() {
        val catalog = catalog(
            lessons = listOf(lesson("f.ST-01"), lesson("f.ST-02"), lesson("f.ST-03")),
            nodes = listOf(
                node("f.ST-01"),
                node("f.ST-02", prerequisites = setOf("f.ST-01")),
                node("f.ST-03", prerequisites = setOf("f.ST-02")),
            ),
        )

        assertEquals(listOf(0, 1, 2), catalog.levels.map(Level::row))
        assertEquals(2, catalog.lastRow)
    }

    @Test
    fun `progress unlocks the required path while optional columns stay independent`() {
        val catalog = catalog(
            lessons = listOf(lesson("f.ST-01"), lesson("f.ST-02"), lesson("f.ST-03"), lesson("f.ST-04")),
            nodes = listOf(
                node("f.ST-01"),
                node("f.ST-02", column = LevelColumn.Left, prerequisites = setOf("f.ST-01")),
                node("f.ST-03", prerequisites = setOf("f.ST-01")),
                node("f.ST-04", prerequisites = setOf("f.ST-03")),
            ),
        )
        val first = catalog.level("f.ST-01")!!
        val optional = catalog.level("f.ST-02")!!
        val current = catalog.level("f.ST-03")!!
        val future = catalog.level("f.ST-04")!!
        val progress = LearningProgress(levels = mapOf(first.id to LevelProgress(completed = true)))

        assertEquals(current, progress.currentLevel(catalog))
        assertEquals(LevelNodeState.Completed, progress.stateOf(first, catalog))
        assertEquals(LevelNodeState.Available, progress.stateOf(optional, catalog))
        assertEquals(LevelNodeState.Current, progress.stateOf(current, catalog))
        assertEquals(LevelNodeState.Locked, progress.stateOf(future, catalog))
        assertFalse(progress.isFamilyComplete(catalog))

        val complete = progress.copy(
            levels = progress.levels +
                (current.id to LevelProgress(completed = true)) +
                (future.id to LevelProgress(completed = true)),
        )
        assertTrue(complete.isFamilyComplete(catalog))
    }

    @Test
    fun `catalog rejects a row without a required center level`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                lessons = listOf(lesson("f.ST-01"), lesson("f.ST-02")),
                nodes = listOf(node("f.ST-01", column = LevelColumn.Left), node("f.ST-02", column = LevelColumn.Right)),
            )
        }
    }

    @Test
    fun `catalog rejects a lesson outside the family namespace`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(lessons = listOf(lesson("other.ST-01")), nodes = listOf(node("other.ST-01")))
        }
    }

    @Test
    fun `catalog rejects a prerequisite cycle`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                lessons = listOf(lesson("f.ST-01"), lesson("f.ST-02")),
                nodes = listOf(
                    node("f.ST-01", prerequisites = setOf("f.ST-02")),
                    node("f.ST-02", prerequisites = setOf("f.ST-01")),
                ),
            )
        }
    }

    @Test
    fun `practice grid keeps the hand order of the pattern`() {
        val level = catalog(
            lessons = listOf(lesson("f.ST-01", hands = "RLRRLRLL")),
            nodes = listOf(node("f.ST-01")),
        ).levels.single()

        val row = level.toPracticeGrid().rows.single()

        assertEquals("RLRRLRLL", row.beats.joinToString("") { it.hand.label })
        assertEquals(Hand.Left, row.beats.last().hand)
    }

    @Test
    fun `unison is one unsupported BeatGrid position with both hands`() {
        val unison = catalog(
            lessons = listOf(
                lesson(
                    id = "f.ST-01",
                    type = LevelType.Unison,
                    steps = listOf(PatternStep(setOf(PatternHand.Right, PatternHand.Left))),
                ),
            ),
            nodes = listOf(node("f.ST-01")),
        ).levels.single()

        assertFalse(unison.supportsBeatGrid)
        assertEquals("RL", unison.pattern.single().label)
        assertThrows(IllegalArgumentException::class.java) { unison.toPracticeGrid() }
    }

    private fun catalog(lessons: List<Lesson>, nodes: List<MapNode>): LevelCatalog = LevelCatalog.build(
        schemaVersion = LevelCatalog.CURRENT_SCHEMA_VERSION,
        family = Family(id = "f", name = "Family", description = "Description."),
        lessons = lessons,
        nodes = nodes,
    )

    private fun lesson(
        id: String,
        type: LevelType = LevelType.Steady,
        modifiers: Set<LevelModifier> = emptySet(),
        hands: String = "RL",
        steps: List<PatternStep> = hands.map { label ->
            PatternStep(hands = setOf(PatternHand.fromStorageName(label.toString())))
        },
    ): Lesson = Lesson(
        id = id,
        type = type,
        modifiers = modifiers,
        pattern = Pattern(mode = PatternMode.Repeat, steps = steps),
        technique = Technique(strokeStyle = "full_rebound", dynamics = "even", accents = "none"),
        execution = Execution(beatCount = 64),
        rankTargets = PracticeRank.entries.mapIndexed { index, rank ->
            RankTarget(rank = rank, bpm = 90, hitsPerBeat = 1 shl index)
        },
    )

    private fun node(
        lessonId: String,
        column: LevelColumn = LevelColumn.Center,
        prerequisites: Set<String> = emptySet(),
    ): MapNode = MapNode(lessonId = lessonId, column = column, prerequisites = prerequisites)
}
