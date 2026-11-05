package com.rudimentor.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Test

class StickingWordSizeTest {
    @Test
    fun `binary stickings count in fours`() {
        assertEquals(4, stickingWordSize(2, hitsPerBeat = 2))
        assertEquals(4, stickingWordSize(8, hitsPerBeat = 4))
        assertEquals(4, stickingWordSize(12, hitsPerBeat = 4))
    }

    @Test
    fun `a sticking in threes or sixes counts in sixes`() {
        assertEquals(6, stickingWordSize(12, hitsPerBeat = 3))
        assertEquals(6, stickingWordSize(12, hitsPerBeat = 6))
        // Not a multiple of six: the old rule.
        assertEquals(4, stickingWordSize(8, hitsPerBeat = 3))
        assertEquals(3, stickingWordSize(3, hitsPerBeat = 3))
    }
}
