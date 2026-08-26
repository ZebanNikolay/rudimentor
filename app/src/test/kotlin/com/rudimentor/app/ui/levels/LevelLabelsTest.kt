package com.rudimentor.app.ui.levels

import com.rudimentor.app.data.levels.Execution
import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelColumn
import com.rudimentor.app.data.levels.LevelModifier
import com.rudimentor.app.data.levels.LevelType
import com.rudimentor.app.data.levels.Lesson
import com.rudimentor.app.data.levels.MapNode
import com.rudimentor.app.data.levels.Pattern
import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.data.levels.PatternMode
import com.rudimentor.app.data.levels.PatternStep
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.RankTarget
import com.rudimentor.app.data.levels.SubdivisionPlan
import com.rudimentor.app.data.levels.Technique
import com.rudimentor.app.data.levels.TempoRampPhase
import com.rudimentor.app.data.levels.TempoRampPlan
import com.rudimentor.app.data.levels.TransitionPhase
import com.rudimentor.app.data.levels.TransitionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The level screen used to title every level after its family, so a whole map read
 * `Single Strokes` (decision 152). The name comes from the track instead, and the tests pin
 * both the naming and the attempt length the screen quotes.
 */
class LevelLabelsTest {
    @Test
    fun `two tracks of one family are named differently`() {
        val steady = level(lesson("singles.ST-07"))
        val triplets = level(lesson("singles.TS-01"))

        assertEquals("Single strokes", steady.title(singles))
        assertEquals("Triplets", triplets.title(singles))
        assertNotEquals(steady.title(singles), triplets.title(singles))
    }

    @Test
    fun `the same track code is named per family where the figure differs`() {
        assertEquals("Single strokes", level(lesson("singles.ST-01")).title(singles))
        assertEquals("Double strokes", level(lesson("doubles.ST-01")).title(doubles))
    }

    @Test
    fun `an unknown track falls back to the kind of lesson`() {
        val level = level(lesson("singles.ZZ-01", type = LevelType.Dynamics))

        assertEquals("Dynamics", level.title(singles))
    }

    @Test
    fun `the subtitle names the map and what the level changes`() {
        val level = level(lesson("singles.EN-01", modifiers = setOf(LevelModifier.Endurance)))

        assertEquals("Singles · Endurance", level.subtitle(singles))
    }

    @Test
    fun `an attempt at a fixed tempo lasts its beats at that tempo`() {
        val level = level(lesson("singles.ST-01"))

        // 64 beats at 120 BPM = 32 s, whatever the density of the beat is.
        assertEquals(32, level.attemptSeconds(level.target(PracticeRank.Practice)))
    }

    @Test
    fun `a ramp attempt counts every phase at its own tempo`() {
        val ramp = TempoRampPlan(
            mode = "linear",
            direction = "up",
            phases = listOf(
                TempoRampPhase(bpm = 60, beatCount = 30),
                TempoRampPhase(bpm = 120, beatCount = 30),
            ),
            repeatCount = 1,
        )
        val level = level(
            lesson("singles.RM-01", type = LevelType.TempoRamp).copy(
                execution = Execution(beatCount = 60),
                rankTargets = listOf(
                    RankTarget(
                        rank = PracticeRank.Practice,
                        bpm = 60,
                        hitsPerBeat = 1,
                        tempoRampPlan = ramp,
                    ),
                ),
            ),
        )

        // 30 beats at 60 BPM = 30 s, 30 beats at 120 BPM = 15 s.
        assertEquals(45, level.attemptSeconds(level.target(PracticeRank.Practice)))
    }

    @Test
    fun `a one-pattern level is one sticking block sized at the rank`() {
        val level = level(lesson("singles.ST-01"))

        // 64 beats at two notes per beat: 128 notes, so the two-step pattern runs 64 times.
        val blocks = level.stickingBlocks(level.target(PracticeRank.Groove))

        assertEquals(1, blocks.size)
        val block = blocks.single()
        assertEquals("RL", block.sticking)
        assertEquals(64, block.beats)
        assertEquals(128, block.notes)
        assertEquals(64, block.cycles)
        assertEquals(2, block.hitsPerBeat)
    }

    @Test
    fun `a phase level lists every block and the passes of the chain`() {
        val level = level(
            lesson("singles.TR-01", type = LevelType.Transition).copy(
                pattern = null,
                transitionPlan = TransitionPlan(
                    repeatCount = 3,
                    phases = listOf(
                        TransitionPhase(beatCount = 8, pattern = pattern("RL")),
                        TransitionPhase(beatCount = 4, pattern = pattern("RRLL")),
                    ),
                ),
            ),
        )
        val target = level.target(PracticeRank.Practice)

        val blocks = level.stickingBlocks(target)

        assertEquals(listOf("RL", "RRLL"), blocks.map { it.sticking })
        assertEquals(listOf(8, 4), blocks.map { it.notes })
        assertEquals(listOf(4, 1), blocks.map { it.cycles })
        assertEquals(3, level.attemptPasses(target))
    }

    @Test
    fun `a sticking longer than four steps is grouped in fours`() {
        val level = level(
            lesson("singles.SP-01").copy(pattern = pattern("RLRRLRLL")),
        )

        assertEquals("RLRR LRLL", level.stickingBlocks(level.target(PracticeRank.Practice)).single().sticking)
    }

    @Test
    fun `a block whose density changes states no single density`() {
        val level = level(
            lesson("singles.SS-01", type = LevelType.SubdivisionSwitch).copy(
                execution = Execution(beatCount = 16),
                rankTargets = listOf(
                    RankTarget(
                        rank = PracticeRank.Practice,
                        bpm = 90,
                        hitsPerBeat = 1,
                        subdivisionPlan = SubdivisionPlan(blockBeats = 8, hitsPerBeat = listOf(1, 2)),
                    ),
                ),
            ),
        )

        val block = level.stickingBlocks(level.target(PracticeRank.Practice)).single()

        // Eight beats of one note plus eight of two: 24 notes, and no one density to name.
        assertEquals(24, block.notes)
        assertEquals(null, block.hitsPerBeat)
        assertEquals(12, block.cycles)
    }

    @Test
    fun `durations read as seconds under a minute and as minutes above it`() {
        assertEquals("32 s", formatSeconds(32))
        assertEquals("1:07", formatSeconds(67))
        assertEquals("2:00", formatSeconds(120))
    }

    private val singles = Family(id = "singles", name = "Singles", description = "Description.")
    private val doubles = Family(id = "doubles", name = "Doubles", description = "Description.")

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
            RankTarget(rank = rank, bpm = 120, hitsPerBeat = 1 shl index)
        },
    )

    private fun pattern(sticking: String): Pattern = Pattern(
        mode = PatternMode.Repeat,
        steps = sticking.map { PatternStep(hands = setOf(PatternHand.fromStorageName(it.toString()))) },
    )

    private fun level(lesson: Lesson): Level = Level(
        lesson = lesson,
        node = MapNode(lessonId = lesson.id, column = LevelColumn.Center, prerequisites = emptySet()),
        row = 0,
    )
}
