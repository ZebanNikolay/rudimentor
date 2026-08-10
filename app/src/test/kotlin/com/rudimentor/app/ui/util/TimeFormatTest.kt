package com.rudimentor.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun `zero formats as double-zero minutes and seconds`() {
        assertEquals("00:00", formatElapsed(0))
    }

    @Test
    fun `single seconds pad to two digits`() {
        assertEquals("00:07", formatElapsed(7))
    }

    @Test
    fun `values below one minute keep the minute slot as zero`() {
        assertEquals("00:59", formatElapsed(59))
    }

    @Test
    fun `one minute rolls the seconds slot back to zero`() {
        assertEquals("01:00", formatElapsed(60))
    }

    @Test
    fun `over one hour keeps counting minutes past sixty`() {
        assertEquals("61:00", formatElapsed(61 * 60))
    }

    @Test
    fun `a negative delta clamps to zero instead of showing a minus sign`() {
        assertEquals("00:00", formatElapsed(-42))
    }
}
