package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.levels.AssetCourseLoader
import com.rudimentor.app.data.levels.CompletionMode
import com.rudimentor.app.data.levels.Execution
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelColumn
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PracticeLoopTest {
    @Test
    fun `all 390 authored configurations repeat their complete sequence through distant seams`() {
        val course = AssetCourseLoader(RuntimeEnvironment.getApplication().assets).load()
        val levels = course.catalogs.values.flatMap { it.levels }
        assertEquals(130, levels.size)
        var configurations = 0
        levels.forEach { level ->
            level.rankTargets.forEach { target ->
                configurations += 1
                val loop = PracticeLoop(level, target.rank, target.bpm)
                val notes = buildPracticeNotes(level, target.rank, target.bpm)
                val bpms = attemptBeatBpms(level, target.rank, target.bpm)
                val expectedPeriod = bpms.sumOf { 60_000.0 / it }
                val label = "${level.id}/${target.rank}"
                assertEquals(label, expectedPeriod, loop.cycleDurationMs, 0.000001)
                assertEquals(label, attemptCountInBeats(level, target.rank, target.bpm) * (60_000.0 / bpms.first()), loop.countInMs, 0.000001)
                notes.forEach { note ->
                    val due = loop.noteTimeMs(LoopNoteId(0, note.index))
                    // The finite builder accumulates Float; free practice retimes in Double.
                    assertEquals(label, note.timeMs.toDouble(), due, 8.0)
                    assertTrue(label, due >= loop.countInMs)
                    assertTrue(label, due < loop.countInMs + loop.cycleDurationMs)
                }
                for (cycle in listOf(1L, 3L, 1_000_001L)) {
                    val lastId = LoopNoteId(cycle - 1, notes.lastIndex)
                    val firstId = LoopNoteId(cycle, 0)
                    val last = loop.noteTimeMs(lastId)
                    val first = loop.noteTimeMs(firstId)
                    val before = if (notes.size == 1) expectedPeriod else first - last
                    assertTrue(label, before > 0)
                    assertTrue(label, loop.windows.forNote(0).okMs <= before * 0.450001)
                    assertTrue(label, loop.windows.forNote(notes.lastIndex).okMs <= before * 0.450001)
                    assertTrue(label, loop.registerHit(last + 5) is HitOutcome.Judged)
                    val hit = loop.registerHit(first - 5) as HitOutcome.Judged
                    assertEquals(label, 0, hit.noteIndex)
                    assertEquals(label, -5f, hit.judgement.offsetMs, 0.001f)
                    loop.advance(first + 25)
                    val view = loop.renderView(first, 300.0, 300.0)
                    val rendered = view.notes.single { it.id == firstId }
                    assertEquals(label, notes.first().copy(timeMs = rendered.note.timeMs), rendered.note)
                    assertNotNull(label, rendered.judgement)
                    assertTrue(label, view.countInTimesMs.isEmpty())
                    assertTrue(label, view.notes.size < 32)
                }
                assertEquals(label, 6L, loop.hitCount)
            }
        }
        assertEquals(390, configurations)
    }

    @Test
    fun `initial and trailing rests belong to the period not to count-in or last note`() {
        val loop = loop(level(hands = "_R__L_", beats = 6))
        assertEquals(6_000.0, loop.cycleDurationMs, 0.000001)
        assertEquals(loop.countInMs + 1_000.0, loop.noteTimeMs(LoopNoteId(0, 0)), 0.000001)
        assertEquals(loop.countInMs + 4_000.0, loop.noteTimeMs(LoopNoteId(0, 1)), 0.000001)
        assertEquals(loop.countInMs + 7_000.0, loop.noteTimeMs(LoopNoteId(1, 0)), 0.000001)
        assertNull(loop.registerHit(loop.countInMs - 1))
        assertTrue(loop.registerHit(loop.countInMs + 200) is HitOutcome.Extra)
        assertTrue(loop.registerHit(loop.noteTimeMs(LoopNoteId(1, 0))) is HitOutcome.Judged)
        assertEquals(2L, loop.hitCount)
    }

    @Test
    fun `single note uses its own circular period as both neighbours`() {
        val loop = loop(level(hands = "R", beats = 1, bpm = 240))
        assertEquals(250.0, loop.cycleDurationMs, 0.0)
        assertEquals(HitWindows.forMinInterval(250f), loop.windows.forNote(0))
        assertTrue(loop.windows.forNote(0).okMs < HitWindows.Default.okMs)
        val next = LoopNoteId(1, 0)
        assertTrue(loop.registerHit(loop.noteTimeMs(next) - 20) is HitOutcome.Judged)
        loop.advance(loop.noteTimeMs(next) + 10)
        assertNotNull(loop.judgementAt(next))
        assertNull(loop.judgementAt(LoopNoteId(0, 0)))
    }

    @Test
    fun `circular windows include dense side of the seam`() {
        val level = level(hands = "RLL", beats = 2).let {
            it.copy(lesson = it.lesson.copy(
                type = LevelType.SubdivisionSwitch,
                midCycleSwitch = true,
                rankTargets = listOf(it.target(PracticeRank.Practice).copy(
                    subdivisionPlan = SubdivisionPlan(1, listOf(1, 4)),
                )),
            ))
        }
        val loop = loop(level)
        assertEquals(HitWindows.forMinInterval(250f), loop.windows.forNote(0))
        assertEquals(HitWindows.forMinInterval(250f), loop.windows.forNote(4))
        val view = loop.renderView(loop.countInMs + 1_000, 1_001.0, 1_001.0)
        assertEquals("RLLRLR", view.notes.joinToString("") { it.note.hand.storageName })
        assertTrue(view.notes.single { it.id == LoopNoteId(0, 1) }.note.densityStart)
    }

    @Test
    fun `ramp repeats every authored pass and does not extend the final tempo`() {
        val level = level(beats = 3).let {
            it.copy(lesson = it.lesson.copy(
                type = LevelType.TempoRamp,
                rankTargets = listOf(it.target(PracticeRank.Practice).copy(
                    tempoRampPlan = TempoRampPlan(
                        mode = "step",
                        direction = "up",
                        phases = listOf(TempoRampPhase(60, 2), TempoRampPhase(120, 1)),
                        repeatCount = 2,
                    ),
                )),
            ))
        }
        val loop = loop(level)
        assertEquals(5_000.0, loop.cycleDurationMs, 0.000001)
        val seam = loop.countInMs + loop.cycleDurationMs
        val beats = loop.renderView(seam, 501.0, 1_501.0).beats
        assertEquals(listOf(5L, 6L, 7L), beats.map { it.index })
        assertEquals(listOf(-500f, 0f, 1_000f), beats.map { it.timeMs })
        assertTrue(beats.none { it.countIn })
        assertEquals(6, buildPracticeNotes(level, PracticeRank.Practice, 60).size)
    }

    @Test
    fun `timed cycle with 150 beats keeps the global four beat accent`() {
        val level = level(hands = "RRLL", bpm = 75, density = 2).let {
            it.copy(lesson = it.lesson.copy(
                execution = Execution(durationSeconds = 120, completionMode = CompletionMode.CompletePatternCycle),
            ))
        }
        val loop = loop(level)
        assertEquals(120_000.0, loop.cycleDurationMs, 0.000001)
        val seam = loop.countInMs + loop.cycleDurationMs
        val beats = loop.renderView(seam, 801.0, 1_601.0).beats
        assertEquals(listOf(149L, 150L, 151L, 152L), beats.map { it.index })
        assertEquals(listOf(false, false, false, true), beats.map { it.strong })
        assertTrue(loop.renderView(seam, 2_000.0, 2_000.0).countInTimesMs.isEmpty())
    }

    @Test
    fun `an early hit across the seam is neither discarded nor judged twice`() {
        val loop = loop(level(beats = 2))
        val id = LoopNoteId(1, 0)
        val due = loop.noteTimeMs(id)
        val first = loop.registerHit(due - 20) as HitOutcome.Judged
        assertEquals(0, first.noteIndex)
        loop.advance(due + 10)
        assertEquals(first.judgement, loop.judgementAt(id))
        assertTrue(loop.registerHit(due + 40) is HitOutcome.Extra)
        assertEquals(2L, loop.hitCount)
        assertEquals(listOf(-20f), loop.offsets)
    }

    @Test
    fun `poll pruning keeps delayed latency-compensated hits eligible`() {
        val loop = loop(level(beats = 1))
        val id = LoopNoteId(30, 0)
        val due = loop.noteTimeMs(id)
        loop.advance(due + 1_200)
        val hit = loop.registerHit(due - 10) as HitOutcome.Judged
        assertEquals(-10f, hit.judgement.offsetMs, 0.001f)
        assertNotNull(loop.judgementAt(id))
        loop.advance(due + 1_300)
        assertNotNull(loop.judgementAt(id))
    }

    @Test
    fun `a late last note can be judged after a seam with a long initial rest`() {
        val loop = loop(level(hands = "_".repeat(19) + "R", beats = 5, density = 4))
        val previous = LoopNoteId(0, 0)
        val seam = loop.countInMs + loop.cycleDurationMs
        val hit = loop.registerHit(seam + 50) as HitOutcome.Judged
        assertEquals(300f, hit.judgement.offsetMs, 0.001f)
        assertNotNull(loop.judgementAt(previous))
        assertNull(loop.judgementAt(LoopNoteId(1, 0)))
    }

    @Test
    fun `a wide single note window retains an early hit beyond the usual history horizon`() {
        val loop = loop(level(hands = "R" + "_".repeat(299), beats = 300))
        val next = LoopNoteId(1, 0)
        val due = loop.noteTimeMs(next)
        assertNull(loop.registerHit(-1.0))
        assertTrue(loop.registerHit(due - 10_000) is HitOutcome.Judged)
        loop.advance(due + 1)
        assertNotNull(loop.judgementAt(next))
        assertTrue(loop.registerHit(due + 100) is HitOutcome.Extra)
        loop.advance(due + loop.windows.forNote(0).okMs + 1)
        assertNull(loop.judgementAt(next))
    }

    @Test
    fun `recent offset snapshots are immutable for the deviation scale`() {
        val loop = loop(level())
        loop.registerHit(loop.countInMs + 5)
        val previous = loop.offsets
        loop.registerHit(loop.countInMs + 1_000 - 5)
        assertEquals(listOf(5f), previous)
        assertEquals(listOf(5f, -5f), loop.offsets)
    }

    @Test
    fun `count-in occurs once and ignored hits do not poison the first note debounce`() {
        val loop = loop(level())
        assertTrue(loop.renderView(0.0, 0.0, 5_000.0).countInTimesMs.isNotEmpty())
        assertNull(loop.registerHit(500.0))
        assertNull(loop.registerHit(Double.NaN))
        assertNull(loop.registerHit(Double.POSITIVE_INFINITY))
        assertEquals(0L, loop.hitCount)
        val firstWindow = loop.windows.forNote(0).okMs
        assertNull(loop.registerHit(loop.countInMs - firstWindow - 1))
        assertTrue(loop.registerHit(loop.countInMs - firstWindow + 1) is HitOutcome.Judged)
        assertEquals(1L, loop.hitCount)
        val seam = loop.countInMs + loop.cycleDurationMs
        assertTrue(loop.renderView(seam, 100.0, 100.0).countInTimesMs.isEmpty())
    }

    @Test
    fun `silence creates no judgements and arbitrary hits remain visible without penalties`() {
        val loop = loop(level(beats = 2))
        val now = loop.countInMs + 60_250.0
        loop.advance(now)
        assertEquals(0L, loop.hitCount)
        assertTrue(loop.offsets.isEmpty())
        val silent = loop.renderView(now, 1_000.0, 1_000.0)
        assertTrue(silent.notes.all { it.judgement == null })
        assertTrue(loop.registerHit(now) is HitOutcome.Extra)
        assertTrue(loop.registerHit(now + 10) is HitOutcome.Debounced)
        assertEquals(1L, loop.hitCount)
        assertEquals(listOf(0f), loop.renderView(now, 1_000.0, 1_000.0).extrasMs)
    }

    @Test
    fun `an hour of dense playing has bounded history and frame size`() {
        val loop = loop(level(hands = "RL", beats = 1, bpm = 240, density = 4))
        val strokes = 57_600
        repeat(strokes) { stroke ->
            val due = loop.countInMs + stroke * 62.5
            val hit = loop.registerHit(due + 0.125) as HitOutcome.Judged
            assertEquals(0.125f, hit.judgement.offsetMs, 0.00001f)
            assertTrue(loop.registerHit(due + 31.375) is HitOutcome.Extra)
            loop.advance(due + 32)
            assertTrue(loop.retainedHitCount <= 2 * PracticeLoop.MAX_RECENT_HITS)
            assertTrue(loop.offsets.size <= PracticeScoring.RECENT_OFFSETS)
            if (stroke % 1_000 == 0) {
                val view = loop.renderView(due + 32, 500.0, 1_000.0)
                assertTrue(view.notes.size <= 25)
                assertTrue(view.extrasMs.size <= 17)
                assertTrue(view.notes.all { abs(it.note.timeMs) <= 1_000 })
            }
        }
        assertEquals(strokes * 2L, loop.hitCount)
        val future = loop.countInMs + 36_000_000.0
        loop.advance(future)
        assertEquals(0, loop.retainedHitCount)
        assertTrue(loop.renderView(future, 500.0, 1_000.0).notes.size <= 25)
        assertTrue(loop.registerHit(future + 0.125) is HitOutcome.Judged)
    }

    @Test
    fun `cycle identity exceeds Int and local Float rendering retains submillisecond detail`() {
        val loop = loop(level(hands = "R", beats = 1, bpm = 240))
        val id = LoopNoteId(3_000_000_000L, 0)
        val now = loop.noteTimeMs(id) + 0.125
        val hit = loop.registerHit(now) as HitOutcome.Judged
        assertEquals(0, hit.noteIndex)
        assertEquals(0.125f, hit.judgement.offsetMs, 0.0f)
        val rendered = loop.renderView(now, 100.0, 100.0).notes.single()
        assertEquals(id, rendered.id)
        assertEquals(-0.125f, rendered.note.timeMs, 0.0f)
        assertFalse(rendered.judgement?.window == HitWindow.Miss)
        assertNull(loop.registerHit(now - 10_000))
    }

    private fun loop(level: Level) = PracticeLoop(level, PracticeRank.Practice, level.target(PracticeRank.Practice).bpm)

    private fun level(hands: String = "RL", beats: Int = 4, bpm: Int = 60, density: Int = 1): Level {
        val lesson = Lesson(
            id = "f.ST-01",
            type = LevelType.Steady,
            modifiers = emptySet(),
            pattern = Pattern(PatternMode.Repeat, hands.map {
                PatternStep(if (it == '_') emptySet() else setOf(PatternHand.fromStorageName(it.toString())))
            }),
            technique = Technique("full_rebound", "even", "none"),
            execution = Execution(beatCount = beats),
            rankTargets = listOf(RankTarget(PracticeRank.Practice, bpm, density)),
        )
        return Level(lesson, MapNode(lesson.id, LevelColumn.Center, emptySet()), row = 0)
    }
}
