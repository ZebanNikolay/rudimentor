package com.rudimentor.app.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EchoFilterTest {

    @Test
    fun weakSoundSoonAfterAStrokeIsAnEcho() {
        val filter = EchoFilter(enabled = true)
        assertNull(filter.check(1_000.0, 0.12f))
        val echo = filter.check(1_062.0, 0.018f)
        assertNotNull(echo)
        assertEquals(62f, echo!!.gapMs, 0.01f)
        assertEquals(0.15f, echo.ratio, 0.001f)
    }

    @Test
    fun disabledFilterKeepsEverything() {
        val filter = EchoFilter(enabled = false)
        assertNull(filter.check(1_000.0, 0.12f))
        assertNull(filter.check(1_062.0, 0.018f))
    }

    @Test
    fun fastestDoubleOfTheCourseIsKept() {
        // 83 ms is the shortest interval in the course; a double's second stroke is at
        // least half as loud as the first.
        val filter = EchoFilter(enabled = true)
        assertNull(filter.check(0.0, 0.12f))
        assertNull(filter.check(83.3, 0.06f))
        assertNull(filter.check(166.7, 0.12f))
    }

    @Test
    fun closeUnisonOfTwoSticksIsKept() {
        val filter = EchoFilter(enabled = true)
        assertNull(filter.check(0.0, 0.10f))
        assertNull(filter.check(30.0, 0.08f))
    }

    @Test
    fun secondEchoIsMeasuredAgainstTheStrokeNotTheFirstEcho() {
        val filter = EchoFilter(enabled = true)
        assertNull(filter.check(0.0, 0.13f))
        assertNotNull(filter.check(64.0, 0.02f))
        // Against the first echo this would be 60 % and kept; against the stroke it is 9 %.
        val second = filter.check(124.0, 0.012f)
        assertNotNull(second)
        assertEquals(124f, second!!.gapMs, 0.01f)
    }

    @Test
    fun quietSoundPastTheWindowIsAStroke() {
        val filter = EchoFilter(enabled = true)
        assertNull(filter.check(0.0, 0.13f))
        assertNull(filter.check(131.0, 0.02f))
    }

    @Test
    fun loudestRecentStrokeIsTheReference() {
        val filter = EchoFilter(enabled = true)
        assertNull(filter.check(0.0, 0.20f))
        assertNull(filter.check(90.0, 0.11f))
        // 45 % of the stroke just before, but 25 % of the loud one 120 ms ago.
        assertNotNull(filter.check(120.0, 0.05f))
    }

    @Test
    fun brokenEnvelopeIsNeitherEchoNorReference() {
        val filter = EchoFilter(enabled = true)
        assertNull(filter.check(0.0, Float.NaN))
        assertNull(filter.check(10.0, 0.01f))
        assertNotNull(filter.check(60.0, 0.002f))
    }

    // Real attempts recorded before the filter existed, replayed in log order. The expected
    // counts are the offline replay of analysis-2026-10-09: what was judged stays judged,
    // except in EN-03, where the 10 "judged" echoes are themselves echoes of a stroke that
    // landed 45-66 ms earlier and was charged as an extra.

    @Test
    fun is03StandEchoesAreRemovedAndStrokesKept() =
        assertReplay("is03", echoExtras = 93, echoJudged = 0, keptJudged = 127)

    @Test
    fun en03StandEchoesAreRemovedAndStrokesKept() =
        assertReplay("en03", echoExtras = 313, echoJudged = 10, keptJudged = 801)

    @Test
    fun un02UnisonKeepsEveryNote() =
        assertReplay("un02", echoExtras = 45, echoJudged = 0, keptJudged = 63)

    @Test
    fun st04KeepsEveryNote() =
        assertReplay("st04", echoExtras = 16, echoJudged = 0, keptJudged = 124)

    private fun assertReplay(name: String, echoExtras: Int, echoJudged: Int, keptJudged: Int) {
        val stream = checkNotNull(javaClass.getResourceAsStream("/echo/$name.csv"))
        val filter = EchoFilter(enabled = true)
        var extras = 0
        var judgedEchoes = 0
        var judgedKept = 0
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val (at, env, outcome) = line.split(",")
                val echo = filter.check(at.toDouble(), env.toFloat())
                when {
                    echo != null && outcome == "extra" -> extras += 1
                    echo != null && outcome == "judged" -> judgedEchoes += 1
                    echo == null && outcome == "judged" -> judgedKept += 1
                }
            }
        }
        assertEquals("$name echo extras", echoExtras, extras)
        assertEquals("$name echo judged", echoJudged, judgedEchoes)
        assertEquals("$name kept judged", keptJudged, judgedKept)
    }
}
