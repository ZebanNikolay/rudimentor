package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.SettingsDraft
import com.rudimentor.app.data.levels.PatternHand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A whole attempt played over an audio path the calibration got wrong (decision 167).
 *
 * This is the dev.41 complaint in a test: the learner strikes exactly on the click, the phone
 * reports every stroke tens of milliseconds late, and the run has to grade the playing and not
 * the phone.
 */
class PracticeSelfTuningTest {
    @Test
    fun `a steady run on a mis-calibrated path scores perfect after the first bar`() {
        val windows = HitWindows.forMinInterval(500f)
        val notes = notesEvery(count = 64, spacingMs = 500f)
        val attempt = PracticeAttempt(notes, AttemptWindows.uniform(windows), LatencyTracker())

        // Every stroke arrives 60 ms late, exactly as dev.41 reported it, and the run is
        // polled between the strokes the way the practice screen polls it.
        notes.forEach { note ->
            attempt.expireMissedNotes(note.timeMs - 1f)
            attempt.registerHit(note.timeMs + 60f)
        }
        attempt.expireMissedNotes(notes.last().timeMs + 1_000f)

        val result = attempt.result()
        // Nothing is lost: no note expires under a bias that is still being measured, and no
        // stroke is charged as an extra for arriving late.
        assertEquals(0, result.misses)
        assertEquals(0, result.extras)
        // The opening bar is graded against its own median, everything after it against the
        // measured bias, so the whole run reads as what it was: dead on.
        assertEquals(64, result.perfect)
        assertEquals(1f, result.accuracy, 0.001f)
        assertEquals(60f, result.latencyBiasMs, 2f)
    }

    @Test
    fun `real playing errors still count while the path is late`() {
        val windows = HitWindows.forMinInterval(500f)
        val notes = notesEvery(count = 24, spacingMs = 500f)
        val attempt = PracticeAttempt(notes, AttemptWindows.uniform(windows), LatencyTracker())

        notes.forEachIndexed { index, note ->
            // The path is 60 ms late throughout; from the second bar on the learner also
            // rushes every fourth stroke by a whole GOOD window.
            val playerErrorMs = if (index >= 8 && index % 4 == 0) -windows.goodMs else 0f
            attempt.registerHit(note.timeMs + 60f + playerErrorMs)
        }

        val result = attempt.result()
        assertEquals(0, result.misses)
        assertEquals(0, result.extras)
        // The rushed strokes are graded as rushed, not absorbed into the clock.
        assertTrue("good ${result.good}", result.good >= 3)
        assertTrue("perfect ${result.perfect}", result.perfect >= 16)
        assertEquals(60f, result.latencyBiasMs, 8f)
    }

    @Test
    fun `the measured bias is written back into the output profile`() {
        val draft = SettingsDraft.from(com.rudimentor.app.data.AppSettings())
            .withCalibration(167f)
        val tuned = draft.withLatencyBias(60f)

        assertEquals(227f, tuned.latencyMs, 0.001f)
        assertEquals(227f, tuned.selectedProfile.latencyMs, 0.001f)
        assertTrue(tuned.selectedProfile.latencyCalibrated)

        // A bias inside the player's own scatter changes nothing.
        assertEquals(draft, draft.withLatencyBias(SettingsDraft.BIAS_APPLY_MIN_MS - 1f))
        assertEquals(draft, draft.withLatencyBias(Float.NaN))
    }

    private fun notesEvery(count: Int, spacingMs: Float): List<PracticeNote> =
        List(count) { index ->
            PracticeNote(
                index = index,
                hand = if (index % 2 == 0) PatternHand.Right else PatternHand.Left,
                timeMs = 1000f + index * spacingMs,
            )
        }
}
