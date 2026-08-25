package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.levels.CompletionMode
import com.rudimentor.app.data.levels.Execution
import com.rudimentor.app.data.levels.LevelColumn
import com.rudimentor.app.data.levels.LevelType
import com.rudimentor.app.data.levels.Lesson
import com.rudimentor.app.data.levels.LevelModifier
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.MapNode
import com.rudimentor.app.data.levels.Pattern
import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.data.levels.PatternMode
import com.rudimentor.app.data.levels.PatternStep
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.RankTarget
import com.rudimentor.app.data.levels.SubdivisionPlan
import com.rudimentor.app.data.levels.TempoRampPhase
import com.rudimentor.app.data.levels.TempoRampPlan
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

    @Test
    fun `a subdivision switch changes density between blocks and keeps the pulse`() {
        // doubles.SS-01 at Practice: RRLL, 48 beats, blocks of 8 beats at 1 - 2 - 1 hits
        // per beat, one tempo throughout (decision 98).
        val level = level(
            lesson(hands = "RRLL", beatCount = 48).copy(
                type = LevelType.SubdivisionSwitch,
                rankTargets = listOf(
                    RankTarget(
                        rank = PracticeRank.Practice,
                        bpm = 60,
                        hitsPerBeat = 1,
                        subdivisionPlan = SubdivisionPlan(blockBeats = 8, hitsPerBeat = listOf(1, 2, 1)),
                    ),
                ),
            ),
        )
        val notes = buildPracticeNotes(level, PracticeRank.Practice, bpm = 60)

        // The plan cycles: 8 beats x1, 8 x2, 8 x1, then the same three blocks again.
        assertEquals(2 * (8 + 16 + 8), notes.size)
        assertEquals(notes.size, level.noteCount(level.target(PracticeRank.Practice)))
        // The sticking runs through a switch instead of restarting on it.
        assertEquals("RRLL".repeat(notes.size / 4), notes.hands())
        // Every beat of the attempt still lands on the click, switch or not.
        val countInMs = PracticeScoring.COUNT_IN_BEATS * 1000f
        (0..48).forEach { beat ->
            val onBeat = notes.firstOrNull { abs(it.timeMs - (countInMs + beat * 1000f)) < 0.01f }
            assertTrue("beat $beat is not on the grid", beat == 48 || onBeat != null)
        }
        // Inside the dense block the notes sit half a beat apart.
        assertEquals(500f, notes[9].timeMs - notes[8].timeMs, 0.01f)
        // Only a real change of density is marked: the last block of a pass and the first of
        // the next both play one hit per beat, so no mark sits between them.
        assertEquals(
            listOf(8, 24, 40, 56),
            notes.filter(PracticeNote::densityStart).map(PracticeNote::index),
        )
        assertTrue(notes.none(PracticeNote::phaseStart))
    }

    @Test
    fun `a steady level marks no density switch`() {
        val notes = buildPracticeNotes(level(lesson(hands = "RL", beatCount = 8)), PracticeRank.Groove, bpm = 90)

        assertEquals(16, notes.size)
        assertTrue(notes.none(PracticeNote::densityStart))
    }

    @Test
    fun `a tempo ramp plays every phase at its own tempo`() {
        // doubles.RM-01 shaped: 16 beats per pass, 60 -> 120 BPM, two passes per attempt.
        val level = rampLevel()
        val notes = buildPracticeNotes(level, PracticeRank.Practice, bpm = 60)

        assertEquals(32, notes.size)
        val countInMs = PracticeScoring.COUNT_IN_BEATS * 1000f
        // The first phase runs at 60 BPM: whole-second beats after the count-in.
        assertEquals(countInMs, notes[0].timeMs, 0.01f)
        assertEquals(1000f, notes[1].timeMs - notes[0].timeMs, 0.01f)
        // The second phase runs at 120 BPM, so its beats are half as long.
        assertEquals(countInMs + 8000f, notes[8].timeMs, 0.01f)
        assertEquals(500f, notes[9].timeMs - notes[8].timeMs, 0.01f)
        // The pass repeats, so the attempt goes back to the slow phase.
        assertEquals(countInMs + 12_000f, notes[16].timeMs, 0.01f)
        assertEquals(1000f, notes[17].timeMs - notes[16].timeMs, 0.01f)
    }

    @Test
    fun `the tempo plan carries the count-in and follows the chosen tempo`() {
        val level = rampLevel()
        val plan = buildTempoPlan(level, PracticeRank.Practice, bpm = 60)

        // Four count-in beats at the entry tempo, then a beat per beat of the attempt.
        assertEquals(PracticeScoring.COUNT_IN_BEATS + 32, plan.size)
        assertEquals(listOf(60, 60, 60, 60), plan.take(4))
        assertEquals(60, plan[PracticeScoring.COUNT_IN_BEATS])
        assertEquals(120, plan[PracticeScoring.COUNT_IN_BEATS + 8])
        // Picking a faster tempo moves the whole authored shape, keeping its ratio.
        val faster = buildTempoPlan(level, PracticeRank.Practice, bpm = 90)
        assertEquals(90, faster[PracticeScoring.COUNT_IN_BEATS])
        assertEquals(180, faster[PracticeScoring.COUNT_IN_BEATS + 8])
        // A level without a ramp needs no plan: the engine keeps its fixed tempo.
        assertEquals(0, buildTempoPlan(level(lesson(hands = "RL", beatCount = 8)), PracticeRank.Practice, bpm = 60).size)
    }

    @Test
    fun `a timed level plays the duration at the chosen tempo, rounded up to a cycle`() {
        // singles.EN-01 shaped: RL, two minutes, no beat count in the package.
        val level = level(
            lesson(hands = "RL", beatCount = 0).copy(
                modifiers = setOf(LevelModifier.Endurance),
                execution = Execution(
                    durationSeconds = 120,
                    completionMode = CompletionMode.CompletePatternCycle,
                ),
            ),
        )
        val notes = buildPracticeNotes(level, PracticeRank.Practice, bpm = 60)

        // 120 s at 60 bpm is 120 beats, one note per beat at Practice.
        assertEquals(120, notes.size)
        assertEquals(notes.size, level.noteCount(level.target(PracticeRank.Practice), bpm = 60))
        assertEquals("RL".repeat(60), notes.hands())
        // The grid starts after the count-in and the last note lands on the last beat.
        val countInMs = PracticeScoring.COUNT_IN_BEATS * 1000f
        assertEquals(countInMs, notes.first().timeMs, 0.01f)
        assertEquals(countInMs + 119_000f, notes.last().timeMs, 0.01f)
        // A tempo that does not divide the duration evenly plays on to the end of the
        // sticking cycle instead of cutting it short.
        assertEquals(184, buildPracticeNotes(level, PracticeRank.Practice, bpm = 92).size)
    }

    private fun rampLevel() = level(
        lesson(hands = "RRLL", beatCount = 16).copy(
            type = LevelType.TempoRamp,
            rankTargets = listOf(
                RankTarget(
                    rank = PracticeRank.Practice,
                    bpm = 60,
                    hitsPerBeat = 1,
                    tempoRampPlan = TempoRampPlan(
                        mode = "step",
                        direction = "up",
                        phases = listOf(
                            TempoRampPhase(bpm = 60, beatCount = 8),
                            TempoRampPhase(bpm = 120, beatCount = 8),
                        ),
                        repeatCount = 2,
                    ),
                ),
            ),
        ),
    )

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
