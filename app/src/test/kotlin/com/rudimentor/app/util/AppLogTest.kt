package com.rudimentor.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What leaves the device in a feedback mail is decided here, so this is the test that
 * matters: a debug-only line must never reach the letter, and a long session must not
 * produce an attachment no mail app will send.
 */
class AppLogTest {

    private fun event(tag: String, message: String) = "09-01 20:00:00.000 $tag: $message"

    private fun trace(tag: String, message: String) =
        "09-01 20:00:00.000 ${AppLog.TRACE_MARK}$tag: $message"

    @Test
    fun `debug lines never reach the report`() {
        val lines = listOf(
            event("audio", "output HEADPHONES"),
            trace("nav", "screen=Practice level=singles-07"),
            trace("practice", "finished singles-07 accuracy=91%"),
            event("calibration", "stored latency 42 ms"),
        )

        val text = AppLog.diagnosticOf(lines, maxChars = 10_000)

        assertEquals("09-01 20:00:00.000 audio: output HEADPHONES\n" +
            "09-01 20:00:00.000 calibration: stored latency 42 ms", text)
        assertFalse(text, text.contains("singles-07"))
    }

    @Test
    fun `the tail is kept and cut on a line boundary`() {
        val lines = (1..500).map { event("audio", "line $it") }

        val text = AppLog.diagnosticOf(lines, maxChars = 500)

        assertTrue(text.length.toString(), text.length <= 500)
        assertTrue(text, text.endsWith("line 500"))
        assertFalse(text, text.startsWith("09-01 20:00:00.000 audio: line 1\n"))
        // Cut on a newline, so the report never opens mid-timestamp.
        assertTrue(text, text.first().isDigit())
    }

    @Test
    fun `a short log is passed through whole`() {
        val lines = listOf(event("app", "session start"), event("mic", "permission denied"))

        assertEquals(lines.joinToString("\n"), AppLog.diagnosticOf(lines, maxChars = 10_000))
    }
}
