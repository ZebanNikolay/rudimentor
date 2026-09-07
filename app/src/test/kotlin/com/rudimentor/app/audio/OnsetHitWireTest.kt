package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same slot fixture as checkWire() in tests/native/onset_regression.cpp. */
internal fun onsetWireFixture(): LongArray {
    val start = 1L shl 40
    return longArrayOf(
        -25, 500000, 250000, 1, 101, 48000, start + 6, start + 7,
        start + 8, -1, -1000000, 125000, 250000, 62500, 500000, 750000,
        31250, 15625, 375000, 625000, 2500000, 7812, 512, 1920,
    )
}

class OnsetHitWireTest {
    @Test
    fun `all wire slots retain provenance without loading native code`() {
        val start = 1L shl 40
        val hit = OnsetHitWire.decode(onsetWireFixture(), 0)
        assertEquals(-25L, hit.frame)
        assertEquals(0.5f, hit.envelope, 0f)
        assertEquals(0.25f, hit.threshold, 0f)
        assertEquals(
            OnsetDiagnostics(
                sequence = 101,
                sampleRate = 48000,
                peakFrame = start + 6,
                armFrame = start + 7,
                commitFrame = start + 8,
                previousPeakGapFrames = -1,
                previousPeakEnvelope = -1f,
                preArmEnvelope = 0.125f,
                armEnvelope = 0.25f,
                minimumEnvelopeBeforeArm = 0.0625f,
                commitEnvelope = 0.5f,
                candidateSignalPeak = 0.75f,
                adaptiveThresholdAtArm = 0.031250f,
                postHitFloorAtArm = 0.015625f,
                effectiveThresholdAtPeak = 0.375f,
                effectiveThresholdAtCommit = 0.625f,
                thresholdFactorAtArm = 2.5f,
                thresholdFloorAtArm = 0.007812f,
                medianWindowAtArm = 512,
                refractoryFramesAtArm = 1920,
            ),
            hit.diagnostics,
        )
    }

    @Test
    fun `multiple hits in one drain have separate evidence`() {
        val first = onsetWireFixture()
        val second = onsetWireFixture().apply {
            this[0] = 77
            this[4] = 102
            this[9] = 2400
            this[15] = 250000
        }
        val batch = first + second
        val a = OnsetHitWire.decode(batch, 0)
        val b = OnsetHitWire.decode(batch, OnsetHitWire.STRIDE)
        assertEquals(0.75f, a.diagnostics!!.candidateSignalPeak, 0f)
        assertEquals(0.25f, b.diagnostics!!.candidateSignalPeak, 0f)
        assertEquals(101L, a.diagnostics.sequence)
        assertEquals(102L, b.diagnostics.sequence)
        assertEquals(2400L, b.diagnostics.previousPeakGapFrames)
        assertEquals(77L, b.frame)
        assertEquals(0.5f, b.envelope, 0f)
    }

    @Test
    fun `legacy triples and unknown versions never fabricate evidence`() {
        val legacy = OnsetHitWire.decode(longArrayOf(12, 500000, 250000), 0, stride = 3)
        assertEquals(NativeMicLab.HitEvent(12, 0.5f, 0.25f), legacy)
        assertNull(legacy.diagnostics)
        assertNull(OnsetHitWire.decode(onsetWireFixture().apply { this[3] = 99 }, 0).diagnostics)
        assertNull(PracticeSession.Hit(1f, 0.5f, 0.25f).diagnostics)
    }
}
