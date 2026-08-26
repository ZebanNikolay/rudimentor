package com.rudimentor.app.telemetry

import com.rudimentor.app.audio.LatencyCalibration
import com.rudimentor.app.audio.ThresholdProbe
import java.util.Locale
import kotlin.math.roundToInt

/** Everything that holds for a whole calibration round: the phone, the build, the output. */
data class CalibrationHeader(
    val startedAt: String,
    val device: String,
    val androidVersion: String,
    val build: String,
    val clickBpm: Int,
    val warmUpStrokes: Int,
    val targetStrokes: Int,
    val headphones: Boolean,
    val sensitivity: Float,
    /** The compensation stored before this round, so a re-calibration can be compared. */
    val previousLatencyMs: Float,
    val previousCalibrated: Boolean,
    /** The loudness gate stored before this visit, as an envelope level (decision 158). */
    val previousThresholdLevel: Float,
    val audio: TelemetryAudio?,
)

/**
 * Collects one calibration round into the same two texts an attempt produces: a JSONL body
 * with every stroke the microphone heard, and a short human summary.
 *
 * A round used to leave nothing behind but two `DevLog` lines, so a learner who came back
 * saying "I am not sure it worked" could not be answered from the log (decision 157). Now a
 * round is a log entry beside the attempts, and Share sends it the same way.
 *
 * Pure and in-memory, like [PracticeTelemetry]: nothing here touches disk or the audio
 * layer, so it is unit-testable on the JVM.
 */
