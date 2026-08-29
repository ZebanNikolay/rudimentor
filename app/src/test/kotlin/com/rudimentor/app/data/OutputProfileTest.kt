package com.rudimentor.app.data

import com.rudimentor.app.audio.MicThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules behind one latency per pair of headphones (decision 161). */
class OutputProfileTest {

    private val bluetooth = OutputDevice(OutputKind.Bluetooth, "WH-1000XM4", "8|WH-1000XM4")
    private val wired = OutputDevice(OutputKind.Wired, "Wired", "3|Wired")

    private fun settings(vararg profiles: OutputProfile, selected: String) = AppSettings(
        outputProfiles = profiles.toList(),
        selectedProfileId = selected,
    )

    private fun profile(
        id: String,
        latency: Float,
        key: String? = null,
        used: Long = 0L,
        calibrated: Boolean = true,
    ) = OutputProfile(
        id = id,
        name = id,
        kind = OutputKind.Bluetooth,
        boundKey = key,
        latencyMs = latency,
        latencyCalibrated = calibrated,
        lastUsedAt = used,
    )

    @Test
    fun `defaults to one built-in profile that cannot be removed`() {
        val safe = AppSettings().sanitized()
        assertEquals(1, safe.outputProfiles.size)
        assertEquals(OutputProfile.DEFAULT_ID, safe.outputProfiles.single().id)
        assertFalse(safe.outputProfiles.single().removable)
    }

    @Test
    fun `the latency in force is the selected profile's own`() {
        val safe = settings(
            OutputProfile.default(20f, true),
            profile("bt", 250f, bluetooth.key),
            selected = "bt",
        ).sanitized()
        assertEquals(250f, safe.inputLatencyMs, 0.001f)
        assertEquals("bt", safe.selectedProfile.id)
    }

    @Test
    fun `a selection that no longer exists falls back to the built-in profile`() {
        val safe = settings(OutputProfile.default(20f, true), selected = "gone").sanitized()
        assertEquals(OutputProfile.DEFAULT_ID, safe.selectedProfileId)
        assertEquals(20f, safe.inputLatencyMs, 0.001f)
    }

    @Test
    fun `the list never grows past three, dropping the one used longest ago`() {
        val safe = settings(
            OutputProfile.default(20f, true),
            profile("old", 100f, "k1", used = 1L),
            profile("newer", 200f, "k2", used = 5L),
            profile("newest", 300f, "k3", used = 9L),
            selected = OutputProfile.DEFAULT_ID,
        ).sanitized()
        assertEquals(OutputProfile.MAX_PROFILES, safe.outputProfiles.size)
        assertEquals(
            listOf(OutputProfile.DEFAULT_ID, "newest", "newer"),
            safe.outputProfiles.map { it.id },
        )
    }

    @Test
    fun `an output is matched by its key, and an unknown one matches nothing`() {
        val safe = settings(
            OutputProfile.default(20f, true),
            profile("bt", 250f, bluetooth.key),
            selected = OutputProfile.DEFAULT_ID,
        ).sanitized()
        assertEquals("bt", safe.profileFor(bluetooth)?.id)
        assertNull(safe.profileFor(wired))
    }

    @Test
    fun `saving the connected output selects it and marks it unmeasured`() {
        val draft = SettingsDraft.from(AppSettings(inputLatencyMs = 40f, latencyCalibrated = true))
        val added = draft.withAddedProfile(bluetooth, now = 7L)
        assertEquals(2, added.outputProfiles.size)
        assertEquals("WH-1000XM4", added.selectedProfile.name)
        assertEquals(40f, added.latencyMs, 0.001f)
        assertFalse(added.latencyCalibrated)
    }

    @Test
    fun `saving an output that is already known just selects it`() {
        val start = SettingsDraft.from(AppSettings()).withAddedProfile(bluetooth, now = 7L)
        val again = start.withSelectedProfile(OutputProfile.DEFAULT_ID)
            .withAddedProfile(bluetooth, now = 9L)
        assertEquals(2, again.outputProfiles.size)
        assertEquals(bluetooth.key, again.selectedProfile.boundKey)
    }

    @Test
    fun `deleting a profile takes its calibration with it and falls back to the default`() {
        val draft = SettingsDraft.from(AppSettings(inputLatencyMs = 20f))
            .withAddedProfile(bluetooth, now = 7L)
            .withLatency(260f)
        val after = draft.withoutProfile(draft.selectedProfileId)
        assertEquals(1, after.outputProfiles.size)
        assertEquals(OutputProfile.DEFAULT_ID, after.selectedProfileId)
        assertEquals(20f, after.latencyMs, 0.001f)
    }

    @Test
    fun `the built-in profile cannot be deleted`() {
        val draft = SettingsDraft.from(AppSettings())
        assertEquals(draft, draft.withoutProfile(OutputProfile.DEFAULT_ID))
    }

