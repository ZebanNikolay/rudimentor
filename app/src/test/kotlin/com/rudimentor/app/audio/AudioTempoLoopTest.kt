package com.rudimentor.app.audio

import kotlin.math.ceil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTempoLoopTest {
    private val rate = 48_000
    private val plan = intArrayOf(60, 60, 120, 240)

    private fun frames(beat: Long, loopStart: Int = 2): Long =
        AudioTempoLoop.framesBeforeBeat(beat, rate, 60, plan, loopStart)

    @Test
    fun `prefix plays once and suffix repeats on the same clock`() {
        val expected = longArrayOf(0, 48_000, 96_000, 120_000, 132_000, 156_000, 168_000, 192_000)
        expected.forEachIndexed { beat, frame ->
            assertEquals("beat $beat", frame, frames(beat.toLong()))
        }
    }

    @Test
    fun `zero loop start keeps legacy whole-plan repeating order`() {
        val expected = longArrayOf(0, 48_000, 96_000, 120_000, 132_000, 180_000, 228_000, 252_000)
        expected.forEachIndexed { beat, frame ->
            assertEquals("beat $beat", frame, frames(beat.toLong(), loopStart = 0))
        }
        assertEquals(
            frames(19, loopStart = 0),
            AudioTempoLoop.framesBeforeBeat(19, rate, 60, plan),
        )
    }

    @Test
    fun `one-beat suffix repeats without a modulo by zero`() {
        assertEquals(144_000L, frames(5, loopStart = 3))
        assertEquals(156_000L, frames(6, loopStart = 3))
    }

    @Test
    fun `invalid and all-prefix requests fall back to whole-plan looping`() {
        for (invalid in listOf(-1, Int.MIN_VALUE, plan.size, plan.size + 1, Int.MAX_VALUE)) {
            assertEquals(0, AudioTempoLoop.normalizedLoopStart(invalid, plan.size))
            assertEquals(frames(101, loopStart = 0), frames(101, loopStart = invalid))
        }
    }

    @Test
    fun `empty plan ignores loop start and stays at fixed tempo`() {
        assertEquals(0, AudioTempoLoop.normalizedLoopStart(3, 0))
        for (loopStart in listOf(-1, 0, 3, Int.MAX_VALUE)) {
            assertEquals(
                7 * 24_000L,
                AudioTempoLoop.framesBeforeBeat(7, rate, 120, intArrayOf(), loopStart),
            )
        }
    }

    @Test
    fun `invalid beat or sample rate does not create a timestamp`() {
        for (beat in listOf(Long.MIN_VALUE, -1, 0)) {
            assertEquals(0L, frames(beat))
        }
        for (sampleRate in listOf(Int.MIN_VALUE, -1, 0)) {
            assertEquals(0L, AudioTempoLoop.framesBeforeBeat(1, sampleRate, 60, plan, 2))
        }
    }

    @Test
    fun `plan capacity and BPM clamps match native before anchoring`() {
        val authored = IntArray(AudioTempoLoop.MAX_PLAN_BEATS + 37) {
            if (it % 2 == 0) Int.MIN_VALUE else Int.MAX_VALUE
        }
        val retained = AudioTempoLoop.boundedPlan(authored)
        assertEquals(512, retained.size)
        assertTrue(retained.all { it == 40 || it == 240 })
        assertEquals(
            AudioTempoLoop.framesBeforeBeat(10_003, rate, 60, retained, 4),
            AudioTempoLoop.framesBeforeBeat(10_003, rate, 60, authored, 4),
        )
        assertEquals(0, AudioTempoLoop.normalizedLoopStart(512, retained.size))
        assertEquals(
            AudioTempoLoop.framesBeforeBeat(10_003, rate, 60, retained, 0),
            AudioTempoLoop.framesBeforeBeat(10_003, rate, 60, authored, 512),
        )
    }

    @Test
    fun `installed plan is a copy independent of the author array`() {
        val authored = plan.copyOf()
        val retained = AudioTempoLoop.boundedPlan(authored)
        authored[0] = 240
        assertArrayEquals(plan, retained)
    }

    @Test
    fun `fractional frames round at the deadline not once per beat`() {
        val fractional = intArrayOf(137)
        val beatFrames = rate * 60.0 / 137
        val beat = 101L
        val offset = AudioTempoLoop.framesBeforeBeat(beat, rate, 137, fractional)
        assertEquals(ceil(beat * beatFrames).toLong(), offset)
        assertTrue(offset > beat * beatFrames.toLong())
    }

    @Test
    fun `fractional warm-up is included when recovering a missing first tick`() {
        val unusualRate = 44_101
        val warmUp = unusualRate * 0.30
        val beatFrames = unusualRate * 60.0 / 137
        for (beat in listOf(1L, 2L, 5L, 101L, 10_003L)) {
            val tick = ceil(warmUp + beat * beatFrames).toLong()
            val offset = AudioTempoLoop.framesBeforeBeat(beat, unusualRate, 137, intArrayOf(137))
            assertEquals(ceil(warmUp).toLong(), tick - offset)
        }
    }

    @Test
    fun `first received tick can follow the prefix and many whole suffix cycles`() {
        val firstTick = 14_400L
        for (beat in listOf(1L, 2L, 3L, 4L, 5L, 129L, 1_000_001L, 4_294_967_333L)) {
            val expectedElapsed = when (beat) {
                1L -> 48_000L
                else -> 96_000L + ((beat - 2) / 2) * 36_000L + ((beat - 2) % 2) * 24_000L
            }
            assertEquals(firstTick, firstTick + expectedElapsed - frames(beat))
        }
    }

    @Test
    fun `long beat math converts before integer products can overflow`() {
        // The old beat * sampleRate intermediate overflowed for a valid Long index.
        val beat = 1_000_000_000_000_000L
        val offset = AudioTempoLoop.framesBeforeBeat(beat, rate, 240, intArrayOf())
        assertEquals(Long.MAX_VALUE, offset) // safely saturates beyond representable frames
        assertEquals(36_000_000_000_096_000L, frames(2_000_000_000_002L))
    }

    @Test
    fun `48-hour exact clock still resolves adjacent frames unlike Float`() {
        val anchor = 14_400L
        val frame = anchor + 48L * 60 * 60 * rate
        val now = AudioTempoLoop.positionMsExact(frame, anchor, rate)
        val next = AudioTempoLoop.positionMsExact(frame + 1, anchor, rate)
        assertEquals(172_800_000.0, now, 0.0)
        assertEquals(1000.0 / rate, next - now, 0.0000001)
        assertEquals(now.toFloat(), next.toFloat(), 0f)
    }

    @Test
    fun `nearby large frame counters are subtracted before conversion to Double`() {
        val anchor = Long.MAX_VALUE - 100
        assertEquals(1000.0 / rate, AudioTempoLoop.positionMsExact(anchor + 1, anchor, rate), 0.0)
        assertEquals(-1000.0 / rate, AudioTempoLoop.positionMsExact(anchor - 1, anchor, rate), 0.0)
        assertEquals(0.0, AudioTempoLoop.positionMsExact(1, 0, 0), 0.0)
    }
}
