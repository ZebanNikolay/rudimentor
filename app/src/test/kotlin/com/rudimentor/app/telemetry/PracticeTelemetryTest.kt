package com.rudimentor.app.telemetry

import com.rudimentor.app.ui.practice.HitOutcome
import com.rudimentor.app.ui.practice.HitWindow
import com.rudimentor.app.ui.practice.HitWindows
import com.rudimentor.app.ui.practice.NoteJudgement
import com.rudimentor.app.ui.practice.PracticeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The log format itself: the collector is pure, so the whole thing is checked here
 * without a device.
 */
class PracticeTelemetryTest {

    @Test
    fun `every event lands on its own line`() {
        val telemetry = PracticeTelemetry(header = header())
        telemetry.hit(
            atMs = 2_000f,
            outcome = HitOutcome.Judged(
                noteIndex = 0,
                judgement = NoteJudgement(offsetMs = -4.2f, window = HitWindow.Perfect),
            ),
            envelope = 0.0421f,
            threshold = 0.0180f,
            peak = 0.2f,
        )
        telemetry.hit(
            atMs = 2_010f,
            outcome = HitOutcome.Debounced(gapMs = 10f),
            envelope = 0.03f,
            threshold = 0.018f,
            peak = 0.2f,
        )
        telemetry.hit(
            atMs = 2_400f,
            outcome = HitOutcome.Extra(positionMs = 2_400f),
            envelope = 0.05f,
            threshold = 0.018f,
            peak = 0.2f,
        )
        telemetry.miss(atMs = 2_500f, noteIndex = 1)
        telemetry.audioEvent(atMs = 2_600f, kind = "headphones", detail = "disconnected")
        telemetry.finish(
            atMs = 3_000f,
            result = result(),
            debouncedTotal = 1,
            audio = audio(),
            aborted = false,
        )

        val lines = telemetry.jsonLines()
        // session, attempt, three hits, miss, audio, result.
        assertEquals(8, lines.size)
        assertTrue(lines.all { it.startsWith("{") && it.endsWith("}") })
        assertTrue(lines.none { it.contains("\n") })
        assertTrue(lines[0].contains("\"type\":\"session\""))
        assertTrue(lines[1].contains("\"type\":\"attempt\""))
        assertTrue(lines[2].contains("\"outcome\":\"judged\""))
        assertTrue(lines[2].contains("\"window\":\"Perfect\""))
        assertTrue(lines[3].contains("\"outcome\":\"debounced\""))
        assertTrue(lines[4].contains("\"outcome\":\"extra\""))
        assertTrue(lines[5].contains("\"type\":\"miss\""))
        assertTrue(lines[6].contains("\"type\":\"audio\""))
        assertTrue(lines[7].contains("\"type\":\"result\""))
        assertTrue(lines[7].contains("\"debounced\":1"))
    }

    @Test
    fun `a missed note never writes a NaN offset`() {
        val telemetry = PracticeTelemetry(header = header())
        telemetry.finish(
            atMs = 100f,
            result = PracticeResult.Empty,
            debouncedTotal = 0,
            audio = null,
            aborted = true,
        )
        val last = telemetry.jsonLines().last()
        assertTrue(last.contains("\"type\":\"abort\""))
        assertTrue(last.none { it == 'N' })
    }

    @Test
    fun `the tail is counted once the event cap is reached`() {
        val telemetry = PracticeTelemetry(header = header(), maxEvents = 2)
        repeat(5) { index -> telemetry.miss(atMs = index * 10f, noteIndex = index) }
        val lines = telemetry.jsonLines()
        assertEquals(5, lines.size)
        assertTrue(lines.last().contains("\"droppedEvents\":3"))
    }

    @Test
    fun `the summary is a short block of readable lines`() {
        val telemetry = PracticeTelemetry(header = header())
        telemetry.finish(
            atMs = 3_000f,
            result = result(),
            debouncedTotal = 4,
            audio = audio(),
            aborted = false,
        )
        val summary = telemetry.summary()
        val lines = summary.lines()
        assertEquals(7, lines.size)
        assertTrue(lines.none { it.isBlank() })
        assertTrue(summary.contains("48000 Hz"))
        assertTrue(summary.contains("xruns 0/2"))
        assertTrue(summary.contains("debounced 4"))
        assertTrue(summary.contains("preset unprocessed"))
        // An uncalibrated run has to say so, and no latency was polled here.
        assertTrue(summary.contains("(guessed)"))
        assertTrue(summary.contains("output latency not reported"))
    }

    @Test
    fun `the summary reports the drift of the polled output latency`() {
        val telemetry = PracticeTelemetry(header = header())
        telemetry.latency(atMs = 0f, outputLatencyMs = 24f, appliedMs = 24f)
        telemetry.latency(atMs = 900f, outputLatencyMs = 31f, appliedMs = 31f)
        telemetry.latency(atMs = 1_800f, outputLatencyMs = 27f, appliedMs = 27f)
        telemetry.finish(
            atMs = 3_000f,
            result = result(),
            debouncedTotal = 0,
            audio = audio(),
            aborted = false,
        )
        val summary = telemetry.summary()
        assertTrue(summary.contains("output latency 27 ms"))
        assertTrue(summary.contains("24..31 ms"))
        assertTrue(summary.contains("drift 7 ms"))
        assertTrue(summary.contains("3 changes"))
    }

    @Test
    fun `the spread of a clean run is zero and of a scattered one is not`() {
        assertEquals(0f, PracticeTelemetry.spreadMs(listOf(5f)), 0f)
        assertEquals(0f, PracticeTelemetry.spreadMs(listOf(5f, 5f, 5f)), 0f)
        assertEquals(10f, PracticeTelemetry.spreadMs(listOf(-10f, 10f)), 0.01f)
    }

    @Test
    fun `a quote in a device name cannot break the json`() {
        val telemetry = PracticeTelemetry(header = header(device = "Weird \"Phone\"\n2"))
        val session = telemetry.jsonLines().first()
        assertTrue(session.contains("Weird \\\"Phone\\\"\\n2"))
        assertTrue(session.count { it == '\n' } == 0)
    }

    private fun header(device: String = "Google Pixel 7") = TelemetryHeader(
        startedAt = "2026-08-24 15:10:04",
        device = device,
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

    private fun result() = PracticeResult(
        noteCount = 32,
        perfect = 20,
        good = 8,
        ok = 2,
        misses = 2,
        extras = 1,
        maxCombo = 14,
        accuracy = 0.874f,
        meanOffsetMs = 6.2f,
        offsets = listOf(-4f, 2f, 8f, 12f, 6f),
        windows = HitWindows.Default,
    )
}
