package com.rudimentor.app.telemetry

import com.rudimentor.app.audio.StreamClockDrift
import com.rudimentor.app.ui.practice.HitOutcome
import com.rudimentor.app.ui.practice.PracticeResult
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Audio path facts of one attempt, as the log records them.
 *
 * A copy of the native `NativeMicLab.StreamInfo` numbers rather than the class itself:
 * the collector stays free of the audio layer, so the whole log format is unit-testable
 * on the JVM without loading the native library.
 */
data class TelemetryAudio(
    val sampleRate: Int,
    val outputBurstFrames: Int,
    val outputBufferFrames: Int,
    val inputBurstFrames: Int,
    val inputBufferFrames: Int,
    val inputCapacityFrames: Int,
    val outputExclusive: Boolean,
    val inputExclusive: Boolean,
    val inputPreset: String,
    val outputXRuns: Int,
    val inputXRuns: Int,
    val errorCount: Int,
    val lastErrorCode: Int,
)

/** Everything that holds for a whole attempt: the phone, the build and the settings. */
data class TelemetryHeader(
    val startedAt: String,
    val device: String,
    val androidVersion: String,
    val build: String,
    val levelId: String,
    val levelLabel: String,
    val family: String,
    val rank: String,
    val bpm: Int,
    val noteCount: Int,
    val minIntervalMs: Float,
    val perfectMs: Float,
    val goodMs: Float,
    val okMs: Float,
    val latencyMs: Float,
    /**
     * Whether [latencyMs] was measured on the calibration screen or is still the guessed
     * default. A late run only means something once this is known (decision 154).
     */
    val latencyCalibrated: Boolean,
    /**
     * Stream-start skew that held while [latencyMs] was measured, or null when unknown.
     * Together with the skew of this run it says how much of the round trip the engine's own
     * re-anchoring already removed, which is the difference decision 164 corrects for.
     */
    val calibrationSkewMs: Float? = null,
    val sensitivity: Float,
    /**
     * The loudness gate in force during the run. A stroke below it never reaches the score,
     * so the summary states it next to the levels of the strokes that passed and the ones it
     * refused (decision 167).
     */
    val micThresholdLevel: Float = Float.NaN,
    val clickAudible: Boolean,
    val headphones: Boolean,
    val audio: TelemetryAudio?,
)

/**
 * Collects one practice attempt into two texts: a line-per-event JSONL body for
 * re-scoring the run later, and a short human summary for the log list.
 *
 * Pure and in-memory on purpose. Nothing here touches the file system or the audio
 * layer, so it can be called from the poll loop without a disk write on the caller's
 * thread, and [PracticeLogStore] is the only part that needs a device.
 *
 * The events keep enough of the raw path -- every detector trigger, including the ones
 * the debounce filter dropped, with its envelope and threshold -- that a finished
 * attempt can be re-judged against different windows or a different filter from the
 * log alone.
 */
