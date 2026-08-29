package com.rudimentor.app.data

import com.rudimentor.app.audio.Beat
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.audio.Hand
import com.rudimentor.app.audio.MicLab
import com.rudimentor.app.audio.MicThreshold

/**
 * Everything the app remembers between runs: the metronome the user has built and
 * the two practice settings.
 *
 * The metronome grid, its tempo and the selected row belong to the user alone --
 * entering a level no longer overwrites them (decision 102).
 */
data class AppSettings(
    val grid: BeatGrid = BeatGrid.default(),
    val bpm: Int = Bpm.DEFAULT,
    val activeRow: Int = 0,
    val showHandLetters: Boolean = true,
    val clickAudible: Boolean = false,
    val clickFollowsHeadphones: Boolean = true,
    val inputLatencyMs: Float = MicLab.DEFAULT_LATENCY_MS,
    /**
     * True once the latency above was measured by the calibration screen instead of
     * guessed. A measured value is the whole round trip -- click written, sound heard,
     * stick hit, onset detected -- so the engine must not add the output latency on top
     * of it a second time (decision 154).
     */
    val latencyCalibrated: Boolean = false,
    /**
     * Loudness an onset has to reach before it is treated as a stroke, as an envelope
     * level. Room noise and a real stroke are 25-80x apart, and without this gate the
     * detector scored the noise (decision 158). Zero means no gate.
     */
    val micThresholdLevel: Float = MicThreshold.DEFAULT_LEVEL,
    /**
     * Whether the verdict floater spells the deviation in milliseconds under its word.
     * Off by default: the dot on the rail already shows which side of the note the
     * stroke landed on, and the numbers pulled the eye off the lane (decision 130).
     */
    val showOffsetMs: Boolean = false,
    /**
     * Saved outputs with their own latency, newest use first after the built-in one
     * (decision 161). [inputLatencyMs] and [latencyCalibrated] above always mirror the
     * selected profile: the engine reads them and knows nothing about profiles.
     */
    // Empty means "not stored yet": the built-in profile is then built from the latency
    // fields above, which is what carries an install from before decision 161 across.
    val outputProfiles: List<OutputProfile> = emptyList(),
    val selectedProfileId: String = OutputProfile.DEFAULT_ID,
    /**
     * True once the sound check on the level map has been walked through to the end. It only
     * decides how the node is drawn and whether it is offered on its own; the check itself
     * can always be replayed (decision 169).
     */
    val soundCheckDone: Boolean = false,
    /**
     * True once the learner has closed the plate that calls for the sound check. The check
     * itself stays on the map as its own node, so hiding the call never hides the way in
     * (decision 171).
     */
    val soundCheckPlateHidden: Boolean = false,
) {
    val safeActiveRow: Int = activeRow.coerceIn(0, grid.rowCount - 1)

    /**
     * The click state the practice engine should actually use.
     *
     * While [clickFollowsHeadphones] is on the click simply follows the output:
     * private on headphones, silent on the speaker, where the microphone would hear
     * it and score it as a stroke (decision 88). Touching the switch by hand turns
     * the following off and [clickAudible] wins from then on (decision 114).
     */
    fun clickAudibleWith(headphonesConnected: Boolean): Boolean =
        if (clickFollowsHeadphones) headphonesConnected else clickAudible

    /**
     * Return a copy with every value forced into the domain-allowed range.
     * Called before writing to storage so a bad in-memory value never persists.
     */
    fun sanitized(): AppSettings {
        val profiles = normalizedProfiles()
        val selected = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()
        return copy(
            bpm = Bpm.clamp(bpm),
            activeRow = safeActiveRow,
            // The selected profile is the single source of truth for the latency, so a value
            // written straight into the field cannot drift away from the list.
            inputLatencyMs = selected.latencyMs.coerceIn(LATENCY_MIN_MS, LATENCY_MAX_MS),
            latencyCalibrated = selected.latencyCalibrated,
            micThresholdLevel = MicThreshold.clamp(micThresholdLevel),
            outputProfiles = profiles,
            selectedProfileId = selected.id,
        )
    }

    /** The profile the latency currently comes from. */
    val selectedProfile: OutputProfile
        get() = sanitized().let { safe ->
            safe.outputProfiles.first { it.id == safe.selectedProfileId }
        }

    /**
     * The list as it is allowed to be stored: the built-in profile first and present, no
     * duplicate ids or bindings, and at most [OutputProfile.MAX_PROFILES] entries. When the
     * list is too long the profile used longest ago is dropped -- the built-in one never is.
     */
    private fun normalizedProfiles(): List<OutputProfile> {
        val cleaned = outputProfiles.map { it.sanitized() }
        val builtIn = cleaned.firstOrNull { it.id == OutputProfile.DEFAULT_ID }
            ?: OutputProfile.default(inputLatencyMs, latencyCalibrated)
        val seenIds = mutableSetOf(builtIn.id)
        val seenKeys = mutableSetOf<String>()
        val extras = cleaned
            .filter { it.id != OutputProfile.DEFAULT_ID }
            .filter { profile ->
                val key = profile.boundKey
                seenIds.add(profile.id) && (key == null || seenKeys.add(key))
            }
            .sortedByDescending { it.lastUsedAt }
            .take(OutputProfile.MAX_PROFILES - 1)
        return listOf(builtIn.copy(kind = OutputKind.Default, boundKey = null)) + extras
    }

    /** The profile bound to [device], if one was saved for it. */
    fun profileFor(device: OutputDevice): OutputProfile? =
        outputProfiles.firstOrNull { it.boundKey == device.key }

    /** Select a profile, remembering when it was last used so eviction stays fair. */
    fun withSelectedProfile(id: String, now: Long): AppSettings {
        val target = outputProfiles.firstOrNull { it.id == id } ?: return this
        return copy(
            selectedProfileId = target.id,
            outputProfiles = outputProfiles.map {
                if (it.id == target.id) it.copy(lastUsedAt = now) else it
            },
        ).sanitized()
    }

    companion object {
        /**
         * Range of the latency compensation, in milliseconds. The ceiling has to hold a
         * full Bluetooth round trip: A2DP headphones measured 230 ms on the device, and
         * the old 80 ms ceiling made the calibrated value unreachable (decision 154).
         *
         * It is kept equal to [com.rudimentor.app.audio.LatencyCalibration.MAX_PLAUSIBLE_MS]:
         * while the measurement accepted up to 600 ms and this clamp stopped at 400 ms, a
         * slower pair of headphones could be measured honestly and then silently truncated
         * on the way into storage.
         */
        const val LATENCY_MIN_MS = 0f
        const val LATENCY_MAX_MS = 600f
    }
}

