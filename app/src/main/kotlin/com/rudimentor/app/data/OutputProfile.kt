package com.rudimentor.app.data

/**
 * One saved output the learner practises through, with the latency measured for it.
 *
 * The round trip a stroke takes is a property of the output, not of the player: wired
 * headphones land near 30 ms, A2DP Bluetooth measured 230-270 ms on this phone. With one
 * shared number the learner had to recalibrate every time they swapped headphones, and a
 * forgotten swap silently ruined an attempt (decision 161).
 *
 * The profile is chosen by hand on the settings screen, and the calibration screen writes
 * into whichever profile is chosen. Deleting a profile deletes its calibration with it.
 * [DEFAULT_ID] is the built-in one: it always exists, always sits first and cannot be
 * removed, so there is always something to fall back to.
 */
data class OutputProfile(
    val id: String,
    val name: String,
    val kind: OutputKind,
    /**
     * The output this profile belongs to, as [OutputDevice.key] reports it, or null for the
     * built-in profile, which belongs to no particular output. Matching on it is what lets
     * a known pair of headphones select its own profile when it connects.
     */
    val boundKey: String?,
    val latencyMs: Float,
    val latencyCalibrated: Boolean,
    /** Millis since epoch of the last time this profile was selected. Drives eviction. */
    val lastUsedAt: Long,
) {
    val removable: Boolean = id != DEFAULT_ID

    fun sanitized(): OutputProfile = copy(
        name = cleanName(name).ifBlank { kind.fallbackName },
        latencyMs = latencyMs.coerceIn(AppSettings.LATENCY_MIN_MS, AppSettings.LATENCY_MAX_MS),
    )

    companion object {
        /** The built-in profile: first in the list, never deleted. */
        const val DEFAULT_ID = "default"

        /**
         * How many profiles are kept, the built-in one included. Three is what the learner
         * asked for: the default, plus the two pairs of headphones actually in use.
         */
        const val MAX_PROFILES = 3

        fun default(
            latencyMs: Float,
            latencyCalibrated: Boolean,
            lastUsedAt: Long = 0L,
        ): OutputProfile = OutputProfile(
            id = DEFAULT_ID,
            name = OutputKind.Default.fallbackName,
            kind = OutputKind.Default,
            boundKey = null,
            latencyMs = latencyMs,
            latencyCalibrated = latencyCalibrated,
            lastUsedAt = lastUsedAt,
        )

        /** A profile for an output that is connected right now. */
        fun forDevice(
            device: OutputDevice,
            latencyMs: Float,
            latencyCalibrated: Boolean,
            now: Long,
        ): OutputProfile = OutputProfile(
            id = "out-$now",
            name = cleanName(device.name).ifBlank { device.kind.fallbackName },
            kind = device.kind,
            boundKey = device.key,
            latencyMs = latencyMs,
            latencyCalibrated = latencyCalibrated,
            lastUsedAt = now,
        ).sanitized()

        /** Longest name a profile may carry, so the row on the settings screen fits. */
        const val MAX_NAME_LENGTH = 24

        /**
         * Names are stored in one preferences string, so the separators of that format are
         * stripped rather than escaped: a pair of headphones called `a~b` is not worth a
         * parser.
         */
        fun cleanName(raw: String): String = raw
            .replace(Regex("[~;|\\r\\n\\t]"), " ")
            .trim()
            .take(MAX_NAME_LENGTH)
    }
}

/** The kind of output a profile belongs to. Decides the badge shown on its row. */
enum class OutputKind(val code: String, val fallbackName: String) {
    Default("d", "Default"),
    Bluetooth("b", "Bluetooth"),
    Wired("w", "Wired"),
    Usb("u", "USB"),
    ;

    companion object {
        fun fromCode(code: String?): OutputKind =
            entries.firstOrNull { it.code == code } ?: Default
    }
}

/**
 * An output connected right now, as the audio device list reports it.
 *
 * [key] is what a profile is bound to: the device type plus its product name. The MAC
 * address would be a better key, but reading it -- or the Bluetooth name itself -- needs the
 * Nearby devices permission from Android 12 on, and calibration is not worth that prompt.
 * A device that reports no name of its own falls back to its type, which is why two unnamed
 * wired headsets share one profile.
 */
data class OutputDevice(
    val kind: OutputKind,
    val name: String,
    val key: String,
)

/**
 * Profiles are stored as one string: entries separated by `;`, fields by `~`. The same
 * shape the beat grid uses, for the same reason -- one preferences key, no schema to
 * migrate, and a corrupt entry can be dropped on its own.
 */
internal fun List<OutputProfile>.serialize(): String = joinToString(separator = ";") { p ->
    listOf(
        p.id,
        p.name,
        p.kind.code,
        p.boundKey ?: "",
        p.latencyMs.toString(),
        if (p.latencyCalibrated) "1" else "0",
        p.lastUsedAt.toString(),
    ).joinToString(separator = "~")
}

internal fun parseProfiles(
    raw: String?,
    fallbackLatencyMs: Float,
    fallbackCalibrated: Boolean,
): List<OutputProfile> {
    // No profiles stored yet means an install from before decision 161: the latency it
    // already has becomes the built-in profile, so nobody loses a calibration on update.
    if (raw.isNullOrBlank()) {
        return listOf(OutputProfile.default(fallbackLatencyMs, fallbackCalibrated))
    }
    val parsed = raw.split(";").mapNotNull(::parseProfile)
    return parsed.ifEmpty {
        listOf(OutputProfile.default(fallbackLatencyMs, fallbackCalibrated))
    }
}

private fun parseProfile(raw: String): OutputProfile? {
    val parts = raw.split("~")
    if (parts.size != 7) return null
    val id = parts[0].takeIf { it.isNotBlank() } ?: return null
    val latency = parts[4].toFloatOrNull() ?: return null
    val boundKey = parts[3].takeIf { it.isNotBlank() }
    return OutputProfile(
        id = id,
        name = parts[1],
        kind = OutputKind.fromCode(parts[2]),
        boundKey = boundKey,
        latencyMs = latency,
        latencyCalibrated = parts[5] == "1",
        lastUsedAt = parts[6].toLongOrNull() ?: 0L,
    ).sanitized()
}