class PracticeTelemetry(
    val header: TelemetryHeader,
    private val maxEvents: Int = MAX_EVENTS,
) {

    private val events = ArrayList<String>(256)

    /** Events refused after [maxEvents], so a long run cannot grow without bound. */
    private var dropped = 0

    private var judged = 0
    private var extras = 0
    private var debounced = 0
    private var afterEnd = 0
    private var quiet = 0
    private var missed = 0

    // The output latency the engine measured during the run. Over Bluetooth it is
    // hundreds of milliseconds and it moves, and that movement is what makes a run drift
    // late halfway through, so the extremes are kept for the summary (decision 154).
    private var latencySamples = 0
    private var minOutputLatencyMs = Float.NaN
    private var maxOutputLatencyMs = Float.NaN
    private var lastOutputLatencyMs = Float.NaN
    private var lastAppliedLatencyMs = Float.NaN
    // The two halves of the path this run used: the stroke's and the picture's
    // (decision 188). Read back to see which half a bad run got wrong.
    private var lastMicLatencyMs = Float.NaN
    private var lastVisualShiftMs = Float.NaN
    private var lastStreamSkewMs = Float.NaN

    /**
     * Distance from the nearest note of every stroke the attempt saw, judged or extra.
     *
     * The scored offsets cover judged strokes only, so an attempt that was late everywhere
     * scored no notes at all and reported "timing mean 0 ms · 0 offsets" -- the one number
     * that would have named the problem was missing from the dev.39 log (decision 164).
     */
    private val driftOffsets = ArrayList<Float>(64)

    /**
     * Distance from the note of every judged stroke *before* the run's own latency bias was
     * taken out, in playing order.
     *
     * This is the one series that separates the phone from the player: the graded offsets are
     * corrected and therefore always look centred, while these say what the audio path really
     * delivered and how it moved during the run. The summary prints their median per group of
     * [PROFILE_GROUP] strokes, which is how a run like dev.41 -- +115 ms at the start, +30 ms at
     * the end, spread only 25 ms -- names itself in one line instead of ten iterations
     * (decision 167).
     */
    private val rawOffsets = ArrayList<Float>(64)

    /** Envelope of every accepted stroke and of every one the gate refused. */
    private val hitEnvelopes = ArrayList<Float>(64)
    private val quietEnvelopes = ArrayList<Float>(32)

    /** The latency bias the attempt measured on itself, and how it got there. */
    private var biasMs = Float.NaN
    private var biasCapturedMs = Float.NaN
    private var biasSamples = 0

    private var result: PracticeResult? = null
    private var finalAudio: TelemetryAudio? = null
    private var aborted = false
    private var abortedAtMs = 0f

    /** A stick hit as the attempt judged it, with the detector state behind it. */
    fun hit(
        atMs: Float,
        outcome: HitOutcome,
        envelope: Float,
        threshold: Float,
        peak: Float,
        /**
         * For an extra stroke: how far it fell from the nearest note of the attempt.
         * Without it an extra is a bare timestamp in the log, and a run that is simply
         * late everywhere cannot be told from one that struck in the wrong places
         * (decision 154). NaN when there was no note to compare against.
         */
        extraOffsetMs: Float = Float.NaN,
    ) {
        hitEnvelopes.add(envelope)
        val json = TelemetryJson("hit")
            .num("atMs", atMs)
            .num("env", envelope, ENVELOPE_DIGITS)
            .num("thr", threshold, ENVELOPE_DIGITS)
            .num("peak", peak, ENVELOPE_DIGITS)
        when (outcome) {
            is HitOutcome.Judged -> {
                judged += 1
                driftOffsets.add(outcome.judgement.offsetMs)
                if (!outcome.rawOffsetMs.isNaN()) rawOffsets.add(outcome.rawOffsetMs)
                json.text("outcome", "judged")
                    .int("note", outcome.noteIndex)
                    .text("window", outcome.judgement.window.name)
                    .num("offsetMs", outcome.judgement.offsetMs)
                    // What the audio path delivered, before the self-tuning took the run's
                    // systematic lateness out of it (decision 167).
                    .num("rawOffsetMs", outcome.rawOffsetMs)
            }

            is HitOutcome.Extra -> {
                extras += 1
                if (!extraOffsetMs.isNaN()) driftOffsets.add(extraOffsetMs)
                json.text("outcome", "extra").num("offsetMs", extraOffsetMs)
            }

            is HitOutcome.Debounced -> {
                debounced += 1
                json.text("outcome", "debounced").num("gapMs", outcome.gapMs)
            }

            is HitOutcome.AfterEnd -> {
                afterEnd += 1
                json.text("outcome", "afterEnd").num("atMs", outcome.positionMs)
            }
        }
        add(json.done())
    }

    /**
     * The measured output latency at this point of the run, and the compensation the
     * detector is actually using because of it.
     *
     * Written only when it moved: the poll loop runs every few milliseconds and the log
     * is meant to be read by a human.
     */
    fun latency(
        atMs: Float,
        outputLatencyMs: Float,
        appliedMs: Float,
        streamSkewMs: Float = Float.NaN,
        /** Measured input half of the path: what the stroke is late by (decision 188). */
        micLatencyMs: Float = Float.NaN,
        /** How far ahead of the render clock the picture is drawn (decision 188). */
        visualShiftMs: Float = Float.NaN,
    ) {
        latencySamples += 1
        lastMicLatencyMs = micLatencyMs
        lastVisualShiftMs = visualShiftMs
        lastOutputLatencyMs = outputLatencyMs
        lastAppliedLatencyMs = appliedMs
        lastStreamSkewMs = streamSkewMs
        if (minOutputLatencyMs.isNaN() || outputLatencyMs < minOutputLatencyMs) {
            minOutputLatencyMs = outputLatencyMs
        }
        if (maxOutputLatencyMs.isNaN() || outputLatencyMs > maxOutputLatencyMs) {
            maxOutputLatencyMs = outputLatencyMs
        }
        add(
            TelemetryJson("latency")
                .num("atMs", atMs)
                .num("outputMs", outputLatencyMs)
                .num("appliedMs", appliedMs)
                .num("skewMs", streamSkewMs)
                .num("micMs", micLatencyMs)
                .num("visualShiftMs", visualShiftMs)
                .done(),
        )
    }

    /**
     * The self-tuning of this run: how late the audio path reports the strokes, measured by the
     * attempt itself rather than by the calibration screen.
     *
     * Written when the capture phase closes and whenever the tracked value moves a step, so the
     * log shows both the number and its drift (decision 167).
     */
    fun latencyBias(atMs: Float, biasMs: Float, samples: Int, phase: String) {
        this.biasMs = biasMs
        this.biasSamples = samples
        if (phase == PHASE_CAPTURE) biasCapturedMs = biasMs
        add(
            TelemetryJson("bias")
                .num("atMs", atMs)
                .num("biasMs", biasMs)
                .int("strokes", samples)
                .text("phase", phase)
                .done(),
        )
    }

    /**
     * An onset the loudness gate refused, with the numbers that decided it.
     *
     * The dev.37 log had no such line, so room noise at envelope 0.012 was
     * indistinguishable from playing and was scored as it (decision 158).
     */
    fun quiet(atMs: Float, envelope: Float, threshold: Float, gate: Float) {
        quiet += 1
        quietEnvelopes.add(envelope)
        add(
            TelemetryJson("quiet")
                .num("atMs", atMs)
                .num("env", envelope, LEVEL_DIGITS)
                .num("thr", threshold, LEVEL_DIGITS)
                .num("gate", gate, LEVEL_DIGITS)
                .done(),
        )
    }

    /** A note whose window passed without a stroke. */
    fun miss(atMs: Float, noteIndex: Int) {
        missed += 1
        add(
            TelemetryJson("miss")
                .num("atMs", atMs)
                .int("note", noteIndex)
                .done(),
        )
    }

    /**
     * A fault or a change in the audio path: a dropped buffer, a stream error, or the
     * headphones going in or out mid-attempt.
     */
    fun audioEvent(atMs: Float, kind: String, detail: String) {
        add(
            TelemetryJson("audio")
                .num("atMs", atMs)
                .text("kind", kind)
                .text("detail", detail)
                .done(),
        )
    }

    /** Closes the attempt with its result. The log is only written after this. */
    fun finish(
        atMs: Float,
        result: PracticeResult,
        debouncedTotal: Int,
        audio: TelemetryAudio?,
        aborted: Boolean,
    ) {
        this.result = result
        this.finalAudio = audio
        this.aborted = aborted
        this.abortedAtMs = atMs
        // The attempt is the source of truth for the dropped strokes: an event that fell
        // off the tail of the ring would otherwise quietly lower the count.
        this.debounced = debouncedTotal
        add(
            TelemetryJson(if (aborted) "abort" else "result")
                .num("atMs", atMs)
                .num("accuracy", result.accuracy, ACCURACY_DIGITS)
                .int("stars", result.stars)
                .bool("crown", result.crown)
                .int("notes", result.noteCount)
                .int("perfect", result.perfect)
                .int("good", result.good)
                .int("ok", result.ok)
                .int("miss", result.misses)
                .int("extra", result.extras)
                .int("debounced", debouncedTotal)
                .int("maxCombo", result.maxCombo)
                .num("meanOffsetMs", result.meanOffsetMs)
                .num("spreadMs", spreadMs(result.offsets))
                .num("latencyBiasMs", result.latencyBiasMs)
                .done(),
        )
    }

    /**
     * The whole attempt as JSONL: the session line, the attempt line, every event in
     * order, and the result. Built on demand, off the poll loop.
     */
    fun jsonLines(): List<String> {
        val lines = ArrayList<String>(events.size + 3)
        lines.add(sessionLine())
        lines.add(attemptLine())
        lines.addAll(events)
        if (dropped > 0) {
            lines.add(TelemetryJson("truncated").int("droppedEvents", dropped).done())
        }
        return lines
    }

    /**
     * A handful of lines a human can read on the phone: what ran it, what was played, how
     * the audio path behaved, how it scored, and how the output latency moved underneath
     * it. The log list shows exactly this text, so nothing on the screen has to parse the
     * JSONL body.
     */
    /**
     * One second of the clock diagnostic: how fast each stream's frame counter really runs
     * and how far the note grid has moved away from the stroke grid since the start.
     *
     * Diagnostics only, and the reason it is here: a run drifting 0.68 ms/s and a round trip
     * overstated by ~93 ms both look like two clocks running at different rates, and nothing
     * should try to compensate before the log says whether they do (decision 188).
     */
    fun clock(atMs: Float, reading: StreamClockDrift.Reading) {
        clockSamples += 1
        lastClockPpm = reading.driftPpm
        lastDivergenceMs = reading.divergenceMs
        if (maxAbsDivergenceMs.isNaN() ||
            kotlin.math.abs(reading.divergenceMs) > kotlin.math.abs(maxAbsDivergenceMs)
        ) {
            maxAbsDivergenceMs = reading.divergenceMs
        }
        add(
            TelemetryJson("clock")
                .num("atMs", atMs)
                .num("sec", reading.elapsedSec)
                .num("inFps", reading.inputRateHz, 1)
                .num("outFps", reading.outputRateHz, 1)
                .num("ppm", reading.driftPpm, 0)
                .num("inLagMs", reading.inputLagMs)
                .num("outLagMs", reading.outputLagMs)
                .num("apartMs", reading.divergenceMs)
                .int("inCb", reading.inputCallbacks.toInt())
                .int("outCb", reading.outputCallbacks.toInt())
                .done(),
        )
    }
    // Clock diagnostic of this log (decision 188): the last rate difference, the last and
    // largest divergence between the note grid and the stroke grid, and how many seconds
    // were sampled. Reported in the summary so a log can be read without the JSON.
    private var clockSamples: Int = 0
    private var lastClockPpm: Float = Float.NaN
    private var lastDivergenceMs: Float = Float.NaN
    private var maxAbsDivergenceMs: Float = Float.NaN

    private fun clockLine(): String =
        if (clockSamples == 0) {
            "clocks not sampled"
        } else {
            "clocks: rate difference ${signedPlain(lastClockPpm)} ppm · " +
                "grids apart ${signed(lastDivergenceMs)} " +
                "(worst ${signed(maxAbsDivergenceMs)}) · $clockSamples s sampled"
        }

    fun summary(): String {
        val audio = finalAudio ?: header.audio
        val lines = ArrayList<String>(7)
        lines.add(
            "${header.build} · ${header.device} · Android ${header.androidVersion} · " +
                header.startedAt,
        )
        lines.add(
            "${header.levelLabel} · ${header.family} · rank ${header.rank} · " +
                "${header.bpm} bpm · ${header.noteCount} notes · " +
                "interval ${ms(header.minIntervalMs)}",
        )
        lines.add(
            "windows perfect ±${ms(header.perfectMs)} / good ±${ms(header.goodMs)} / " +
                "ok ±${ms(header.okMs)} · " +
                "latency ${ms(header.latencyMs)} " +
                "(${if (header.latencyCalibrated) "measured" else "guessed"}" +
                (header.calibrationSkewMs?.let { " at skew ${ms(it)}" } ?: "") +
                ") · " +
                "click ${onOff(header.clickAudible)} · " +
                "headphones ${yesNo(header.headphones)} · " +
                "sensitivity ${decimal(header.sensitivity, ACCURACY_DIGITS)}",
        )
        lines.add(
            if (audio == null) {
                "audio not reported"
            } else {
                "audio ${audio.sampleRate} Hz · " +
                    "burst ${audio.outputBurstFrames}/${audio.inputBurstFrames} · " +
                    "buffer ${audio.outputBufferFrames}/${audio.inputBufferFrames} " +
                    "of ${audio.inputCapacityFrames} · " +
                    "exclusive ${yesNo(audio.outputExclusive)}/" +
                    "${yesNo(audio.inputExclusive)} · " +
                    "preset ${audio.inputPreset} · " +
                    "xruns ${audio.outputXRuns}/${audio.inputXRuns} · " +
                    "errors ${audio.errorCount}" +
                    if (audio.errorCount > 0) " (last ${audio.lastErrorCode})" else ""
            },
        )
        val outcome = result
        if (outcome == null) {
            lines.add(
                "result none · judged $judged · extra $extras · debounced $debounced · " +
                    "afterEnd $afterEnd · quiet $quiet",
            )
        } else {
            lines.add(
                "result ${percent(outcome.accuracy)} · ${outcome.stars}★" +
                    (if (outcome.crown) " + crown" else "") +
                    " · perfect ${outcome.perfect} good ${outcome.good} ok ${outcome.ok} " +
                    "miss ${outcome.misses} · extra ${outcome.extras} · " +
                    "debounced $debounced · afterEnd $afterEnd · quiet $quiet · " +
                    "combo ${outcome.maxCombo}",
            )
            lines.add(
                "timing mean ${signed(outcome.meanOffsetMs)} · " +
                    "spread ${ms(spreadMs(outcome.offsets))} · " +
                    "${outcome.offsets.size} offsets",
            )
        }
        lines.add(
            if (latencySamples == 0) {
                "output latency not reported"
            } else {
                "output latency ${ms(lastOutputLatencyMs)} " +
                    "(${minOutputLatencyMs.roundToInt()}..${ms(maxOutputLatencyMs)}, " +
                    "drift ${ms(maxOutputLatencyMs - minOutputLatencyMs)}) · " +
                    "applied ${ms(lastAppliedLatencyMs)} · " +
                    "skew ${ms(lastStreamSkewMs)} · $latencySamples changes"
            },
        )
        // The split the run actually used. The two numbers are what the model is made of:
        // the stroke is moved by the microphone half, the picture and the click by the
        // output half, and a run where the two disagree scores near zero however well it
        // was played (decision 188).
        if (!lastMicLatencyMs.isNaN() || !lastVisualShiftMs.isNaN()) {
            lines.add(
                "path split: microphone ${ms(lastMicLatencyMs)} · " +
                    "picture ahead ${ms(lastVisualShiftMs)} · " +
                    "sum ${ms(lastMicLatencyMs + lastVisualShiftMs)} " +
                    "vs compensation ${ms(lastAppliedLatencyMs)}",
            )
        }
        // Every stroke against its nearest note, extras included. A run that is late
        // everywhere says so here even when it scored nothing at all (decision 164).
        val drift = medianOf(driftOffsets)
        lines.add(
            if (drift == null) {
                "drift no strokes"
            } else {
                "drift median ${signed(drift)} · " +
                    "spread ${ms(spreadMs(driftOffsets))} · ${driftOffsets.size} strokes"
            },
        )
        // What the audio path itself delivered, and what the run did about it. These two lines
        // are the whole diagnosis of a timing complaint: the first says how wrong the round trip
        // was and whether it moved, the second says what the attempt corrected it by
        // (decision 167).
        lines.add(
            if (rawOffsets.isEmpty()) {
                "raw no strokes"
            } else {
                "raw median ${signed(medianOf(rawOffsets) ?: 0f)} · " +
                    "spread ${ms(spreadMs(rawOffsets))} · " +
                    "profile ${profileOf(rawOffsets)} (per $PROFILE_GROUP)"
            },
        )
        lines.add(
            if (biasMs.isNaN()) {
                "self-tune not engaged"
            } else {
                "self-tune bias ${signed(biasMs)} · " +
                    "captured ${signed(biasCapturedMs)} · $biasSamples strokes · " +
                    "latency ${ms(header.latencyMs)} → ${ms(header.latencyMs + biasMs)}"
            },
        )
        // The gate is the other way a steady run loses notes: a stroke it refused never
        // reaches the score at all, so its levels belong next to the ones that passed.
        lines.add(
            "gate ${decimal(header.micThresholdLevel, ACCURACY_DIGITS)} · " +
                "hits ${envelopeRange(hitEnvelopes)} · " +
                "refused $quiet ${envelopeRange(quietEnvelopes)}",
        )
        if (aborted) lines.add("ABORTED at ${ms(abortedAtMs)}")
        lines.add(clockLine())
        return lines.joinToString(separator = "\n")
    }

    /** First line of the summary, for the log list. */
    fun title(): String {
        val accuracy = result?.let { percent(it.accuracy) } ?: "no result"
        val suffix = if (aborted) " · aborted" else ""
        return "${header.levelLabel} · ${header.rank} · ${header.bpm} bpm · $accuracy$suffix"
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

    private fun attemptLine(): String = TelemetryJson("attempt")
        .text("level", header.levelId)
        .text("levelLabel", header.levelLabel)
        .text("family", header.family)
        .text("rank", header.rank)
        .int("bpm", header.bpm)
        .int("notes", header.noteCount)
        .num("intervalMs", header.minIntervalMs)
        .num("perfectMs", header.perfectMs)
        .num("goodMs", header.goodMs)
        .num("okMs", header.okMs)
        .num("latencyMs", header.latencyMs)
        .bool("latencyCalibrated", header.latencyCalibrated)
        .num("calibrationSkewMs", header.calibrationSkewMs ?: Float.NaN)
        .num("sensitivity", header.sensitivity, ACCURACY_DIGITS)
        .num("micGate", header.micThresholdLevel, LEVEL_DIGITS)
        .bool("clickAudible", header.clickAudible)
        .done()

    companion object {
        /** Events one attempt may hold. Above this the tail is counted, not kept. */
        const val MAX_EVENTS = 4_000

        private const val OFFSET_DIGITS = 1
        private const val ENVELOPE_DIGITS = 4
        private const val ACCURACY_DIGITS = 3

        /**
         * Strokes per group of the raw-offset profile. Eight is two bars of quarters: enough
         * to average a player's own scatter out, short enough to show a drift moving
         * (decision 167).
         */
        const val PROFILE_GROUP = 8

        /** Phase names of the self-tuning, as they appear in the log. */
        const val PHASE_CAPTURE = "capture"
        const val PHASE_TRACK = "track"

        /**
         * Median of every group of [PROFILE_GROUP] offsets, in playing order: "+115 / +88 /
         * +60 / +34" is a drifting audio path, "+58 / +55 / +60 / +57" is a constant one that
         * the calibration simply got wrong. One line, and the two are no longer confusable.
         */
        fun profileOf(offsets: List<Float>): String =
            offsets.chunked(PROFILE_GROUP)
                .map { group -> signed(medianOf(group) ?: 0f).removeSuffix(" ms") }
                .joinToString(separator = " / ")

        /** Loudest and quietest of a set of envelopes, for the gate line. */
        fun envelopeRange(levels: List<Float>): String =
            if (levels.isEmpty()) {
                "none"
            } else {
                "${decimal(levels.min(), LEVEL_DIGITS)}..${decimal(levels.max(), LEVEL_DIGITS)}"
            }

        /** Standard deviation of the offsets: how wide the timing scattered. */
        fun spreadMs(offsets: List<Float>): Float {
            if (offsets.size < 2) return 0f
            val mean = offsets.average()
            var sum = 0.0
            for (offset in offsets) {
                val delta = offset - mean
                sum += delta * delta
            }
            return sqrt(sum / offsets.size).toFloat()
        }

        /**
         * Middle of the offsets, or null when there are none. A median and not a mean: a
         * couple of wild strokes must not move the number a correction is read off.
         */
        fun medianOf(offsets: List<Float>): Float? {
            if (offsets.isEmpty()) return null
            val sorted = offsets.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[middle]
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2f
            }
        }

        /** Envelope levels are small: three decimals is what tells 0.012 from 0.02. */
        private const val LEVEL_DIGITS = 4

        private fun decimal(value: Float, digits: Int): String =
            String.format(Locale.US, "%.${digits}f", value)

        private fun ms(value: Float): String =
            if (value.isNaN()) "n/a" else "${value.roundToInt()} ms"

        /** Signed number without a unit, for the parts per million of the clock line. */
        private fun signedPlain(value: Float): String {
            if (value.isNaN()) return "n/a"
            val rounded = value.roundToInt()
            return if (rounded > 0) "+$rounded" else "$rounded"
        }

        private fun signed(value: Float): String {
            if (value.isNaN()) return "n/a"
            val rounded = value.roundToInt()
            return if (rounded > 0) "+$rounded ms" else "$rounded ms"
        }

        private fun percent(value: Float): String =
            "${decimal(value * 100f, OFFSET_DIGITS)} %"

        private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

        private fun onOff(value: Boolean): String = if (value) "on" else "off"
    }
}
