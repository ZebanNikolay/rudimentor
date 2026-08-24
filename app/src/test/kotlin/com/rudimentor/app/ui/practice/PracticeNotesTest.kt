package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.levels.Execution
import com.rudimentor.app.data.levels.LevelColumn
import com.rudimentor.app.data.levels.LevelType
import com.rudimentor.app.data.levels.Lesson
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.MapNode
import com.rudimentor.app.data.levels.Pattern
import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.data.levels.PatternMode
import com.rudimentor.app.data.levels.PatternStep
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.RankTarget
import com.rudimentor.app.data.levels.Technique
import com.rudimentor.app.data.levels.TransitionPhase
import com.rudimentor.app.data.levels.TransitionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The note list of an attempt: how a one-pattern level and a phase level are laid out on the
 * same even grid (decision 141).
 */
class PracticeNotesTest {
    @Test
    fun `a one-pattern level repeats its sticking over the whole attempt`() {
        val level = level(lesson(hands = "RL", beatCount = 8))
        val notes = buildPracticeNotes(level, PracticeRank.Practice, bpm = 60)

        // 8 beats at one hit per beat: Practice is the entry density.
        assertEquals(8, notes.size)
        assertEquals("RLRLRLRL", notes.hands())
        assertTrue(notes.none(PracticeNote::phaseStart))
        assertTrue(notes.all { it.phaseIndex == 0 })
        // Count-in first, then an even grid of whole beats at 60 BPM.
        assertEquals(PracticeScoring.COUNT_IN_BEATS * 1000f, notes.first().timeMs, 0.01f)
        assertEquals(1000f, notes[1].timeMs - notes[0].timeMs, 0.01f)
    }

    @Test
    fun `a phase level walks its blocks and repeats the chain`() {
        val level = level(
            lesson(hands = "RL", beatCount = 16).copy(
                type = LevelType.Transition,
                pattern = null,
                transitionPlan = TransitionPlan(
                    repeatCount = 2,
                    phases = listOf(
                        TransitionPhase(beatCount = 2, pattern = pattern("R")),
                        TransitionPhase(beatCount = 2, pattern = pattern("RL")),
                        TransitionPhase(beatCount = 2, pattern = pattern("L")),
                        TransitionPhase(beatCount = 2, pattern = pattern("LR")),
                    ),
                ),
            ),
        )
        // One hit per beat keeps the arithmetic readable: two beats per block.
        val notes = buildPracticeNotes(level, PracticeRank.Practice, bpm = 60)

        assertEquals(16, notes.size)
        assertEquals("RRRLLLLRRRRLLLLR", notes.hands())
        // Each block starts its own sticking, and the chain of four blocks runs twice.
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14), notes.filter(PracticeNote::phaseStart).map(PracticeNote::index))
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 0, 0, 1, 1, 2, 2, 3, 3), notes.map(PracticeNote::phaseIndex))
        // The grid stays even across a switch: one beat between two notes at 60 BPM.
        assertTrue(notes.zipWithNext().all { (a, b) -> abs(b.timeMs - a.timeMs - 1000f) < 0.01f })
    }

    @Test
    fun `the density of the rank fills every block`() {
        val level = level(
            lesson(hands = "RL", beatCount = 8).copy(
                type = LevelType.Transition,
                pattern = null,
                transitionPlan = TransitionPlan(
                    repeatCount = 1,
                    phases = listOf(
                        TransitionPhase(beatCount = 2, pattern = pattern("RRLL")),
                        TransitionPhase(beatCount = 2, pattern = pattern("RLLR")),
                    ),
                ),
            ),
        )
        // Groove doubles the entry density: two notes per beat instead of one.
        val notes = buildPracticeNotes(level, PracticeRank.Groove, bpm = 120)

        // Two blocks of two beats at two notes per beat.
        assertEquals(8, notes.size)
        assertEquals("RRLLRLLR", notes.hands())
        assertEquals(listOf(0, 4), notes.filter(PracticeNote::phaseStart).map(PracticeNote::index))
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1), notes.map(PracticeNote::phaseIndex))
    }

    private fun List<PracticeNote>.hands(): String = joinToString("") {
        if (it.hand == PatternHand.Right) "R" else "L"
    }

    private fun pattern(hands: String) = Pattern(
        mode = PatternMode.Repeat,
        steps = hands.map { PatternStep(hands = setOf(PatternHand.fromStorageName(it.toString()))) },
    )

    private fun lesson(hands: String, beatCount: Int) = Lesson(
        id = "f.ST-01",
        type = LevelType.Steady,
        modifiers = emptySet(),
        pattern = pattern(hands),
        technique = Technique(strokeStyle = "full_rebound", dynamics = "even", accents = "none"),
        execution = Execution(beatCount = beatCount),
        rankTargets = PracticeRank.entries.mapIndexed { index, rank ->
            RankTarget(rank = rank, bpm = 90, hitsPerBeat = 1 shl index)
        },
    )

    private fun level(lesson: Lesson) = Level(
        lesson = lesson,
        node = MapNode(lessonId = lesson.id, column = LevelColumn.Center, prerequisites = emptySet()),
        row = 0,
    )
}
