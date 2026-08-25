package com.rudimentor.app.data

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
) {
    /** The draft with a freshly measured round-trip latency in it. */
    fun withCalibration(latencyMs: Float): SettingsDraft = copy(
        latencyMs = latencyMs.coerceIn(AppSettings.LATENCY_MIN_MS, AppSettings.LATENCY_MAX_MS),
        latencyCalibrated = true,
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
    )

    /** True when Save would change something, so the button can say so. */
    fun differsFrom(settings: AppSettings): Boolean = applyTo(settings) != settings

    companion object {
        fun from(settings: AppSettings): SettingsDraft = SettingsDraft(
            clickAudible = settings.clickAudible,
            clickFollowsHeadphones = settings.clickFollowsHeadphones,
            showOffsetMs = settings.showOffsetMs,
            latencyMs = settings.inputLatencyMs,
            latencyCalibrated = settings.latencyCalibrated,
        )
    }
}
