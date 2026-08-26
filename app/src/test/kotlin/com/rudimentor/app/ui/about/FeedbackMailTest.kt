package com.rudimentor.app.ui.about

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feedback draft has to arrive prefilled in every mail app, so subject and body
 * travel inside the `mailto:` URI and not only as intent extras. These tests guard the
 * encoding of that URI: a '+' instead of %20, or a raw newline, and mail apps quietly
 * open an empty draft -- exactly the bug this replaced.
 */
class FeedbackMailTest {

    @Test
    fun `subject and body live in the uri`() {
        val uri = FeedbackMail.mailtoWith("RudiMentor feedback", "Line one\nLine two")

        assertTrue(uri.startsWith("mailto:${FeedbackMail.ADDRESS}?subject="))
        assertTrue(uri.contains("subject=RudiMentor%20feedback"))
        assertTrue(uri.contains("&body=Line%20one%0ALine%20two"))
    }

    @Test
    fun `spaces never become plus signs`() {
        val uri = FeedbackMail.mailtoWith("a b", "c d")

        assertTrue(uri, !uri.contains("+"))
    }
}
