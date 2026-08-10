package com.rudimentor.app.data

import com.rudimentor.app.audio.Beat
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.audio.Hand

data class AppSettings(
    val grid: BeatGrid = BeatGrid.default(),
    val bpm: Int = Bpm.DEFAULT,
    val activeRow: Int = 0,
    val showHandLetters: Boolean = true,
) {
    val safeActiveRow: Int = activeRow.coerceIn(0, grid.rowCount - 1)
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
