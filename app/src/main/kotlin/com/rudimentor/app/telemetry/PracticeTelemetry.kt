package com.rudimentor.app.telemetry

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
    val sensitivity: Float,
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
    private var missed = 0

    // The output latency the engine measured during the run. Over Bluetooth it is
    // hundreds of milliseconds and it moves, and that movement is what makes a run drift
    // late halfway through, so the extremes are kept for the summary (decision 154).
    private var latencySamples = 0
    private var minOutputLatencyMs = Float.NaN
    private var maxOutputLatencyMs = Float.NaN
    private var lastOutputLatencyMs = Float.NaN
    private var lastAppliedLatencyMs = Float.NaN

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
        val json = Json("hit")
            .num("atMs", atMs)
            .num("env", envelope, ENVELOPE_DIGITS)
            .num("thr", threshold, ENVELOPE_DIGITS)
            .num("peak", peak, ENVELOPE_DIGITS)
        when (outcome) {
            is HitOutcome.Judged -> {
                judged += 1
                json.text("outcome", "judged")
                    .int("note", outcome.noteIndex)
                    .text("window", outcome.judgement.window.name)
                    .num("offsetMs", outcome.judgement.offsetMs)
            }

            is HitOutcome.Extra -> {
                extras += 1
                json.text("outcome", "extra").num("offsetMs", extraOffsetMs)
            }

            is HitOutcome.Debounced -> {
                debounced += 1
                json.text("outcome", "debounced").num("gapMs", outcome.gapMs)
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
    fun latency(atMs: Float, outputLatencyMs: Float, appliedMs: Float) {
        latencySamples += 1
        lastOutputLatencyMs = outputLatencyMs
        lastAppliedLatencyMs = appliedMs
        if (minOutputLatencyMs.isNaN() || outputLatencyMs < minOutputLatencyMs) {
            minOutputLatencyMs = outputLatencyMs
        }
        if (maxOutputLatencyMs.isNaN() || outputLatencyMs > maxOutputLatencyMs) {
            maxOutputLatencyMs = outputLatencyMs
        }
        add(
            Json("latency")
                .num("atMs", atMs)
                .num("outputMs", outputLatencyMs)
                .num("appliedMs", appliedMs)
                .done(),
        )
    }

    /** A note whose window passed without a stroke. */
    fun miss(atMs: Float, noteIndex: Int) {
        missed += 1
        add(
            Json("miss")
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
            Json("audio")
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
            Json(if (aborted) "abort" else "result")
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
            lines.add(Json("truncated").int("droppedEvents", dropped).done())
        }
        return lines
    }

    /**
     * A handful of lines a human can read on the phone: what ran it, what was played, how
     * the audio path behaved, how it scored, and how the output latency moved underneath
     * it. The log list shows exactly this text, so nothing on the screen has to parse the
     * JSONL body.
     */
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
                "(${if (header.latencyCalibrated) "measured" else "guessed"}) · " +
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
            lines.add("result none · judged $judged · extra $extras · debounced $debounced")
        } else {
            lines.add(
                "result ${percent(outcome.accuracy)} · ${outcome.stars}★" +
                    (if (outcome.crown) " + crown" else "") +
                    " · perfect ${outcome.perfect} good ${outcome.good} ok ${outcome.ok} " +
                    "miss ${outcome.misses} · extra ${outcome.extras} · " +
                    "debounced $debounced · combo ${outcome.maxCombo}",
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
                    "applied ${ms(lastAppliedLatencyMs)} · $latencySamples changes"
            },
        )
        if (aborted) lines.add("ABORTED at ${ms(abortedAtMs)}")
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
        val json = Json("session")
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

    private fun attemptLine(): String = Json("attempt")
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
        .num("sensitivity", header.sensitivity, ACCURACY_DIGITS)
        .bool("clickAudible", header.clickAudible)
        .done()

    /** Minimal JSON object writer: the log format is fixed, so a builder is enough. */
    private class Json(type: String) {
        private val out = StringBuilder(160)
        private var first = true

        init {
            out.append('{')
            text("type", type)
        }

        fun text(key: String, value: String): Json {
            key(key)
            out.append('"')
            for (char in value) {
                when (char) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else -> if (char < ' ') out.append(' ') else out.append(char)
                }
            }
            out.append('"')
            return this
        }

        fun int(key: String, value: Int): Json {
            key(key)
            out.append(value)
            return this
        }

        fun bool(key: String, value: Boolean): Json {
            key(key)
            out.append(value)
            return this
        }

        fun num(key: String, value: Float, digits: Int = OFFSET_DIGITS): Json {
            key(key)
            // A non-finite number is not JSON: a missed note carries NaN as its offset.
            if (value.isNaN() || value.isInfinite()) out.append("null") else {
                out.append(decimal(value, digits))
            }
            return this
        }

        fun done(): String = out.append('}').toString()

        private fun key(name: String) {
            if (!first) out.append(',')
            first = false
            out.append('"').append(name).append("\":")
        }
    }

    companion object {
        /** Events one attempt may hold. Above this the tail is counted, not kept. */
        const val MAX_EVENTS = 4_000

        private const val OFFSET_DIGITS = 1
        private const val ENVELOPE_DIGITS = 4
        private const val ACCURACY_DIGITS = 3

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

        private fun decimal(value: Float, digits: Int): String =
            String.format(Locale.US, "%.${digits}f", value)

        private fun ms(value: Float): String =
            if (value.isNaN()) "n/a" else "${value.roundToInt()} ms"

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
