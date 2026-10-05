package com.rudimentor.app.data

import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.Bpm

/**
 * Every edit the metronome screen makes, as plain functions of the settings.
 *
 * Shared by the user's own metronome, which stores the result, and by a level opened on the
 * metronome, which keeps it for that visit only (decision 228): the same stepper has to do
 * the same thing on both.
 */
object MetronomeEdits {
    fun adjustBpm(settings: AppSettings, delta: Int): AppSettings =
        settings.copy(bpm = Bpm.adjust(settings.bpm, delta))

    fun selectRow(settings: AppSettings, rowIndex: Int): AppSettings =
        settings.copy(activeRow = rowIndex.mod(settings.grid.rowCount))

    // A new row duplicates the last one: the user usually wants a variation of what they
    // already have, not an empty row they must fill from scratch.
    fun addRow(settings: AppSettings): AppSettings = with(settings) {
        if (grid.rowCount >= BeatGrid.MAX_ROWS) {
            this
        } else {
            copy(grid = grid.withRowAppended(), activeRow = grid.rowCount)
        }
    }

    fun removeRow(settings: AppSettings): AppSettings = with(settings) {
        val target = (grid.rowCount - 1).coerceIn(BeatGrid.MIN_ROWS, BeatGrid.MAX_ROWS)
        val resized = grid.withRowCount(target)
        copy(grid = resized, activeRow = safeActiveRow.coerceIn(0, resized.rowCount - 1))
    }

    fun addBeat(settings: AppSettings, rowIndex: Int): AppSettings = withRowLength(settings, rowIndex, +1)

    fun removeBeat(settings: AppSettings, rowIndex: Int): AppSettings = withRowLength(settings, rowIndex, -1)

    // The row always comes from the caller: the edit lands on the row the user touched,
    // never on a stale "active row".
    fun cycleBeat(settings: AppSettings, rowIndex: Int, beatIndex: Int): AppSettings = with(settings) {
        copy(grid = grid.cycleState(rowIndex.coerceIn(0, grid.rowCount - 1), beatIndex))
    }

    fun toggleHand(settings: AppSettings, rowIndex: Int, beatIndex: Int): AppSettings = with(settings) {
        copy(grid = grid.toggleHand(rowIndex.coerceIn(0, grid.rowCount - 1), beatIndex))
    }

    private fun withRowLength(settings: AppSettings, rowIndex: Int, delta: Int): AppSettings = with(settings) {
        val index = rowIndex.coerceIn(0, grid.rowCount - 1)
        val target = (grid.rows[index].size + delta).coerceIn(BeatRow.MIN_BEATS, BeatRow.MAX_BEATS)
        copy(grid = grid.withRowLength(index, target))
    }
}