    @Test
    fun `saving writes the measured latency into the selected profile only`() {
        val stored = settings(
            OutputProfile.default(20f, true),
            profile("bt", 250f, bluetooth.key),
            selected = "bt",
        ).sanitized()
        val applied = SettingsDraft.from(stored).withCalibration(266f).applyTo(stored)
        assertEquals(266f, applied.outputProfiles.first { it.id == "bt" }.latencyMs, 0.001f)
        val builtIn = applied.outputProfiles.first { it.id == OutputProfile.DEFAULT_ID }
        assertEquals(20f, builtIn.latencyMs, 0.001f)
        assertEquals(266f, applied.inputLatencyMs, 0.001f)
    }

    @Test
    fun `a measurement shows up on the profile row before Save`() {
        val draft = SettingsDraft.from(AppSettings(inputLatencyMs = 114f))
            .withAddedProfile(bluetooth, now = 7L)
            .withCalibration(194f)
        assertEquals(194f, draft.selectedProfile.latencyMs, 0.001f)
        assertTrue(draft.selectedProfile.latencyCalibrated)
        val untouched = draft.outputProfiles.first { it.id == OutputProfile.DEFAULT_ID }
        assertEquals(114f, untouched.latencyMs, 0.001f)
    }

    @Test
    fun `renaming keeps the format's separators out of the name`() {
        val draft = SettingsDraft.from(AppSettings())
            .withAddedProfile(bluetooth, now = 7L)
        val renamed = draft.withRenamedProfile(draft.selectedProfileId, "a~b;c|d")
        assertEquals("a b c d", renamed.selectedProfile.name)
    }

    @Test
    fun `a blank name falls back to the kind`() {
        val draft = SettingsDraft.from(AppSettings()).withAddedProfile(wired, now = 7L)
        val renamed = draft.withRenamedProfile(draft.selectedProfileId, "   ")
        assertEquals(OutputKind.Wired.fallbackName, renamed.selectedProfile.name)
    }

    @Test
    fun `profiles survive a round trip through the stored string`() {
        val profiles = listOf(
            OutputProfile.default(20f, true),
            profile("bt", 250f, bluetooth.key, used = 12L),
        )
        val restored = parseProfiles(profiles.serialize(), 0f, false, MicThreshold.DEFAULT_LEVEL)
        assertEquals(profiles, restored)
    }

    @Test
    fun `the skew of a measurement is stored with it and survives a round trip`() {
        val draft = SettingsDraft.from(AppSettings())
            .withAddedProfile(bluetooth, now = 7L)
            .withCalibration(268f, calibrationSkewMs = 145f)
        assertEquals(145f, draft.selectedProfile.calibrationSkewMs!!, 0.001f)
        val restored = parseProfiles(draft.outputProfiles.serialize(), 0f, false, MicThreshold.DEFAULT_LEVEL)
        assertEquals(draft.outputProfiles, restored)
    }

    @Test
    fun `a hand-moved slider stores no skew, so nothing is corrected for it`() {
        val draft = SettingsDraft.from(AppSettings())
            .withAddedProfile(bluetooth, now = 7L)
            .withCalibration(268f, calibrationSkewMs = 145f)
            .withLatency(200f)
        assertEquals(null, draft.selectedProfile.calibrationSkewMs)
    }

    @Test
    fun `a profile stored before skews were recorded still loads`() {
        val old = "default~Built-in~d~~20.0~1~0"
        // The gate was global back then, so the profile inherits the one in force.
        val restored = parseProfiles(old, 0f, false, fallbackGateLevel = 0.07f)
        assertEquals(1, restored.size)
        assertEquals(20f, restored.single().latencyMs, 0.001f)
        assertEquals(null, restored.single().calibrationSkewMs)
        assertEquals(0.07f, restored.single().micThresholdLevel, 0.001f)
        // And the click of the built-in output starts silent, as it always did.
        assertFalse(restored.single().clickAudible)
    }

    @Test
    fun `an install with no profiles yet keeps the latency it already had`() {
        val restored = parseProfiles(
            raw = null,
            fallbackLatencyMs = 231f,
            fallbackCalibrated = true,
            fallbackGateLevel = MicThreshold.DEFAULT_LEVEL,
        )
        assertEquals(231f, restored.single().latencyMs, 0.001f)
        assertTrue(restored.single().latencyCalibrated)
    }

    @Test
    fun `a corrupt entry is dropped, the rest survive`() {
        val raw = OutputProfile.default(20f, true).let { listOf(it).serialize() } + ";garbage"
        val restored = parseProfiles(raw, 0f, false, MicThreshold.DEFAULT_LEVEL)
        assertEquals(1, restored.size)
    }

    @Test
    fun `the calibration round applies half the gate the learner set`() {
        assertEquals(
            MicThreshold.DEFAULT_LEVEL / 2f,
            MicThreshold.softened(MicThreshold.DEFAULT_LEVEL),
            0.0001f,
        )
        assertEquals(MicThreshold.MIN_LEVEL, MicThreshold.softened(MicThreshold.MIN_LEVEL), 0.0001f)
    }
}
