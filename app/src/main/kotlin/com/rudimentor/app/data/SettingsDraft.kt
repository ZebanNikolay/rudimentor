package com.rudimentor.app.data

import com.rudimentor.app.audio.MicThreshold

/**
 * The settings the user is editing right now, before they have said Save.
 *
 * A settings screen edits this copy and nothing else: closing it, backing out of it or
 * walking into the calibration screen and out again never touches what is stored. Only
 * Save writes the draft back into [AppSettings] (decision 154). Without it every touch
 * of a switch was already persisted, so a user who did not remember the old value had
 * no way back.
 */
data class SettingsDraft(
    val clickAudible: Boolean,
    val clickFollowsHeadphones: Boolean,
    val showOffsetMs: Boolean,
    val latencyMs: Float,
    val latencyCalibrated: Boolean,
    val micThresholdLevel: Float,
    /**
     * The saved outputs and the one in use. [latencyMs] above is the selected profile's
     * value while it is being edited; Save writes it back into that profile (decision 161).
     */
    val outputProfiles: List<OutputProfile>,
    val selectedProfileId: String,
) {
    val selectedProfile: OutputProfile
        get() = outputProfiles.firstOrNull { it.id == selectedProfileId }
            ?: outputProfiles.first()

    /** Room for one more saved output. */
    val canAddProfile: Boolean
        get() = outputProfiles.size < OutputProfile.MAX_PROFILES

    /**
     * Switch to another saved output. The latency on the screen follows it, because it is
     * that profile's own number from here on.
     */
    fun withSelectedProfile(id: String): SettingsDraft {
        val target = outputProfiles.firstOrNull { it.id == id } ?: return this
        return copy(
            selectedProfileId = target.id,
            latencyMs = target.latencyMs,
            latencyCalibrated = target.latencyCalibrated,
        )
    }

    /**
     * Save the output that is connected right now as a profile and switch to it.
     *
     * It starts on the latency currently on the screen but counts as uncalibrated: the
     * number belongs to different headphones until this pair has been measured.
     */
    fun withAddedProfile(device: OutputDevice, now: Long): SettingsDraft {
        val existing = outputProfiles.firstOrNull { it.boundKey == device.key }
        if (existing != null) return withSelectedProfile(existing.id)
        if (!canAddProfile) return this
        val added = OutputProfile.forDevice(
            device = device,
            latencyMs = latencyMs,
            latencyCalibrated = false,
            now = now,
        )
        return copy(
            outputProfiles = outputProfiles + added,
            selectedProfileId = added.id,
            latencyMs = added.latencyMs,
            latencyCalibrated = false,
        )
    }

    fun withRenamedProfile(id: String, name: String): SettingsDraft = copy(
        outputProfiles = outputProfiles.map { profile ->
            if (profile.id != id) {
                profile
            } else {
                val clean = OutputProfile.cleanName(name)
                profile.copy(name = clean.ifBlank { profile.kind.fallbackName })
            }
        },
    )

    /**
     * Forget an output, and its calibration with it. The built-in profile cannot go, and
     * dropping the selected one falls back to it.
     */
    fun withoutProfile(id: String): SettingsDraft {
        val target = outputProfiles.firstOrNull { it.id == id } ?: return this
        if (!target.removable) return this
        val rest = outputProfiles.filterNot { it.id == id }
        val next = copy(outputProfiles = rest)
        return if (selectedProfileId == id) {
            next.withSelectedProfile(OutputProfile.DEFAULT_ID)
        } else {
            next
        }
    }
    /**
     * The draft with a freshly measured round-trip latency in it.
     *
     * The number is written into the selected profile at the same time, not only when Save
     * applies the draft: the profile row on the settings screen reads its own latency, so a
     * measurement that lived only in [latencyMs] left the row still saying "not measured
     * yet" while the slider above it showed the new value (decision 162).
     */
    fun withCalibration(latencyMs: Float): SettingsDraft {
        val safe = latencyMs.coerceIn(AppSettings.LATENCY_MIN_MS, AppSettings.LATENCY_MAX_MS)
        return copy(
            latencyMs = safe,
            latencyCalibrated = true,
            outputProfiles = outputProfiles.map { profile ->
                if (profile.id == selectedProfileId) {
                    profile.copy(latencyMs = safe, latencyCalibrated = true)
                } else {
                    profile
                }
            },
        )
    }

    /**
     * The draft with a latency the learner dialled in on the slider.
     *
     * A hand-set number counts as calibrated: it is a whole round trip the learner is
     * claiming, so the engine must not add the measured output latency on top of it, the
     * same way it does not for a measured one (decision 158).
     */
    fun withLatency(latencyMs: Float): SettingsDraft = withCalibration(latencyMs)

    /** The draft with a new microphone gate, set by hand or measured (decision 158). */
    fun withMicThreshold(level: Float): SettingsDraft = copy(
        micThresholdLevel = MicThreshold.clamp(level),
    )

    /**
     * A hand on the click switch takes it off automatic, exactly as it does during an
     * attempt (decision 114).
     */
    fun withClickAudible(audible: Boolean): SettingsDraft = copy(
        clickAudible = audible,
        clickFollowsHeadphones = false,
    )

    /**
     * What the click switch shows: the stored choice, or the output state while the draft
     * is still on automatic (decision 114).
     */
    fun effectiveClickAudible(headphonesConnected: Boolean): Boolean =
        if (clickFollowsHeadphones) headphonesConnected else clickAudible

    fun applyTo(settings: AppSettings): AppSettings = settings.copy(
        clickAudible = clickAudible,
        clickFollowsHeadphones = clickFollowsHeadphones,
        showOffsetMs = showOffsetMs,
        inputLatencyMs = latencyMs,
        latencyCalibrated = latencyCalibrated,
        micThresholdLevel = micThresholdLevel,
        outputProfiles = outputProfiles.map { profile ->
            if (profile.id == selectedProfileId) {
                profile.copy(latencyMs = latencyMs, latencyCalibrated = latencyCalibrated)
            } else {
                profile
            }
        },
        selectedProfileId = selectedProfileId,
    ).sanitized()

    /**
     * True when Save would change something, so the button can say so. Compared against the
     * sanitized settings, because applying always sanitizes: a stored value that was out of
     * range, or an output list not written yet, is not an edit the learner made.
     */
    fun differsFrom(settings: AppSettings): Boolean =
        applyTo(settings) != settings.sanitized()

    companion object {
        fun from(settings: AppSettings): SettingsDraft = SettingsDraft(
            clickAudible = settings.clickAudible,
            clickFollowsHeadphones = settings.clickFollowsHeadphones,
            showOffsetMs = settings.showOffsetMs,
            latencyMs = settings.inputLatencyMs,
            latencyCalibrated = settings.latencyCalibrated,
            micThresholdLevel = settings.micThresholdLevel,
            outputProfiles = settings.sanitized().outputProfiles,
            selectedProfileId = settings.sanitized().selectedProfileId,
        )
    }
}
