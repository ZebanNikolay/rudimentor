package com.rudimentor.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide whether the result screen opens its mouth at all.
 *
 * The cases here are the two real logs that started this: a run whose mean offset was
 * -62 ms with a 33 ms spread (proven, and worth saying), and the "speeding up" in the same
 * logs that turned out to be smaller than the noise of the two groups it was measured from.
 */
class PracticeAdviceTest {

    @Test
    fun `a value smaller than three of its own errors is not significant`() {
        val measured = Measured(valueMs = 20f, errorMs = 10f)
        assertFalse(measured.significant(minimumMs = 0f))
    }

    @Test
    fun `a value needs a practical size as well as a statistical one`() {
        // Tiny error, so 40 sigmas -- and still not worth a sentence: 4 ms is nothing.
        val measured = Measured(valueMs = 4f, errorMs = 0.1f)
        assertTrue(measured.sigmas > Measured.SIGNIFICANT_SIGMAS)
        assertFalse(measured.significant(minimumMs = 10f))
        assertTrue(measured.significant(minimumMs = 1f))
    }

    @Test
    fun `a run that missed notes but proved nothing says nothing at all`() {
        // Eight notes never played and the rest dead on: the score is short of three stars,
        // and there is still no number here worth a sentence. Silence is the right answer.
        val result = attempt(
            offsets = List(32) { if (it % 2 == 0) 3f else -3f },
            noteCount = 40,
        )
        assertTrue(result.accuracy < PracticeScoring.THREE_STAR_ACCURACY)
        assertNull(PracticeAdvice.of(result))
    }

    @Test
    fun `a proven constant offset is named as an offset`() {
        // Every stroke 60 ms late with a small spread: the -62 ms log, mirrored.
        val result = attempt(offsets = List(32) { if (it % 2 == 0) 58f else 62f })
        val advice = PracticeAdvice.of(result)
        assertEquals(AdviceKind.Offset, advice?.kind)
    }

    @Test
    fun `a drift smaller than the noise of its own quarters stays unsaid`() {
        // First quarter around -10, last around +10, but every stroke scatters by ~30 ms:
        // the difference is inside what two groups of eight do on their own.
        val scatter = listOf(-40f, 30f, -25f, 35f, -35f, 25f, -30f, 40f)
        val offsets = scatter.map { it - 10f } +
            List(16) { 0f } +
            scatter.map { it + 10f }
        val metrics = PracticeMetrics.of(attempt(offsets))
        assertFalse(metrics.driftSignificant)
    }

    @Test
    fun `too few counted strokes means no numbers and no advice`() {
        val result = attempt(offsets = List(4) { 80f })
        val metrics = PracticeMetrics.of(result)
        assertEquals(4, metrics.judged)
        assertNull(PracticeAdvice.of(result))
    }

    @Test
    fun `a broken audio path is called out before anything about the playing`() {
        // A third of the notes matched, and those sit far outside the OK window: this is the
        // click arriving late, so the screen must not lecture about timing.
        val result = attempt(
            offsets = List(10) { 150f },
            noteCount = 30,
        )
        assertEquals(AdviceKind.SoundCheck, PracticeAdvice.of(result)?.kind)
    }

    @Test
    fun `strokes too quiet to count outrank a timing hint`() {
        val result = attempt(
            offsets = List(16) { 60f },
            droppedOnsets = 8,
        )
        assertEquals(AdviceKind.Detector, PracticeAdvice.of(result)?.kind)
    }

    @Test
    fun `extra strokes are called out before a timing hint`() {
        val result = attempt(
            offsets = List(16) { 60f },
            extras = 4,
        )
        assertEquals(AdviceKind.ExtraHits, PracticeAdvice.of(result)?.kind)
    }

    @Test
    fun `a clean run at three stars is praised`() {
        val result = attempt(offsets = List(32) { 0f })
        assertEquals(AdviceKind.AllGood, PracticeAdvice.of(result)?.kind)
    }

    @Test
    fun `the offset hint carries what the run is worth without the offset`() {
        val result = attempt(offsets = List(32) { 60f })
        val metrics = PracticeMetrics.of(result)
        // The same strokes, judged around their own mean, score far better than they did
        // against the click -- but nowhere near a perfect run, which is the point.
        assertTrue(metrics.accuracyWithoutOffset > result.accuracy)
    }

    /** One attempt with [offsets] as the graded strokes, scored the way the game scores. */
    private fun attempt(
        offsets: List<Float>,
        noteCount: Int = offsets.size,
        extras: Int = 0,
        droppedOnsets: Int = 0,
    ): PracticeResult {
        val windows = HitWindows.Default
        var perfect = 0
        var good = 0
        var ok = 0
        var weighted = 0f
        offsets.forEach { offset ->
            val window = windows.window(offset)
            weighted += PracticeScoring.weight(window)
            when (window) {
                HitWindow.Perfect -> perfect += 1
                HitWindow.Good -> good += 1
                HitWindow.Ok -> ok += 1
                HitWindow.Miss -> Unit
            }
        }
        val accuracy = PracticeScoring.accuracy(
            weightedNotes = weighted,
            noteCount = noteCount,
            extras = extras,
        )
        return PracticeResult(
            noteCount = noteCount,
            perfect = perfect,
            good = good,
            ok = ok,
            misses = noteCount - (perfect + good + ok),
            extras = extras,
            maxCombo = perfect + good + ok,
            accuracy = accuracy,
            meanOffsetMs = if (offsets.isEmpty()) 0f else offsets.average().toFloat(),
            offsets = offsets,
            windows = windows,
            droppedOnsets = droppedOnsets,
        )
    }
}
