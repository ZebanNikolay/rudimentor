package com.rudimentor.app.data

import com.rudimentor.app.audio.Beat
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.audio.Hand
import com.rudimentor.app.audio.MicLab

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
     * Whether the verdict floater spells the deviation in milliseconds under its word.
     * Off by default: the dot on the rail already shows which side of the note the
     * stroke landed on, and the numbers pulled the eye off the lane (decision 130).
     */
    val showOffsetMs: Boolean = false,
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
    fun sanitized(): AppSettings = copy(
        bpm = Bpm.clamp(bpm),
        activeRow = safeActiveRow,
        inputLatencyMs = inputLatencyMs.coerceIn(LATENCY_MIN_MS, LATENCY_MAX_MS),
    )

    companion object {
        /** The slider range of the input-latency compensation, in milliseconds. */
        const val LATENCY_MIN_MS = 0f
        const val LATENCY_MAX_MS = 80f
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
