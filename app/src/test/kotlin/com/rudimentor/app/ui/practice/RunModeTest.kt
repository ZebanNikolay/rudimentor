package com.rudimentor.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Test

class RunModeTest {
    @Test
    fun `run modes have stable distinct names`() {
        assertEquals(listOf("Challenge", "Practice"), RunMode.entries.map { it.name })
    }

    @Test
    fun `summary keeps long duration and hit counts`() {
        val summary = PracticeSummary(durationMs = Long.MAX_VALUE, hits = Int.MAX_VALUE.toLong() + 1)
        assertEquals(Long.MAX_VALUE, summary.durationMs)
        assertEquals(2_147_483_648L, summary.hits)
    }

    @Test
    fun `duration ignores partial seconds and never displays negative time`() {
        assertEquals("00:00", formatPracticeDuration(-1))
        assertEquals("00:00", formatPracticeDuration(999))
        assertEquals("00:01", formatPracticeDuration(1_999))
        assertEquals("01:00", formatPracticeDuration(60_000))
    }

    @Test
    fun `long sessions display hours without wrapping minutes or overflowing`() {
        assertEquals("59:59", formatPracticeDuration(3_599_999))
        assertEquals("1:00:00", formatPracticeDuration(3_600_000))
        assertEquals("25:01:01", formatPracticeDuration(90_061_000))
        assertEquals("2562047788015:12:55", formatPracticeDuration(Long.MAX_VALUE))
    }
}
