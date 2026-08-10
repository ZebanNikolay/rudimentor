package com.rudimentor.app.data

import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Hand
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun `the drum survives a round trip through storage`() {
        val grid = BeatGrid.default()
            .cycleState(1, 2)
            .toggleHand(1, 3)
            .withRowLength(2, 7)

        val restored = parseGrid(grid.serialize())

        assertEquals(grid, restored)
        assertEquals("1000:RLRL", grid.serialize().split("|").first())
    }

    @Test
    fun `a corrupt or empty grid falls back to the default drum`() {
        assertEquals(BeatGrid.default(), parseGrid(null))
        assertEquals(BeatGrid.default(), parseGrid(""))
        assertEquals(BeatGrid.default(), parseGrid("1000"))
        assertEquals(BeatGrid.default(), parseGrid("1000:RL"))
        assertEquals(BeatGrid.default(), parseGrid("19:RL"))
        assertEquals(BeatGrid.default(), parseGrid("10:RX"))
        assertEquals(BeatGrid.default(), parseGrid(("1:R|").repeat(9).dropLast(1)))
    }

    @Test
    fun `a stored grid keeps states and hands per beat`() {
        val grid = parseGrid("102:RLL|10:RR")

        assertEquals(2, grid.rowCount)
        assertEquals(
            listOf(BeatState.Accent, BeatState.Normal, BeatState.Mute),
            grid.rows[0].beats.map { it.state },
        )
        assertEquals(
            listOf(Hand.Right, Hand.Left, Hand.Left),
            grid.rows[0].beats.map { it.hand },
        )
        assertEquals(2, grid.rows[1].size)
    }

    @Test
    fun `the active row never points past the drum`() {
        val settings = AppSettings(grid = BeatGrid.default().withRowCount(2), activeRow = 5)

        assertEquals(1, settings.safeActiveRow)
    }
}
