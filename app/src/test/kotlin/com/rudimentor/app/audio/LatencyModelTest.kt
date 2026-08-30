package com.rudimentor.app.audio

import com.rudimentor.app.data.AppSettings
import com.rudimentor.app.data.SettingsDraft
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The split of a round trip into the half that moves the stroke and the half that moves the
 * picture and the click (decision 188).
 *
 * The field numbers are in here on purpose: 212 ms of round trip on the Pixel with 38 ms of
 * microphone in it. The same level played twice, once to the click and once to the picture,
 * scored 21.8 % and 0.0 % while the picture stood on the render clock -- these tests are the
 * arithmetic that stops that happening again.
 */
class LatencyModelTest {

    @Test
    fun `reported microphone latency is used`() {
        assertEquals(38f, LatencyModel.micPart(38f, roundTripMs = 212f), 0.001f)
        assertEquals(174f, LatencyModel.outputPart(212f, micMs = 38f), 0.001f)
    }

    @Test
    fun `two halves add back up to the round trip`() {
        val mic = LatencyModel.micPart(41.7f, roundTripMs = 305f)
        assertEquals(305f, mic + LatencyModel.outputPart(305f, mic), 0.001f)
    }

    @Test
    fun `nothing reported falls back to the default share`() {
        assertEquals(LatencyModel.DEFAULT_MIC_MS, LatencyModel.micPart(0f, 212f), 0.001f)
        assertEquals(LatencyModel.DEFAULT_MIC_MS, LatencyModel.micPart(Float.NaN, 212f), 0.001f)
    }

    @Test
    fun `an impossible reading is not believed`() {
        assertEquals(LatencyModel.DEFAULT_MIC_MS, LatencyModel.micPart(480f, 212f), 0.001f)
    }

    @Test
    fun `the microphone half never eats more than the round trip`() {
        assertEquals(12f, LatencyModel.micPart(38f, roundTripMs = 12f), 0.001f)
        assertEquals(0f, LatencyModel.outputPart(12f, micMs = 12f), 0.001f)
    }

    @Test
    fun `a measured microphone half is stored, a nonsense one is not`() {
        val draft = SettingsDraft.from(AppSettings())
        assertEquals(38f, draft.withMicLatency(38f).micLatencyMs, 0.001f)
        assertEquals(0f, draft.withMicLatency(null).micLatencyMs, 0.001f)
        assertEquals(0f, draft.withMicLatency(0f).micLatencyMs, 0.001f)
        assertEquals(0f, draft.withMicLatency(900f).micLatencyMs, 0.001f)
        // Once stored, a later bad reading leaves it alone.
        assertEquals(38f, draft.withMicLatency(38f).withMicLatency(900f).micLatencyMs, 0.001f)
    }

    @Test
    fun `the stored half survives a save`() {
        val settings = SettingsDraft.from(AppSettings())
            .withMicLatency(38f)
            .applyTo(AppSettings())
        assertEquals(38f, settings.micLatencyMs, 0.001f)
    }
}
