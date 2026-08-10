package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BeatGridTest {
    @Test
    fun `the default drum has four rows of uneven length with accented first beats`() {
        val grid = BeatGrid.default()

        assertEquals(listOf(4, 6, 4, 3), grid.rows.map { it.size })
        grid.rows.forEach { row ->
            assertEquals(BeatState.Accent, row.beats.first().state)
            assertEquals(1, row.beats.count { it.state == BeatState.Accent })
        }
        assertEquals("RLRLRRLL", BeatRow.default(8).beats.joinToString("") { it.hand.label })
    }

    @Test
    fun `playback walks every row before looping back`() {
        val grid = BeatGrid.default()

        assertEquals(17, grid.totalSteps)
        assertEquals(BeatGrid.Position(0, 0), grid.locate(0))
        assertEquals(BeatGrid.Position(1, 0), grid.locate(4))
        assertEquals(BeatGrid.Position(3, 2), grid.locate(16))
        assertEquals(BeatGrid.Position(0, 0), grid.locate(17))
    }

    @Test
    fun `the sequence sent to the engine carries state and hand per step`() {
        val grid = BeatGrid(
            listOf(
                BeatRow(
                    listOf(
                        Beat(BeatState.Accent, Hand.Right),
                        Beat(BeatState.Normal, Hand.Left),
                        Beat(BeatState.Mute, Hand.Left),
                    ),
                ),
            ),
        )

        assertEquals(listOf(1, 4, 6), grid.toSequence().toList())
    }

    @Test
    fun `a short tap cycles a beat and a long press flips the hand`() {
        val grid = BeatGrid.default()

        assertEquals(BeatState.Mute, grid.cycleState(0, 0).rows[0].beats[0].state)
        assertEquals(BeatState.Accent, grid.cycleState(0, 1).rows[0].beats[1].state)
        assertEquals(Hand.Left, grid.toggleHand(0, 0).rows[0].beats[0].hand)
        assertEquals(grid, grid.cycleState(0, 99))
    }

    @Test
    fun `resizing keeps existing beats and clamps to the supported bounds`() {
        val grid = BeatGrid.default()

        assertEquals(2, grid.withRowLength(0, 2).rows[0].size)
        assertEquals(BeatRow.MAX_BEATS, grid.withRowLength(0, 99).rows[0].size)
        assertEquals(
            BeatState.Accent,
            grid.withRowLength(0, 12).rows[0].beats[0].state,
        )
        assertEquals(BeatGrid.MIN_ROWS, grid.withRowCount(0).rowCount)
        assertEquals(BeatGrid.MAX_ROWS, grid.withRowCount(99).rowCount)
        assertEquals(grid.rows[0], grid.withRowCount(6).rows[0])
    }
}
