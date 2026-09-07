package com.rudimentor.app.telemetry

import com.rudimentor.app.ui.practice.HitOutcome
import com.rudimentor.app.ui.practice.HitWindow
import com.rudimentor.app.ui.practice.HitWindows
import com.rudimentor.app.ui.practice.NoteJudgement
import com.rudimentor.app.ui.practice.PracticeResult
import com.rudimentor.app.ui.practice.PracticeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Endless runs keep bounded diagnostic prefixes, not a fabricated finite result. */
class PracticeLoopTelemetryTest {

    @Test
    fun `twenty thousand events bound all four sample series but keep cumulative counts`() {
        val telemetry = PracticeTelemetry(header().copy(runMode = "Practice"))
        repeat(10_000) { index ->
            val inPrefix = index < PracticeTelemetry.MAX_EVENTS
            telemetry.hit(
                atMs = index * 20f,
                outcome = judged(
                    noteIndex = index % 32,
                    offsetMs = if (inPrefix) 4f else 80f,
                    rawOffsetMs = if (inPrefix) 9f else 85f,
                ),
                envelope = if (inPrefix) 0.04f else 0.8f,
                threshold = 0.02f,
                peak = 0.2f,
            )
            telemetry.quiet(
                atMs = index * 20f + 1f,
                envelope = if (inPrefix) 0.01f else 0.019f,
                threshold = 0.005f,
                gate = 0.02f,
            )
        }
        telemetry.finishPractice(
            atMs = 200_000f,
            summary = PracticeSummary(durationMs = 200_000L, hits = 10_000L),
            audio = null,
            aborted = false,
        )

        val summary = telemetry.summary()
        assertTrue(summary.contains("duration 200000 ms · hits 10000"))
        assertTrue(summary.contains("detector matched 10000"))
        assertTrue(summary.contains("quiet 10000"))
        val prefix = "[prefix samples 4000/10000; truncated]"
        assertEquals(4, Regex(Regex.escape(prefix)).findAll(summary).count())
        assertTrue(summary.contains("drift $prefix median +4 ms · spread 0 ms · 4000 strokes"))
        assertTrue(summary.contains("raw $prefix median +9 ms · spread 0 ms"))
        assertTrue(summary.contains("hits $prefix 0.0400..0.0400"))
        assertTrue(summary.contains("refused 10000 $prefix 0.0100..0.0100"))
        assertFalse(summary.contains("+80 ms"))
        assertFalse(summary.contains("+85 ms"))

        val lines = telemetry.jsonLines()
        // Two headers, the bounded event prefix, one mandatory close, and its truncation tally.
        assertEquals(PracticeTelemetry.MAX_EVENTS + 4, lines.size)
        assertEquals(
            PracticeTelemetry.MAX_EVENTS,
            lines.count { it.contains("\"type\":\"hit\"") || it.contains("\"type\":\"quiet\"") },
        )
        assertTrue(lines[lines.lastIndex - 1].contains("\"type\":\"practiceEnd\""))
        assertEquals("{\"type\":\"truncated\",\"droppedEvents\":16000}", lines.last())
    }

    @Test
    fun `a smaller event cap also bounds each diagnostic prefix`() {
        val telemetry = PracticeTelemetry(header().copy(runMode = "Practice"), maxEvents = 2)
        repeat(5) { index ->
            val offset = if (index < 2) 2f + index * 2f else 100f
            telemetry.hit(
                atMs = index * 20f,
                outcome = judged(offsetMs = offset, rawOffsetMs = offset + 8f),
                envelope = if (index < 2) 0.04f else 0.8f,
                threshold = 0.02f,
                peak = 0.2f,
            )
            telemetry.quiet(
                atMs = index * 20f + 1f,
                envelope = if (index < 2) 0.01f else 0.019f,
                threshold = 0.005f,
                gate = 0.02f,
            )
        }

        val summary = telemetry.summary()
        val prefix = "[prefix samples 2/5; truncated]"
        assertTrue(summary.contains("drift $prefix median +3 ms · spread 1 ms · 2 strokes"))
        assertTrue(summary.contains("raw $prefix median +11 ms · spread 1 ms · profile +11"))
        assertTrue(summary.contains("hits $prefix 0.0400..0.0400"))
        assertTrue(summary.contains("refused 5 $prefix 0.0100..0.0100"))
        assertTrue(summary.contains("detector matched 5"))
    }

