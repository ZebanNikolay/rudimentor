package com.rudimentor.app.data.levels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dump exists to explain a level that behaves in a way the level screen does not show,
 * so the test checks that the fields carrying that behaviour are present and that the
 * warnings about the engine appear exactly where the data asks for a plan.
 */
class LevelDebugDumpTest {
    @Test
    fun `a steady level prints its identity, sticking and every rank`() {
        val dump = describeLevel(level(lesson("f.ST-01")), family)

        assertTrue(dump.contains("== f.ST-01 =="))
        assertTrue(dump.contains("type: steady"))
        assertTrue(dump.contains("sticking: R L"))
        assertTrue(dump.contains("lead hand: right"))
        assertTrue(dump.contains("beats: 64"))
        PracticeRank.entries.forEach { rank ->
            assertTrue(dump.contains("-- rank ${rank.storageName} --"))
        }
        // 64 beats x 1 hit per beat at Practice, one phase, one repeat.
        assertTrue(dump.contains("notes per attempt: 64"))
        assertFalse(dump.contains("ENGINE"))
    }

    @Test
    fun `a subdivision switch prints its plan and the density of every block`() {
        val lesson = lesson("f.SS-01", type = LevelType.SubdivisionSwitch).copy(
            execution = Execution(beatCount = 48),
            rankTargets = listOf(
                RankTarget(
                    rank = PracticeRank.Practice,
                    bpm = 60,
                    hitsPerBeat = 1,
                    subdivisionPlan = SubdivisionPlan(blockBeats = 8, hitsPerBeat = listOf(1, 2, 1)),
                ),
            ),
        )

        val dump = describeLevel(level(lesson), family)

        assertTrue(dump.contains("type: subdivision_switch"))
        assertTrue(dump.contains("subdivision plan: blocks of 8 beats at 1 -> 2 -> 1 hits per beat"))
        assertTrue(dump.contains("hits per beat: 1 / 2 (per block)"))
        // Two passes of the three blocks: 8 + 16 + 8 notes each.
        assertTrue(dump.contains("notes per attempt: 64"))
        assertFalse(dump.contains("ENGINE"))
    }

    @Test
    fun `a phased level prints every block instead of one sticking line`() {
        val lesson = lesson("f.TR-01", type = LevelType.Transition).copy(
            pattern = null,
            transitionPlan = TransitionPlan(
                repeatCount = 4,
                phases = listOf(
                    TransitionPhase(beatCount = 8, pattern = pattern("RLRL")),
                    TransitionPhase(beatCount = 8, pattern = pattern("RRLL")),
                ),
            ),
        )

        val dump = describeLevel(level(lesson), family)

        assertTrue(dump.contains("phases: 2 blocks, chain repeats 4x per pass"))
        assertTrue(dump.contains("block 1: 8 beats  R L R L"))
        assertTrue(dump.contains("block 2: 8 beats  R R L L"))
        assertFalse(dump.contains("sticking:"))
        // 4 repeats x (8 + 8) beats x 1 hit per beat at Practice.
        assertTrue(dump.contains("notes per attempt: 64"))
    }

    @Test
    fun `a tempo ramp prints its steps and says the engine holds one tempo`() {
        val lesson = lesson("f.RM-01", type = LevelType.TempoRamp).copy(
            rankTargets = listOf(
                RankTarget(
                    rank = PracticeRank.Practice,
                    bpm = 100,
                    hitsPerBeat = 1,
                    tempoRampPlan = TempoRampPlan(
                        mode = "step_ramp",
                        direction = "open_close_open",
                        phases = listOf(
                            TempoRampPhase(bpm = 100, beatCount = 8),
                            TempoRampPhase(bpm = 110, beatCount = 8),
                        ),
                    ),
                ),
            ),
        )

        val dump = describeLevel(level(lesson), family)

        assertTrue(dump.contains("tempo ramp: step_ramp, open_close_open, repeats 1x"))
        assertTrue(dump.contains("step 1: 100 bpm for 8 beats"))
        assertTrue(dump.contains("ENGINE: plays 100 bpm for the whole attempt"))
    }

    @Test
    fun `map placement and prerequisites are printed for a side level`() {
        val dump = describeLevel(
            level(
                lesson("f.WK-01", modifiers = setOf(LevelModifier.Weak)),
                node = MapNode(
                    lessonId = "f.WK-01",
                    column = LevelColumn.Left,
                    prerequisites = setOf("f.ST-02", "f.ST-01"),
                ),
                row = 3,
            ),
            family,
        )

        assertTrue(dump.contains("modifiers: weak"))
        assertTrue(dump.contains("map: row 3, column left"))
        assertTrue(dump.contains("prerequisites: f.ST-01, f.ST-02"))
    }

    private val family = Family(id = "f", name = "Family", description = "Description.")

    private fun pattern(hands: String) = Pattern(
        mode = PatternMode.Repeat,
        steps = hands.map { PatternStep(hands = setOf(PatternHand.fromStorageName(it.toString()))) },
    )

    private fun lesson(
        id: String,
        type: LevelType = LevelType.Steady,
        modifiers: Set<LevelModifier> = emptySet(),
    ): Lesson = Lesson(
        id = id,
        type = type,
        modifiers = modifiers,
        pattern = pattern("RL"),
        technique = Technique(strokeStyle = "full_rebound", dynamics = "even", accents = "none"),
        execution = Execution(beatCount = 64),
        rankTargets = PracticeRank.entries.mapIndexed { index, rank ->
            RankTarget(rank = rank, bpm = 90, hitsPerBeat = 1 shl index)
        },
    )

    private fun level(
        lesson: Lesson,
        node: MapNode = MapNode(lessonId = lesson.id, column = LevelColumn.Center, prerequisites = emptySet()),
        row: Int = 0,
    ): Level = Level(lesson = lesson, node = node, row = row)
}
