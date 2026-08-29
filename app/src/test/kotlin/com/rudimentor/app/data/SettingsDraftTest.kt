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
    fun `the click switch is saved on the output in force`() {
        val draft = SettingsDraft.from(AppSettings()).withClickAudible(true)
        assertTrue(draft.clickAudible)

        val saved = draft.applyTo(AppSettings())
        assertTrue(saved.clickAudible)
        assertTrue(saved.selectedProfile.clickAudible)
    }

    @Test
    fun `the built-in output starts with the click silent`() {
        // Over the speaker the microphone would hear the click and score it as a stroke.
        assertFalse(AppSettings().sanitized().clickAudible)
    }

    @Test
    fun `switching output brings its own click and gate`() {
        val phones = OutputProfile(
            id = "p1",
            name = "Sony",
            kind = OutputKind.Bluetooth,
            boundKey = "bt:sony",
            lastUsedAt = 5L,
            latencyMs = 300f,
            latencyCalibrated = true,
            clickAudible = true,
            micThresholdLevel = 0.08f,
        )
        val settings = AppSettings(outputProfiles = listOf(OutputProfile.default(0f, false), phones))
        val draft = SettingsDraft.from(settings).withSelectedProfile("p1")

        assertTrue(draft.clickAudible)
        assertEquals(0.08f, draft.micThresholdLevel)
        assertEquals(300f, draft.latencyMs)

        // And back to the built-in one, which kept its own silence and its own gate.
        val back = draft.withSelectedProfile(OutputProfile.DEFAULT_ID)
        assertFalse(back.clickAudible)
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
