package com.rudimentor.app.data

import com.rudimentor.app.audio.Hand
import com.rudimentor.app.ui.BeatIndicatorStyle
import com.rudimentor.app.ui.theme.PaletteId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun `persisted settings restore style palette mode and independent beats`() {
        val settings = parseSettings(
            trackerStyle = BeatIndicatorStyle.RingSweep.name,
            paletteId = PaletteId.P6.name,
            mode = PatternMode.Abstract.name,
            patternLength = 6,
            accents = "101001",
            hands = "RRLLRL",
        )

        assertEquals(BeatIndicatorStyle.RingSweep, settings.trackerStyle)
        assertEquals(PaletteId.P6, settings.paletteId)
        assertEquals(PatternMode.Abstract, settings.mode)
        assertEquals(listOf(true, false, true, false, false, true), settings.pattern.accents)
        assertEquals(
            listOf(Hand.Right, Hand.Right, Hand.Left, Hand.Left, Hand.Right, Hand.Left),
            settings.pattern.hands,
        )
    }

    @Test
    fun `invalid persisted fields recover independently to safe defaults`() {
        val settings = parseSettings(
            trackerStyle = "removed-style",
            paletteId = "P7",
            mode = "invalid-mode",
            patternLength = 99,
            accents = "corrupt",
            hands = "RR",
        )

        assertEquals(BeatIndicatorStyle.Default, settings.trackerStyle)
        assertEquals(PaletteId.Default, settings.paletteId)
        assertEquals(PatternMode.RightLeft, settings.mode)
        assertEquals(8, settings.pattern.size)
        assertEquals(listOf(true, false, false, false, false, false, false, false), settings.pattern.accents)
        assertEquals("RLRLRLRL", settings.pattern.serializedHands())
    }
}
