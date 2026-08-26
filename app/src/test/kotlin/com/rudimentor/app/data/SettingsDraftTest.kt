package com.rudimentor.app.data

import com.rudimentor.app.audio.MicThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Save contract of the settings screens: a draft changes nothing until it is applied,
 * and a measured latency is clamped and marked as measured (decision 154).
 */
class SettingsDraftTest {

    @Test
    fun `an untouched draft would change nothing`() {
        val settings = AppSettings()
        val draft = SettingsDraft.from(settings)
        assertFalse(draft.differsFrom(settings))
        assertEquals(settings.sanitized(), draft.applyTo(settings))
    }

    @Test
    fun `a touched switch is only stored once the draft is applied`() {
        val settings = AppSettings()
        val draft = SettingsDraft.from(settings).copy(showOffsetMs = !settings.showOffsetMs)
        assertTrue(draft.differsFrom(settings))
        // The source object is untouched: dropping the draft is enough to cancel.
        assertEquals(false, settings.showOffsetMs)
        assertEquals(true, draft.applyTo(settings).showOffsetMs)
    }

    @Test
    fun `a hand on the click switch takes it off automatic`() {
        val draft = SettingsDraft.from(AppSettings()).withClickAudible(true)
        assertFalse(draft.clickFollowsHeadphones)
        assertTrue(draft.clickAudible)
        // Off automatic the stored choice wins over the output state.
        assertTrue(draft.effectiveClickAudible(headphonesConnected = false))
    }

    @Test
    fun `on automatic the click switch mirrors the output`() {
        val draft = SettingsDraft.from(AppSettings())
        assertTrue(draft.clickFollowsHeadphones)
        assertTrue(draft.effectiveClickAudible(headphonesConnected = true))
        assertFalse(draft.effectiveClickAudible(headphonesConnected = false))
    }

    @Test
    fun `a measured latency is clamped and marked as measured`() {
        val draft = SettingsDraft.from(AppSettings())
        assertFalse(draft.latencyCalibrated)

        val measured = draft.withCalibration(208f)
        assertTrue(measured.latencyCalibrated)
        assertEquals(208f, measured.latencyMs, 0.01f)

        assertEquals(
            AppSettings.LATENCY_MAX_MS,
            draft.withCalibration(AppSettings.LATENCY_MAX_MS + 500f).latencyMs,
            0.01f,
        )
        assertEquals(
            AppSettings.LATENCY_MIN_MS,
            draft.withCalibration(AppSettings.LATENCY_MIN_MS - 500f).latencyMs,
            0.01f,
        )
    }

    @Test
    fun `applying a measured draft carries the flag into the settings`() {
        val settings = AppSettings()
        val applied = SettingsDraft.from(settings).withCalibration(196f).applyTo(settings)
        assertEquals(196f, applied.inputLatencyMs, 0.01f)
        assertTrue(applied.latencyCalibrated)
    }

    @Test
    fun `a hand-set latency counts as calibrated`() {
        // A number the learner dialled in is a whole round trip they are claiming, so the
        // engine must not add the measured output latency on top of it (decision 158).
        val draft = SettingsDraft.from(AppSettings()).withLatency(240f)
        assertTrue(draft.latencyCalibrated)
        assertEquals(240f, draft.latencyMs, 0.01f)
    }

    @Test
    fun `the microphone gate is clamped and only stored on apply`() {
        val settings = AppSettings()
        val draft = SettingsDraft.from(settings).withMicThreshold(9f)
        assertEquals(MicThreshold.MAX_LEVEL, draft.micThresholdLevel, 0.0001f)
        assertEquals(
            MicThreshold.MIN_LEVEL,
            draft.withMicThreshold(-1f).micThresholdLevel,
            0.0001f,
        )
        assertTrue(draft.differsFrom(settings))
        assertEquals(
            MicThreshold.MAX_LEVEL,
            draft.applyTo(settings).micThresholdLevel,
            0.0001f,
        )
    }
}
