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
        // The clock diagnostic adds its own line, and says so when nothing was sampled.
        assertEquals(13, lines.size)
        assertTrue(summary.contains("clocks not sampled"))
        // A run nobody watched the frames of says so, rather than reading as smooth
        // (decision 206).
        assertTrue(summary.contains("picture not reported"))
        assertTrue(lines.none { it.isBlank() })
        assertTrue(summary.contains("48000 Hz"))
        assertTrue(summary.contains("xruns 0/2"))
        assertTrue(summary.contains("debounced 4"))
        assertTrue(summary.contains("preset unprocessed"))
        // An uncalibrated run has to say so, and no latency was polled here.
        assertTrue(summary.contains("(guessed)"))
        assertTrue(summary.contains("output latency not reported"))
        // No stroke was fed in, so the drift line has to say that instead of a zero.
        assertTrue(summary.contains("drift no strokes"))
        // The self-tuning of the run states itself even when it never engaged (decision 167).
        assertTrue(summary.contains("raw no strokes"))
        assertTrue(summary.contains("self-tune not engaged"))
        assertTrue(summary.contains("refused 0 none"))
    }

    @Test
    fun `the summary counts the frames of the run and the stalls beside them`() {
        val telemetry = PracticeTelemetry(header = header())
        telemetry.audioEvent(500f, "stall", "main thread blocked 180 ms")
        telemetry.audioEvent(1_200f, "frame", "frame hitch #1: picture froze 420 ms")
        telemetry.audioEvent(3_000f, "frames", "frames 300 · hitches 1 (over 64 ms) · worst 420 ms")
        telemetry.finish(
            atMs = 3_000f,
            result = result(),
            debouncedTotal = 0,
            audio = audio(),
            aborted = false,
        )
        val summary = telemetry.summary()
        // The freeze the player sees and the block the loop feels are two different
        // things, so the line carries both (decision 206).
        assertTrue(summary.contains("picture frames 300 · hitches 1"))
        assertTrue(summary.contains("main-thread stalls 1"))
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
    fun `a run that scored nothing still reports how late every stroke was`() {
        val telemetry = PracticeTelemetry(header = header())
        listOf(140f, 150f, 160f).forEach { offset ->
            telemetry.hit(
                atMs = 1_000f + offset,
                outcome = HitOutcome.Extra(positionMs = 1_000f + offset),
                envelope = 0.05f,
                threshold = 0.02f,
                peak = 0.2f,
                extraOffsetMs = offset,
            )
        }
        telemetry.finish(
            atMs = 3_000f,
            result = PracticeResult.Empty,
            debouncedTotal = 0,
            audio = audio(),
            aborted = false,
        )
        val summary = telemetry.summary()
        assertTrue(summary.contains("drift median +150 ms"))
        assertTrue(summary.contains("3 strokes"))
    }

    @Test
    fun `the skew a measurement was taken under is written next to it`() {
        val telemetry = PracticeTelemetry(
            header = header().copy(latencyCalibrated = true, calibrationSkewMs = 145f),
        )
        telemetry.latency(atMs = 0f, outputLatencyMs = 24f, appliedMs = 268f, streamSkewMs = 0f)
        telemetry.finish(
            atMs = 3_000f,
            result = result(),
            debouncedTotal = 0,
            audio = audio(),
            aborted = false,
        )
        val summary = telemetry.summary()
        assertTrue(summary.contains("measured at skew 145 ms"))
        assertTrue(summary.contains("skew 0 ms"))
    }

    @Test
    fun `the middle of the offsets ignores a single wild stroke`() {
        assertEquals(null, PracticeTelemetry.medianOf(emptyList()))
        assertEquals(5f, PracticeTelemetry.medianOf(listOf(5f))!!, 0.001f)
        assertEquals(6f, PracticeTelemetry.medianOf(listOf(4f, 8f))!!, 0.001f)
        assertEquals(5f, PracticeTelemetry.medianOf(listOf(4f, 5f, 900f))!!, 0.001f)
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
