package com.rudimentor.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeScoringTest {
    @Test
    fun `the windows are symmetric and closed at their edge`() {
        assertEquals(HitWindow.Perfect, PracticeScoring.window(0f))
        assertEquals(HitWindow.Perfect, PracticeScoring.window(-PracticeScoring.PERFECT_MS))
        assertEquals(HitWindow.Good, PracticeScoring.window(PracticeScoring.PERFECT_MS + 1f))
        assertEquals(HitWindow.Good, PracticeScoring.window(-PracticeScoring.GOOD_MS))
        assertEquals(HitWindow.Ok, PracticeScoring.window(PracticeScoring.OK_MS))
        assertEquals(HitWindow.Miss, PracticeScoring.window(PracticeScoring.OK_MS + 1f))
    }

    @Test
    fun `the combo multiplier grows slowly and stops at double`() {
        assertEquals(1f, PracticeScoring.comboMultiplier(0), 0.001f)
        assertEquals(1.2f, PracticeScoring.comboMultiplier(10), 0.001f)
        assertEquals(2f, PracticeScoring.comboMultiplier(50), 0.001f)
        assertEquals(2f, PracticeScoring.comboMultiplier(500), 0.001f)
    }

    @Test
    fun `stars follow accuracy and the verdict is always milliseconds`() {
        assertEquals(3, PracticeScoring.stars(0.96f))
        assertEquals(2, PracticeScoring.stars(0.85f))
        assertEquals(1, PracticeScoring.stars(0.4f))
        assertEquals("+12 ms", PracticeScoring.verdictLabel(11.6f))
        assertEquals("-12 ms", PracticeScoring.verdictLabel(-11.6f))
        assertEquals("0 ms", PracticeScoring.verdictLabel(0f))
    }

    @Test
    fun `histogram bins span the scale and drop what is out of range`() {
        assertEquals(0, PracticeScoring.histogramBin(-PracticeScoring.SCALE_MS))
        assertEquals(
            PracticeScoring.HISTOGRAM_BINS - 1,
            PracticeScoring.histogramBin(PracticeScoring.SCALE_MS),
        )
        assertNull(PracticeScoring.histogramBin(PracticeScoring.SCALE_MS + 1f))
    }

    @Test
    fun `a clean run scores every note, keeps the combo and passes`() {
        val notes = notesEvery(count = 8, spacingMs = 500f)
        val attempt = PracticeAttempt(notes)
        notes.forEach { note -> attempt.registerHit(note.timeMs) }

        val result = attempt.result()
        assertEquals(8, result.perfect)
        assertEquals(0, result.misses)
        assertEquals(8, attempt.maxCombo)
        assertEquals(1f, result.accuracy, 0.001f)
        assertEquals(3, result.stars)
        assertTrue(result.passed)
    }

    @Test
    fun `a note whose window has passed becomes a miss and breaks the combo`() {
        val notes = notesEvery(count = 3, spacingMs = 500f)
        val attempt = PracticeAttempt(notes)
        attempt.registerHit(notes[0].timeMs)
        assertEquals(1, attempt.combo)

        // Past the second note by more than the OK window.
        attempt.expireMissedNotes(notes[1].timeMs + PracticeScoring.OK_MS + 1f)
        assertEquals(1, attempt.misses)
        assertEquals(0, attempt.combo)
        assertEquals(HitWindow.Miss, attempt.judgementAt(1)?.window)
    }

    @Test
    fun `a hit that belongs to no note is an extra and costs score and accuracy`() {
        val notes = notesEvery(count = 2, spacingMs = 1000f)
        val attempt = PracticeAttempt(notes)
        notes.forEach { note -> attempt.registerHit(note.timeMs) }
        val cleanScore = attempt.score

        attempt.registerHit(notes[1].timeMs + 400f)
        assertTrue(attempt.score < cleanScore)
        assertEquals(0, attempt.combo)

        val result = attempt.result()
        assertEquals(1, result.extras)
        assertEquals(0.75f, result.accuracy, 0.001f)
        assertFalse(result.passed)
    }

    private fun notesEvery(count: Int, spacingMs: Float): List<PracticeNote> =
        List(count) { index ->
            PracticeNote(
                index = index,
                hand = if (index % 2 == 0) {
                    com.rudimentor.app.data.levels.PatternHand.Right
                } else {
                    com.rudimentor.app.data.levels.PatternHand.Left
                },
                timeMs = 1000f + index * spacingMs,
            )
        }
}