/**
 * The grid is stored as one compact string: rows are separated by `|`, and each
 * row is `states:hands`, e.g. `1000:RLRL|100000:RLRLRR`.
 */
internal fun BeatGrid.serialize(): String = rows.joinToString(separator = ROW_SEPARATOR) { row ->
    val states = row.beats.map { it.state.code }.joinToString(separator = "")
    val hands = row.beats.map { it.hand.label }.joinToString(separator = "")
    "$states$FIELD_SEPARATOR$hands"
}

internal fun parseGrid(raw: String?): BeatGrid {
    if (raw.isNullOrBlank()) return BeatGrid.default()
    val rows = raw.split(ROW_SEPARATOR).mapNotNull(::parseRow)
    if (rows.isEmpty() || rows.size > BeatGrid.MAX_ROWS) return BeatGrid.default()
    return BeatGrid(rows)
}

private fun parseRow(raw: String): BeatRow? {
    val parts = raw.split(FIELD_SEPARATOR)
    if (parts.size != 2) return null
    val states = parts[0]
    val hands = parts[1]
    if (states.isEmpty() || states.length != hands.length) return null
    if (states.length > BeatRow.MAX_BEATS) return null
    if (states.any { it !in "012" } || hands.any { it != 'R' && it != 'L' }) return null

    return BeatRow(
        states.mapIndexed { index, code ->
            Beat(
                state = BeatState.fromCode(code),
                hand = Hand.fromLabel(hands[index]),
            )
        },
    )
}

private const val ROW_SEPARATOR = "|"
private const val FIELD_SEPARATOR = ":"
