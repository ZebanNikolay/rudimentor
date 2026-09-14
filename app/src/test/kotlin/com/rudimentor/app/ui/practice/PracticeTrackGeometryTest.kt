package com.rudimentor.app.ui.practice

import com.rudimentor.app.data.levels.PatternHand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeTrackGeometryTest {
    @Test
    fun `densest authored notes keep pad size and an eight dp gap`() {
        val interval = 60_000.0 / 180 / 4
        for (density in listOf(1f, 2f, 3f)) {
            for (width in listOf(640f, 720f, 800f, 960f)) {
                val scale = practiceTrackPixelsPerMs(width * density, 66f * density, 8f * density, interval)
                assertEquals(74.0 * density, scale * interval, 0.0001)
            }
        }
    }

    @Test
    fun `first double stage also gets a gap instead of touching`() {
        val scale = practiceTrackPixelsPerMs(720f, 66f, 8f, 125.0)
        assertEquals(74.0, scale * 125.0, 0.0001)
    }

    @Test
    fun `sparse levels and wide layouts keep the existing scale`() {
        assertEquals(720f / 1400f, practiceTrackPixelsPerMs(720f, 66f, 8f, 250.0), 0f)
        assertEquals(1400f / 1400f, practiceTrackPixelsPerMs(1400f, 66f, 8f, 60_000.0 / 720), 0f)
    }

    @Test
    fun `height capped pads only zoom as much as their actual size needs`() {
        for (height in listOf(100f, 150f, 200f)) {
            val side = minOf(66f, height * 0.36f)
            val scale = practiceTrackPixelsPerMs(720f, side, 8f, 60_000.0 / 720)
            assertEquals(side + 8.0, scale * (60_000.0 / 720), 0.0001)
        }
    }

    @Test
    fun `finite empty and single note sequences do not require zoom`() {
        for (notes in listOf(emptyList(), notes(100f))) {
            val interval = minimumPracticeNoteIntervalMs(notes)
            assertEquals(Double.POSITIVE_INFINITY, interval, 0.0)
            assertEquals(720f / 1400f, practiceTrackPixelsPerMs(720f, 66f, 8f, interval), 0f)
        }
    }

    @Test
    fun `whole attempt minimum covers ramps subdivision changes and rests`() {
        val notes = notes(2_000f, 2_500f, 2_750f, 2_875f, 4_000f, 4_100f)
        val originalTimes = notes.map { it.timeMs }
        val interval = minimumPracticeNoteIntervalMs(notes)
        assertEquals(100.0, interval, 0.0)
        val scale = practiceTrackPixelsPerMs(720f, 66f, 8f, interval)
        for ((first, next) in notes.zipWithNext()) {
            assertTrue((next.timeMs - first.timeMs) * scale >= 74f - 0.0001f)
        }
        assertEquals(originalTimes, notes.map { it.timeMs })
    }

    @Test
    fun `unison remains one note and does not create a zero interval`() {
        val notes = notes(100f, 225f, 350f).map { it.copy(unison = true) }
        assertEquals(125.0, minimumPracticeNoteIntervalMs(notes), 0.0)
    }

    @Test
    fun `unknown or invalid intervals keep a finite default scale`() {
        for (interval in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)) {
            assertEquals(720f / 1400f, practiceTrackPixelsPerMs(720f, 66f, 8f, interval), 0f)
        }
    }

    private fun notes(vararg times: Float): List<PracticeNote> =
        times.mapIndexed { index, time -> PracticeNote(index, PatternHand.Right, time) }
}
