package com.rudimentor.app.ui.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeLogCopyTest {
    @Test
    fun `copies complete text at the allowed boundary`() {
        val text = "x".repeat(MAX_LOG_CLIPBOARD_CHARS)
        var copied: String? = null
        val notice = copyPracticeLog(text, { copied = it }, { throw AssertionError(it) })
        assertEquals(text, copied)
        assertEquals("Summary and events copied.", notice)
    }

    @Test
    fun `oversize log is not truncated or sent to clipboard`() {
        var invoked = false
        val notice = copyPracticeLog(
            "x".repeat(MAX_LOG_CLIPBOARD_CHARS + 1),
            { invoked = true },
            { throw AssertionError(it) },
        )
        assertFalse(invoked)
        assertTrue(notice.contains("Use Share"))
    }

    @Test
    fun `clipboard failure is reported without success or crash`() {
        val error = IllegalStateException("clipboard unavailable")
        var reported: Exception? = null
        val notice = copyPracticeLog("small log", { throw error }, { reported = it })
        assertSame(error, reported)
        assertTrue(notice.startsWith("Could not copy"))
    }
}
