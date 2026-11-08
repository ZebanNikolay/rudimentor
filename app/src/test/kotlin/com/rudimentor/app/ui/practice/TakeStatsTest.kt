package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.stats.PlayEvent
import com.rudimentor.app.stats.StatsInstant
import com.rudimentor.app.stats.TakeEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class TakeStatsTest {
    private val at = StatsInstant(0L, ZoneOffset.UTC)
    private val notes = List(8) { index ->
        PracticeNote(
            index = index,
            hand = if (index % 2 == 0) PatternHand.Right else PatternHand.Left,
            timeMs = 1000f + index * 500f,
        )
    }

    private fun attempt() = PracticeAttempt(notes, HitWindows.forMinInterval(500f))

    @Test
    fun `a stopped take counts what was played and nothing it never reached`() {
        val attempt = attempt()
        notes.take(3).forEach { attempt.registerHit(it.timeMs) }

        val event = challengeTakeEvent(
            at, "singles.SI-01", PracticeRank.Practice, attempt, TakeEnd.Stopped,
            positionMs = notes[2].timeMs + 100.0, result = null, variantPlayedBefore = false,
        )

        assertEquals(3, event.perfect)
        assertEquals("unplayed notes are not misses", 0, event.misses)
        assertEquals(8, event.noteCount)
        assertFalse(event.passed)
        assertEquals(0, event.stars)
        assertFalse(event.crown)
        assertEquals(1_100L, event.padMs)
        assertEquals("singles.SI-01:${PracticeRank.Practice.storageName}", event.variantKey)
    }

    @Test
    fun `a completed take carries its result, and its time is bounded by the notes`() {
        val attempt = attempt()
        notes.forEach { attempt.registerHit(it.timeMs) }

        val event = challengeTakeEvent(
            at, "singles.SI-01", PracticeRank.Practice, attempt, TakeEnd.Completed,
            positionMs = 3_600_000.0, result = attempt.result(), variantPlayedBefore = true,
        )

        assertTrue(event.passed)
        assertTrue(event.crown)
        assertEquals(3, event.stars)
        assertEquals(0f, event.medianOffsetMs!!, 0.001f)
        assertEquals(3_500L + 5_000L, event.padMs)
        assertTrue(event.variantPlayedBefore)
    }

    @Test
    fun `a take is handed to the ledger only once`() {
        val tally = TakeTally("singles.SI-01", PracticeRank.Practice, attempt(), loop = null, variantPlayedBefore = false)
        assertNotNull(tally.close(TakeEnd.Abandoned, at = at))
        assertNull(tally.close(TakeEnd.Completed, at = at))
    }

    @Test
    fun `an abandoned take before the first note has no time`() {
        val event = TakeTally("singles.SI-01", PracticeRank.Practice, attempt(), loop = null, variantPlayedBefore = false)
            .apply { positionMs = 400.0 }
            .close(TakeEnd.Abandoned, at = at) as PlayEvent.ChallengeTake
        assertEquals(0L, event.padMs)
        assertEquals(0, event.judgedNotes)
    }

    @Test
    fun `median of offsets`() {
        assertNull(median(emptyList()))
        assertEquals(2f, median(listOf(5f, -1f, 2f))!!, 0f)
        assertEquals(1.5f, median(listOf(4f, -1f, 1f, 2f))!!, 0f)
    }
}