    @Test
    fun `practice end and abort survive truncation and preserve Long totals exactly`() {
        for (aborted in listOf(false, true)) {
            val telemetry = PracticeTelemetry(header().copy(runMode = "Practice"), maxEvents = 1)
            repeat(3) { index ->
                telemetry.hit(index * 20f, HitOutcome.Debounced(10f), 0.04f, 0.02f, 0.2f)
            }
            telemetry.finishPractice(
                atMs = 123_456f,
                summary = PracticeSummary(durationMs = 4_294_967_299L, hits = 2_147_483_653L),
                audio = audio().copy(outputXRuns = 7, inputXRuns = 8),
                aborted = aborted,
            )

            val type = if (aborted) "practiceAbort" else "practiceEnd"
            val lines = telemetry.jsonLines()
            assertEquals(5, lines.size)
            assertEquals(
                "{\"type\":\"$type\",\"atMs\":123456.0,\"mode\":\"Practice\"," +
                    "\"durationMs\":4294967299,\"hits\":2147483653}",
                lines[3],
            )
            assertEquals("{\"type\":\"truncated\",\"droppedEvents\":2}", lines.last())
            assertTrue(telemetry.summary().contains("duration 4294967299 ms · hits 2147483653"))
            assertTrue(telemetry.summary().contains("debounced 3"))
            assertTrue(telemetry.summary().contains("xruns 7/8"))
            assertEquals(aborted, telemetry.summary().contains("ABORTED at 123456 ms"))
        }
    }

    @Test
    fun `free summaries and closing JSON never invent a result score or progress`() {
        val telemetry = PracticeTelemetry(header().copy(runMode = "Practice"))
        telemetry.finishPractice(3_000f, PracticeSummary(3_000L, 0L), null, aborted = false)

        val summary = telemetry.summary()
        assertTrue(summary.contains("mode Practice"))
        assertTrue(summary.contains("32 template notes"))
        assertTrue(summary.contains("Practice ended · duration 3000 ms · hits 0"))
        assertTrue(summary.contains("clocks not sampled"))
        assertTrue(summary.contains("picture not reported"))
        assertTrue(telemetry.title().contains("Practice ended · duration 3000 ms · hits 0"))
        val body = telemetry.jsonLines().joinToString("\n")
        assertTrue(body.contains("\"mode\":\"Practice\""))
        for (field in listOf("accuracy", "stars", "score", "crown", "maxCombo", "progress")) {
            assertFalse(body.contains("\"$field\":"))
        }
        assertFalse(body.contains("\"type\":\"result\""))
        assertFalse(body.contains("\"type\":\"abort\""))
        for (word in listOf("result", "score", "accuracy", "stars", "combo", "progress", "★", "%")) {
            assertFalse(summary.contains(word, ignoreCase = true))
            assertFalse(telemetry.title().contains(word, ignoreCase = true))
        }
    }

    @Test
    fun `absolute timestamps and template note indices survive while unknown extra offset is null`() {
        val telemetry = PracticeTelemetry(header().copy(runMode = "Practice"))
        telemetry.hit(
            atMs = 123_456f,
            outcome = judged(noteIndex = 31, offsetMs = -4f, rawOffsetMs = 12f),
            envelope = 0.04f,
            threshold = 0.02f,
            peak = 0.2f,
        )
        telemetry.hit(
            atMs = 123_499f,
            outcome = HitOutcome.Extra(positionMs = 123_499f),
            envelope = 0.03f,
            threshold = 0.02f,
            peak = 0.2f,
        )

        val lines = telemetry.jsonLines()
        assertTrue(lines[2].contains("\"atMs\":123456.0"))
        assertTrue(lines[2].contains("\"note\":31"))
        assertTrue(lines[2].contains("\"offsetMs\":-4.0"))
        assertTrue(lines[2].contains("\"rawOffsetMs\":12.0"))
        assertTrue(lines[3].contains("\"atMs\":123499.0"))
        assertTrue(lines[3].contains("\"outcome\":\"extra\",\"offsetMs\":null"))
        assertFalse(lines.any { it.contains("NaN") })
        assertTrue(telemetry.summary().contains("drift [prefix samples 1/1] median -4 ms"))
        assertTrue(telemetry.summary().contains("raw [prefix samples 1/1] median +12 ms"))
        assertTrue(telemetry.summary().contains("detector matched 1 · extra 1"))
    }

    @Test
    fun `zero cap retains no diagnostic samples but still closes and counts the run`() {
        val telemetry = PracticeTelemetry(header().copy(runMode = "Practice"), maxEvents = 0)
        telemetry.hit(1_000f, judged(), 0.04f, 0.02f, 0.2f)
        telemetry.quiet(1_010f, 0.01f, 0.005f, 0.02f)
        telemetry.finishPractice(2_000f, PracticeSummary(2_000L, 1L), null, aborted = false)

        assertEquals(4, telemetry.jsonLines().size)
        assertEquals("{\"type\":\"truncated\",\"droppedEvents\":2}", telemetry.jsonLines().last())
        assertEquals(4, Regex("prefix samples 0/1; truncated").findAll(telemetry.summary()).count())
        assertTrue(telemetry.summary().contains("detector matched 1"))
        assertTrue(telemetry.summary().contains("quiet 1"))
    }

