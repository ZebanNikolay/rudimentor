package com.rudimentor.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BeatIndicatorStyleTest {
    @Test
    fun `every style restores from its saveable value`() {
        BeatIndicatorStyle.entries.forEach { style ->
            assertEquals(style, BeatIndicatorStyle.fromSavedValue(style.name))
        }
    }

    @Test
    fun `missing or obsolete saved style uses the production default`() {
        assertEquals(BeatIndicatorStyle.Default, BeatIndicatorStyle.fromSavedValue(null))
        assertEquals(BeatIndicatorStyle.Default, BeatIndicatorStyle.fromSavedValue("RemovedPrototype"))
    }
}