class CalibrationTelemetry(
    val header: CalibrationHeader,
    private val maxEvents: Int = MAX_EVENTS,
) {

    private val events = ArrayList<String>(64)
    private var dropped = 0

    private var accepted = 0
    private var warmUp = 0
    private var rejected = 0
    private var ignored = 0

    private var rounds = 0
    private var stops = 0
    private var backgrounded = 0

    private var probeRuns = 0
    private var quietOnsets = 0
    private var appliedThresholdLevel = Float.NaN
    private var measuredNoiseLevel = Float.NaN
    private var measuredStrokeLevel = Float.NaN

    private var appliedMs = Float.NaN
    private var finalMedianMs = Float.NaN
    private var finalSpreadMs = Float.NaN
    private var finalSamples = 0
    private var completed = false
    private var audioFailed = false

    /**
     * One stroke the detector reported, and what the measurement did with it.
     *
     * [envelope] and [loud] are what the dev.37 investigation was missing: the log showed
     * room noise and real strokes as the same kind of event, and only their loudness told
     * them apart (decision 158).
     */
    fun stroke(
        atMs: Float,
        roundTripMs: Float,
        outcome: LatencyCalibration.Outcome,
        medianMs: Float?,
        spreadMs: Float,
        samples: Int,
        envelope: Float,
        threshold: Float,
        loud: Boolean,
    ) {
        if (!loud) quietOnsets += 1
        when (outcome) {
            LatencyCalibration.Outcome.Accepted -> accepted += 1
            LatencyCalibration.Outcome.WarmUp -> warmUp += 1
            LatencyCalibration.Outcome.Rejected -> rejected += 1
            LatencyCalibration.Outcome.Full -> ignored += 1
        }
        add(
            TelemetryJson("stroke")
                .num("atMs", atMs)
                .num("roundTripMs", roundTripMs)
                .text("outcome", outcome.name.lowercase(Locale.US))
                .int("samples", samples)
                .num("medianMs", medianMs ?: Float.NaN)
                .num("spreadMs", spreadMs)
                .num("env", envelope, LEVEL_DIGITS)
                .num("thr", threshold, LEVEL_DIGITS)
                .bool("loud", loud)
                .done(),
        )
    }

    /** The threshold probe started listening to the room. */
    fun probeStarted(atMs: Float) {
        probeRuns += 1
        add(TelemetryJson("probeStart").num("atMs", atMs).done())
    }

    /** The silent half is over: this is how loud the room turned out to be. */
    fun probeNoise(atMs: Float, noiseLevel: Float) {
        measuredNoiseLevel = noiseLevel
        add(
            TelemetryJson("probeNoise")
                .num("atMs", atMs)
                .num("noise", noiseLevel, LEVEL_DIGITS)
                .done(),
        )
    }

    /** One onset played into the second half of the probe. */
    fun probeStroke(atMs: Float, envelope: Float, counted: Boolean, count: Int) {
        add(
            TelemetryJson("probeStroke")
                .num("atMs", atMs)
                .num("env", envelope, LEVEL_DIGITS)
                .bool("counted", counted)
                .int("strokes", count)
                .done(),
        )
    }

    /** The probe has both halves and proposes a gate. */
    fun probeFinished(atMs: Float, result: ThresholdProbe.Result) {
        measuredNoiseLevel = result.noiseLevel
        measuredStrokeLevel = result.strokeLevel
        add(
            TelemetryJson("probeResult")
                .num("atMs", atMs)
                .num("noise", result.noiseLevel, LEVEL_DIGITS)
                .num("stroke", result.strokeLevel, LEVEL_DIGITS)
                .num("threshold", result.thresholdLevel, LEVEL_DIGITS)
                .bool("separated", result.separated)
                .done(),
        )
    }

    /** The gate the learner is taking with them, and where the number came from. */
    fun thresholdApplied(atMs: Float, level: Float, source: String) {
        appliedThresholdLevel = level
        add(
            TelemetryJson("threshold")
                .num("atMs", atMs)
                .num("level", level, LEVEL_DIGITS)
                .text("source", source)
                .done(),
        )
    }

    /** The click and the microphone started, or refused to. */
    fun roundStarted(atMs: Float, started: Boolean) {
        if (started) rounds += 1 else audioFailed = true
        add(
            TelemetryJson("start")
                .num("atMs", atMs)
                .bool("started", started)
                .done(),
        )
    }

    /**
     * The round stopped, and why: the learner pressed Stop, the target count was reached,
     * or the app went to the background and took the audio streams with it.
     */
    fun roundStopped(atMs: Float, reason: String) {
        stops += 1
        if (reason == REASON_BACKGROUNDED) backgrounded += 1
        if (reason == REASON_COMPLETE) completed = true
        add(
            TelemetryJson("stop")
                .num("atMs", atMs)
                .text("reason", reason)
                .done(),
        )
    }

    /** The learner threw the round away and started over. */
    fun reset(atMs: Float, samplesDropped: Int) {
        add(
            TelemetryJson("reset")
                .num("atMs", atMs)
                .int("samplesDropped", samplesDropped)
                .done(),
        )
    }

    /** Closes the round with the value handed back to the settings draft. */
    fun applied(atMs: Float, medianMs: Float, spreadMs: Float, samples: Int) {
        appliedMs = medianMs
        finalMedianMs = medianMs
        finalSpreadMs = spreadMs
        finalSamples = samples
        add(
            TelemetryJson("applied")
                .num("atMs", atMs)
                .num("medianMs", medianMs)
                .num("spreadMs", spreadMs)
                .int("samples", samples)
                .done(),
        )
    }

    /** Closes the round without a value: the learner walked out of the screen. */
    fun abandoned(atMs: Float, medianMs: Float?, spreadMs: Float, samples: Int) {
        finalMedianMs = medianMs ?: Float.NaN
        finalSpreadMs = spreadMs
        finalSamples = samples
        add(
            TelemetryJson("left")
                .num("atMs", atMs)
                .num("medianMs", medianMs ?: Float.NaN)
                .num("spreadMs", spreadMs)
                .int("samples", samples)
                .done(),
        )
    }

    /** The whole round as JSONL: the session line, the round line, then every event. */
    fun jsonLines(): List<String> {
        val lines = ArrayList<String>(events.size + 3)
        lines.add(sessionLine())
        lines.add(roundLine())
        lines.addAll(events)
        if (dropped > 0) {
            lines.add(TelemetryJson("truncated").int("droppedEvents", dropped).done())
        }
        return lines
    }

    /** The lines the log list shows: what ran it, what it heard, and what came out. */
    fun summary(): String {
        val lines = ArrayList<String>(5)
        lines.add(
            "${header.build} · ${header.device} · Android ${header.androidVersion} · " +
                header.startedAt,
        )
        lines.add(
            "calibration · ${header.clickBpm} bpm click · " +
                "target ${header.targetStrokes} strokes · " +
                "warm-up ${header.warmUpStrokes} · " +
                "headphones ${yesNo(header.headphones)} · " +
                "sensitivity ${TelemetryJson.decimal(header.sensitivity, 3)}",
        )
        lines.add(
            "before ${ms(header.previousLatencyMs)} " +
                "(${if (header.previousCalibrated) "set by hand" else "guessed"}) · " +
                "gate ${level(header.previousThresholdLevel)}",
        )
        lines.add(
            "gate probes $probeRuns · quiet onsets dropped $quietOnsets · " +
                "noise ${level(measuredNoiseLevel)} · stroke ${level(measuredStrokeLevel)} · " +
                "gate applied ${level(appliedThresholdLevel)}",
        )
        lines.add(
            "strokes accepted $accepted · warm-up $warmUp · rejected $rejected · " +
                "ignored $ignored · rounds $rounds · stops $stops" +
                if (backgrounded > 0) " (backgrounded $backgrounded)" else "",
        )
        val audio = header.audio
        lines.add(
            if (audio == null) {
                "audio not reported"
            } else {
                "audio ${audio.sampleRate} Hz · " +
                    "burst ${audio.outputBurstFrames}/${audio.inputBurstFrames} · " +
                    "buffer ${audio.outputBufferFrames}/${audio.inputBufferFrames} · " +
                    "preset ${audio.inputPreset} · " +
                    "xruns ${audio.outputXRuns}/${audio.inputXRuns} · " +
                    "errors ${audio.errorCount}"
            },
        )
        lines.add(
            "result " + if (appliedMs.isNaN()) {
                "not applied · median ${ms(finalMedianMs)} · spread ±${ms(finalSpreadMs)} · " +
                    "$finalSamples strokes"
            } else {
                "applied ${ms(appliedMs)} · spread ±${ms(finalSpreadMs)} · " +
                    "$finalSamples strokes · complete ${yesNo(completed)}"
            },
        )
        if (audioFailed) lines.add("AUDIO FAILED to start")
        return lines.joinToString(separator = "\n")
    }

    /** First line of the log list entry. */
    fun title(): String {
        val value = if (appliedMs.isNaN()) "not applied" else ms(appliedMs)
        return "calibration · $value · $accepted strokes"
    }

    private fun add(line: String) {
        if (events.size >= maxEvents) {
            dropped += 1
            return
        }
        events.add(line)
    }

    private fun sessionLine(): String {
        val json = TelemetryJson("session")
            .text("startedAt", header.startedAt)
            .text("device", header.device)
            .text("android", header.androidVersion)
            .text("build", header.build)
            .bool("headphones", header.headphones)
        val audio = header.audio
        if (audio != null) {
            json.int("sampleRate", audio.sampleRate)
                .int("outputBurst", audio.outputBurstFrames)
                .int("outputBuffer", audio.outputBufferFrames)
                .int("inputBurst", audio.inputBurstFrames)
                .int("inputBuffer", audio.inputBufferFrames)
                .int("inputCapacity", audio.inputCapacityFrames)
                .bool("outputExclusive", audio.outputExclusive)
                .bool("inputExclusive", audio.inputExclusive)
                .text("inputPreset", audio.inputPreset)
                .int("outputXRuns", audio.outputXRuns)
                .int("inputXRuns", audio.inputXRuns)
        }
        return json.done()
    }

    private fun roundLine(): String = TelemetryJson("calibration")
        .num("previousThresholdLevel", header.previousThresholdLevel, LEVEL_DIGITS)
        .int("clickBpm", header.clickBpm)
        .int("warmUp", header.warmUpStrokes)
        .int("target", header.targetStrokes)
        .num("sensitivity", header.sensitivity, 3)
        .num("previousLatencyMs", header.previousLatencyMs)
        .bool("previousCalibrated", header.previousCalibrated)
        .done()

    companion object {
        /** Events one round may hold. A round is short, so this is only a safety net. */
        const val MAX_EVENTS = 500

        const val REASON_STOPPED = "stopped"
        const val REASON_COMPLETE = "complete"
        const val REASON_BACKGROUNDED = "backgrounded"
        const val REASON_LEFT = "left"

        /** Envelope levels are small numbers: three decimals is what tells 0.012 from 0.02. */
        private const val LEVEL_DIGITS = 4

        private fun level(value: Float): String =
            if (value.isNaN()) "n/a" else TelemetryJson.decimal(value, LEVEL_DIGITS)

        private fun ms(value: Float): String =
            if (value.isNaN()) "n/a" else "${value.roundToInt()} ms"

        private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
    }
}