    @Test
    fun `closing practice twice keeps only its first terminal event`() {
        val telemetry = PracticeTelemetry(header().copy(runMode = "Practice"), maxEvents = 0)
        telemetry.finishPractice(1_000f, PracticeSummary(1_000L, 2L), null, aborted = true)
        repeat(10) {
            telemetry.finishPractice(2_000f, PracticeSummary(2_000L, 3L), null, aborted = false)
        }

        assertEquals(3, telemetry.jsonLines().size)
        assertTrue(telemetry.jsonLines().last().contains("\"type\":\"practiceAbort\""))
        assertTrue(telemetry.summary().contains("Practice aborted · duration 1000 ms · hits 2"))
    }

    @Test
    fun `free practice cannot close through the scored finish API`() {
        val telemetry = PracticeTelemetry(header().copy(runMode = "Practice"))
        assertThrows(IllegalStateException::class.java) {
            telemetry.finish(1_000f, PracticeResult.Empty, 0, null, aborted = false)
        }
        assertEquals(2, telemetry.jsonLines().size)
        assertFalse(telemetry.summary().contains("result"))
    }

    @Test
    fun `default Challenge keeps finite sample statistics and its original result event`() {
        val telemetry = PracticeTelemetry(header(), maxEvents = 2)
        repeat(5) { index ->
            telemetry.hit(
                atMs = index * 20f,
                outcome = judged(offsetMs = 4f + index, rawOffsetMs = 8f + index),
                envelope = 0.04f + index * 0.01f,
                threshold = 0.02f,
                peak = 0.2f,
            )
            telemetry.quiet(index * 20f + 1f, 0.01f + index * 0.001f, 0.005f, 0.02f)
        }
        telemetry.finish(3_000f, PracticeResult.Empty, 4, audio(), aborted = false)

        assertEquals("Challenge", telemetry.header.runMode)
        assertTrue(telemetry.jsonLines()[1].contains("\"mode\":\"Challenge\""))
        val summary = telemetry.summary()
        assertTrue(summary.contains("mode Challenge"))
        assertFalse(summary.contains("template notes"))
        assertFalse(summary.contains("prefix samples"))
        assertTrue(summary.contains("drift median +6 ms · spread 1 ms · 5 strokes"))
        assertTrue(summary.contains("raw median +10 ms · spread 1 ms · profile +10 (per 8)"))
        assertTrue(summary.contains("hits 0.0400..0.0800 · refused 5 0.0100..0.0140"))
        assertTrue(summary.contains("result 0.0 % · 0★"))
        assertTrue(summary.contains("debounced 4"))
        assertEquals(13, summary.lines().size)
        assertEquals(
            "{\"type\":\"result\",\"atMs\":3000.0,\"accuracy\":0.000,\"stars\":0," +
                "\"crown\":false,\"notes\":0,\"perfect\":0,\"good\":0,\"ok\":0,\"miss\":0," +
                "\"extra\":0,\"debounced\":4,\"maxCombo\":0,\"meanOffsetMs\":0.0," +
                "\"spreadMs\":0.0,\"latencyBiasMs\":0.0}",
            telemetry.jsonLines()[4],
        )
        assertEquals("{\"type\":\"truncated\",\"droppedEvents\":8}", telemetry.jsonLines().last())
        assertEquals("1-3 · Bronze · 80 bpm · 0.0 %", telemetry.title())
    }

    private fun judged(
        noteIndex: Int = 0,
        offsetMs: Float = 4f,
        rawOffsetMs: Float = 9f,
    ) = HitOutcome.Judged(
        noteIndex = noteIndex,
        judgement = NoteJudgement(offsetMs = offsetMs, window = HitWindow.Perfect),
        rawOffsetMs = rawOffsetMs,
    )

    private fun header() = TelemetryHeader(
        startedAt = "2026-08-24 15:10:04",
        device = "Google Pixel 7",
        androidVersion = "14 (sdk 34)",
        build = "Version 0.1.0 · Build 30",
        levelId = "single-3",
        levelLabel = "1-3",
        family = "Singles",
        rank = "Bronze",
        bpm = 80,
        noteCount = 32,
        minIntervalMs = 187.5f,
        perfectMs = HitWindows.Default.perfectMs,
        goodMs = HitWindows.Default.goodMs,
        okMs = HitWindows.Default.okMs,
        latencyMs = 40f,
        latencyCalibrated = false,
        sensitivity = 0.5f,
        micThresholdLevel = 0.02f,
        clickAudible = true,
        headphones = true,
        audio = audio(),
    )

    private fun audio() = TelemetryAudio(
        sampleRate = 48_000,
        outputBurstFrames = 96,
        outputBufferFrames = 192,
        inputBurstFrames = 96,
        inputBufferFrames = 192,
        inputCapacityFrames = 3_072,
        outputExclusive = true,
        inputExclusive = true,
        inputPreset = "unprocessed",
        outputXRuns = 0,
        inputXRuns = 2,
        errorCount = 0,
        lastErrorCode = 0,
    )
}
