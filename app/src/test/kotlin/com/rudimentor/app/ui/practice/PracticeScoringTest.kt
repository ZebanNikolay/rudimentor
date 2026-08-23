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
    fun `windows are clamped to half the shortest interval`() {
        // Stage at 180 BPM with four hits per beat leaves 83 ms between notes.
        val windows = HitWindows.forMinInterval(83f)
        assertEquals(41.5f, windows.okMs, 0.001f)
        assertEquals(41.5f, windows.goodMs, 0.001f)
        assertEquals(PracticeScoring.PERFECT_MS, windows.perfectMs, 0.001f)
        // A comfortable tempo is left alone.
        assertEquals(HitWindows.Default, HitWindows.forMinInterval(500f))
    }

    @Test
    fun `the third star needs a clean run, not only accuracy`() {
        assertEquals(3, PracticeScoring.stars(0.97f, misses = 0, extras = 0))
        // Same accuracy, one miss: the third star is FULL COMBO and cannot be bought.
        assertEquals(2, PracticeScoring.stars(0.97f, misses = 1, extras = 0))
        assertEquals(2, PracticeScoring.stars(0.97f, misses = 0, extras = 1))
        assertEquals(2, PracticeScoring.stars(0.91f, misses = 0, extras = 0))
        assertEquals(1, PracticeScoring.stars(0.82f, misses = 2, extras = 0))
        assertEquals(0, PracticeScoring.stars(0.79f, misses = 3, extras = 0))
    }

    @Test
    fun `the verdict is always milliseconds`() {
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
        assertTrue(result.fullCombo)
        assertTrue(result.allPerfect)
        assertTrue(result.passed)
    }

    @Test
    fun `a double trigger inside the debounce window is dropped, not charged`() {
        val notes = notesEvery(count = 2, spacingMs = 1000f)
        val attempt = PracticeAttempt(notes)
        notes.forEach { note -> attempt.registerHit(note.timeMs) }
        // The onset detector ringing right after a counted stroke: no extra, no miss.
        attempt.registerHit(notes[1].timeMs + PracticeScoring.DEBOUNCE_MS - 1f)

        val result = attempt.result()
        assertEquals(0, result.extras)
        assertEquals(1f, result.accuracy, 0.001f)
    }

    @Test
    fun `a note whose window has passed becomes a miss and breaks the combo`() {
        val notes = notesEvery(count = 3, spacingMs = 500f)
        val attempt = PracticeAttempt(notes)
        attempt.registerHit(notes[0].timeMs)
        assertEquals(1, attempt.combo)

        // Past the second note by more than the OK window plus the expiry grace.
        attempt.expireMissedNotes(
            notes[1].timeMs + PracticeScoring.OK_MS + PracticeScoring.EXPIRE_GRACE_MS + 1f,
        )
        assertEquals(1, attempt.misses)
        assertEquals(0, attempt.combo)
        assertEquals(HitWindow.Miss, attempt.judgementAt(1)?.window)
    }

    @Test
    fun `the expiry grace keeps a note alive a little past its window`() {
        val notes = notesEvery(count = 3, spacingMs = 500f)
        val attempt = PracticeAttempt(notes)

        // Inside the grace the note is still open, so a late poll cannot steal it.
        attempt.expireMissedNotes(notes[0].timeMs + PracticeScoring.OK_MS + 1f)
        assertEquals(0, attempt.misses)
    }

    @Test
    fun `a hit that belongs to no note grows the denominator`() {
        val notes = notesEvery(count = 2, spacingMs = 1000f)
        val attempt = PracticeAttempt(notes)
        notes.forEach { note -> attempt.registerHit(note.timeMs) }
        assertEquals(1f, attempt.liveAccuracy, 0.001f)

        attempt.registerHit(notes[1].timeMs + 400f)
        assertEquals(0, attempt.combo)

        val result = attempt.result()
        assertEquals(1, result.extras)
        // Two perfect notes over three counted events: 2 / 3.
        assertEquals(0.667f, result.accuracy, 0.001f)
        assertFalse(result.passed)
        assertFalse(result.allPerfect)
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
