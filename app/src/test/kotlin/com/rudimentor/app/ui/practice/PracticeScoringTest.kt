package com.rudimentor.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeScoringTest {
    @Test
    fun `the windows follow the tempo of the level`() {
        // 60 BPM in quarters: the widest windows the model allows.
        val slow = HitWindows.forMinInterval(1000f)
        assertEquals(31f, slow.perfectMs, 0.5f)
        assertEquals(60f, slow.goodMs, 0.5f)
        assertEquals(120f, slow.okMs, 0.5f)

        // 100 BPM in eighths.
        val middle = HitWindows.forMinInterval(300f)
        assertEquals(20f, middle.perfectMs, 0.5f)
        assertEquals(38f, middle.goodMs, 0.5f)
        assertEquals(76f, middle.okMs, 0.5f)

        // Faster notes get tighter windows, never wider ones.
        assertTrue(middle.perfectMs < slow.perfectMs)
        assertTrue(middle.okMs < slow.okMs)
    }

    @Test
    fun `on the fastest levels the OK window is held under half the interval`() {
        // Stage at 180 BPM with four hits per beat leaves 83 ms between notes.
        val windows = HitWindows.forMinInterval(83f)
        assertEquals(83f * PracticeScoring.OK_INTERVAL_SHARE, windows.okMs, 0.01f)
        assertEquals(35f, windows.goodMs, 0.5f)
        // PERFECT never narrows below the noise of the audio path itself.
        assertEquals(18f, windows.perfectMs, 0.5f)
        assertTrue(windows.perfectMs <= windows.goodMs)
        assertTrue(windows.goodMs <= windows.okMs)
    }

    @Test
    fun `the windows are symmetric and closed at their edge`() {
        val windows = HitWindows.forMinInterval(500f)
        assertEquals(HitWindow.Perfect, windows.window(0f))
        assertEquals(HitWindow.Perfect, windows.window(-windows.perfectMs))
        assertEquals(HitWindow.Good, windows.window(windows.perfectMs + 1f))
        assertEquals(HitWindow.Good, windows.window(-windows.goodMs))
        assertEquals(HitWindow.Ok, windows.window(windows.okMs))
        assertEquals(HitWindow.Miss, windows.window(windows.okMs + 1f))
    }

    @Test
    fun `the third star needs a run without a miss, extras act through the number`() {
        assertEquals(3, PracticeScoring.stars(0.90f, misses = 0))
        // Same accuracy, one miss: the third star is FULL COMBO and cannot be bought.
        assertEquals(2, PracticeScoring.stars(0.90f, misses = 1))
        assertEquals(2, PracticeScoring.stars(0.80f, misses = 0))
        assertEquals(1, PracticeScoring.stars(0.65f, misses = 4))
        assertEquals(0, PracticeScoring.stars(0.59f, misses = 9))
    }

    @Test
    fun `the crown asks for three stars and most notes dead on`() {
        assertTrue(PracticeScoring.crown(stars = 3, perfect = 80, noteCount = 100, extras = 1))
        // Three stars but only three quarters of the notes perfect: no crown.
        assertFalse(PracticeScoring.crown(stars = 3, perfect = 75, noteCount = 100, extras = 0))
        // Perfect enough, but too many strokes too many.
        assertFalse(PracticeScoring.crown(stars = 3, perfect = 95, noteCount = 100, extras = 2))
        assertFalse(PracticeScoring.crown(stars = 2, perfect = 95, noteCount = 100, extras = 0))
    }

    @Test
    fun `extra hits are charged up to a share of the note count`() {
        // On a level of 100 notes at most two extras reach the number.
        assertEquals(1f, PracticeScoring.chargedExtras(extras = 1, noteCount = 100), 0.001f)
        assertEquals(2f, PracticeScoring.chargedExtras(extras = 2, noteCount = 100), 0.001f)
        assertEquals(2f, PracticeScoring.chargedExtras(extras = 40, noteCount = 100), 0.001f)
        // The cap keeps a noisy detector from failing an otherwise clean attempt.
        assertEquals(0.98f, PracticeScoring.accuracy(100f, noteCount = 100, extras = 40), 0.001f)
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
    fun `a clean run scores every note, keeps the combo and takes the crown`() {
        val notes = notesEvery(count = 8, spacingMs = 500f)
        val attempt = PracticeAttempt(notes, HitWindows.forMinInterval(500f))
        notes.forEach { note -> attempt.registerHit(note.timeMs) }

        val result = attempt.result()
        assertEquals(8, result.perfect)
        assertEquals(0, result.misses)
        assertEquals(8, attempt.maxCombo)
        assertEquals(1f, result.accuracy, 0.001f)
        assertEquals(3, result.stars)
        assertTrue(result.fullCombo)
        assertTrue(result.crown)
        assertTrue(result.passed)
    }

    @Test
    fun `notes played inside GOOD land in the middle of the scale`() {
        val windows = HitWindows.forMinInterval(500f)
        val notes = notesEvery(count = 10, spacingMs = 500f)
        val attempt = PracticeAttempt(notes, windows)
        notes.forEach { note -> attempt.registerHit(note.timeMs + windows.goodMs - 1f) }

        val result = attempt.result()
        assertEquals(10, result.good)
        // Everything in GOOD is 70 %: passed, two stars, no crown.
        assertEquals(0.7f, result.accuracy, 0.001f)
        assertEquals(1, result.stars)
        assertFalse(result.crown)
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
        val windows = HitWindows.forMinInterval(500f)
        val notes = notesEvery(count = 3, spacingMs = 500f)
        val attempt = PracticeAttempt(notes, windows)
        attempt.registerHit(notes[0].timeMs)
        assertEquals(1, attempt.combo)

        // Past the second note by more than the OK window plus the expiry grace.
        attempt.expireMissedNotes(
            notes[1].timeMs + windows.okMs + PracticeScoring.EXPIRE_GRACE_MS + 1f,
        )
        assertEquals(1, attempt.misses)
        assertEquals(0, attempt.combo)
        assertEquals(HitWindow.Miss, attempt.judgementAt(1)?.window)
    }

    @Test
    fun `the expiry grace keeps a note alive a little past its window`() {
        val windows = HitWindows.forMinInterval(500f)
        val notes = notesEvery(count = 3, spacingMs = 500f)
        val attempt = PracticeAttempt(notes, windows)

        // Inside the grace the note is still open, so a late poll cannot steal it.
        attempt.expireMissedNotes(notes[0].timeMs + windows.okMs + 1f)
        assertEquals(0, attempt.misses)
    }

    @Test
    fun `hits that belong to no note grow the denominator`() {
        val notes = notesEvery(count = 100, spacingMs = 500f)
        val attempt = PracticeAttempt(notes, HitWindows.forMinInterval(500f))
        notes.forEach { note -> attempt.registerHit(note.timeMs) }
        assertEquals(1f, attempt.liveAccuracy, 0.001f)

        // Two strokes in the gaps, far from any open note.
        attempt.registerHit(notes.last().timeMs + 250f)
        attempt.registerHit(notes.last().timeMs + 400f)
        assertEquals(0, attempt.combo)

        val result = attempt.result()
        assertEquals(2, result.extras)
        // A hundred perfect notes over a hundred and two counted events.
        assertEquals(0.980f, result.accuracy, 0.001f)
        assertTrue(result.passed)
        // Extras never reach the stars directly, but they do cost the crown.
        assertEquals(3, result.stars)
        assertFalse(result.crown)
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
