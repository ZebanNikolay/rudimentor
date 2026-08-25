package com.rudimentor.app.audio

/**
 * Kotlin facade for the native mic lab engine.
 *
 * The engine drives one full-duplex Oboe session: a mono click on the output
 * stream and a mono microphone capture on the input stream, both running at
 * the same sample rate so we can compare frame indices directly.
 *
 * All methods are safe to call from any thread; the native side uses lock-free
 * ring buffers for events and atomics for parameter updates.
 */
class NativeMicLab {

    companion object {
        init {
            System.loadLibrary("rudimentor_audio")
        }

        private const val HIT_DRAIN_CAPACITY = 32
        private const val TICK_DRAIN_CAPACITY = 32

        // Raw Oboe InputPreset values, so a log line can name the preset the
        // device actually granted instead of printing a bare number.
        private const val PRESET_GENERIC = 1
        private const val PRESET_CAMCORDER = 5
        private const val PRESET_VOICE_RECOGNITION = 6
        private const val PRESET_VOICE_COMMUNICATION = 7
        private const val PRESET_UNPROCESSED = 9
        private const val PRESET_VOICE_PERFORMANCE = 10
    }

    /** Snapshot of the running engine. Fields are decoded from a native int array. */
    data class Snapshot(
        val sampleRate: Int,
        val inputFrame: Long,
        /** Output stream frame counter: the clock tick events are stamped on. */
        val outputFrame: Long,
        val tickCount: Long,
        val envelope: Float,
        val threshold: Float,
        val peak: Float,
        val clickAudible: Boolean,
        val running: Boolean,
        /**
         * Measured distance between the render clock [outputFrame] runs on and the
         * sound the player actually hears, in milliseconds. 0 means the device gave
         * no timestamps, so no compensation is possible (decision 147).
         */
        val outputLatencyMs: Float = 0f,
    )

    /**
     * Stream facts that hold for a whole run, plus the counters that say whether the
     * audio path is healthy. Meant for the log at the start and the end of an attempt,
     * not for the poll loop: the native side takes the stream lock to build it.
     */
    data class StreamInfo(
        val sampleRate: Int,
        val outputBurstFrames: Int,
        val outputBufferFrames: Int,
        val inputBurstFrames: Int,
        val inputBufferFrames: Int,
        val inputCapacityFrames: Int,
        val outputExclusive: Boolean,
        val inputExclusive: Boolean,
        /** Raw Oboe input preset the device granted, or -1 when unknown. */
        val inputPreset: Int,
        /** Dropped buffers since the streams opened, or -1 when unsupported. */
        val outputXRuns: Int,
        val inputXRuns: Int,
        val errorCount: Int,
        val lastErrorCode: Int,
        val running: Boolean,
    ) {
        /** Name of the granted input preset, for the log line. */
        val inputPresetName: String
            get() = when (inputPreset) {
                PRESET_GENERIC -> "generic"
                PRESET_CAMCORDER -> "camcorder"
                PRESET_VOICE_RECOGNITION -> "voiceRecognition"
                PRESET_VOICE_COMMUNICATION -> "voiceCommunication"
                PRESET_UNPROCESSED -> "unprocessed"
                PRESET_VOICE_PERFORMANCE -> "voicePerformance"
                else -> "unknown"
            }
    }

    /** Onset event drained from the native ring buffer. */
    data class HitEvent(
        val frame: Long,
        val envelope: Float,
        val threshold: Float,
    )

    /** Tick event drained from the native ring buffer. */
    data class TickEvent(
        val frame: Long,
        val index: Long,
    )

    private val snapshotBuffer = IntArray(13)
    private val streamInfoBuffer = IntArray(14)
    private val hitBuffer = LongArray(HIT_DRAIN_CAPACITY * 3)
    private val tickBuffer = LongArray(TICK_DRAIN_CAPACITY * 2)

    fun start(): Boolean = nativeStart()
    fun stop() = nativeStop()

    fun setBpm(bpm: Int) = nativeSetBpm(bpm)

    /**
     * Installs the tempo of every beat of the attempt, count-in included, replacing the
     * fixed tempo. The engine switches tempo on the frame the beat starts on, which is
     * what keeps the click of a tempo ramp on its notes (decision 148). An empty array
     * goes back to the fixed tempo. Call it before [start].
     */
    fun setTempoPlan(bpmPerBeat: IntArray) = nativeSetTempoPlan(bpmPerBeat)

    fun setClickAudible(audible: Boolean) = nativeSetClickAudible(audible)
    fun setSensitivity(sensitivity01: Float) = nativeSetSensitivity(sensitivity01)
    fun setInputLatencyMillis(millis: Float) = nativeSetInputLatencyMillis(millis)

    fun snapshot(): Snapshot {
        nativeSnapshot(snapshotBuffer)
        val inputFrame = ((snapshotBuffer[2].toLong() and 0xFFFFFFFFL) shl 32) or
                (snapshotBuffer[1].toLong() and 0xFFFFFFFFL)
        val tickCount = ((snapshotBuffer[4].toLong() and 0xFFFFFFFFL) shl 32) or
                (snapshotBuffer[3].toLong() and 0xFFFFFFFFL)
        val outputFrame = ((snapshotBuffer[11].toLong() and 0xFFFFFFFFL) shl 32) or
                (snapshotBuffer[10].toLong() and 0xFFFFFFFFL)
        return Snapshot(
            sampleRate = snapshotBuffer[0],
            inputFrame = inputFrame,
            outputFrame = outputFrame,
            tickCount = tickCount,
            envelope = snapshotBuffer[5] / 1_000_000f,
            threshold = snapshotBuffer[6] / 1_000_000f,
            peak = snapshotBuffer[7] / 1_000_000f,
            clickAudible = snapshotBuffer[8] != 0,
            running = snapshotBuffer[9] != 0,
            outputLatencyMs = snapshotBuffer[12] / 1_000f,
        )
    }

    fun streamInfo(): StreamInfo {
        nativeStreamInfo(streamInfoBuffer)
        return StreamInfo(
            sampleRate = streamInfoBuffer[0],
            outputBurstFrames = streamInfoBuffer[1],
            outputBufferFrames = streamInfoBuffer[2],
            inputBurstFrames = streamInfoBuffer[3],
            inputBufferFrames = streamInfoBuffer[4],
            inputCapacityFrames = streamInfoBuffer[5],
            outputExclusive = streamInfoBuffer[6] != 0,
            inputExclusive = streamInfoBuffer[7] != 0,
            inputPreset = streamInfoBuffer[8],
            outputXRuns = streamInfoBuffer[9],
            inputXRuns = streamInfoBuffer[10],
            errorCount = streamInfoBuffer[11],
            lastErrorCode = streamInfoBuffer[12],
            running = streamInfoBuffer[13] != 0,
        )
    }

    fun drainHits(out: MutableList<HitEvent>) {
        val copied = nativeDrainHits(hitBuffer)
        for (i in 0 until copied) {
            out.add(
                HitEvent(
                    frame = hitBuffer[i * 3],
                    envelope = hitBuffer[i * 3 + 1] / 1_000_000f,
                    threshold = hitBuffer[i * 3 + 2] / 1_000_000f,
                )
            )
        }
    }

    fun drainTicks(out: MutableList<TickEvent>) {
        val copied = nativeDrainTicks(tickBuffer)
        for (i in 0 until copied) {
            out.add(
                TickEvent(
                    frame = tickBuffer[i * 2],
                    index = tickBuffer[i * 2 + 1],
                )
            )
        }
    }

    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeSetBpm(bpm: Int)
    private external fun nativeSetTempoPlan(bpmPerBeat: IntArray)
    private external fun nativeSetClickAudible(audible: Boolean)
    private external fun nativeSetSensitivity(sensitivity: Float)
    private external fun nativeSetInputLatencyMillis(millis: Float)
    private external fun nativeSnapshot(out: IntArray)
    private external fun nativeStreamInfo(out: IntArray)
    private external fun nativeDrainHits(out: LongArray): Int
    private external fun nativeDrainTicks(out: LongArray): Int

}
