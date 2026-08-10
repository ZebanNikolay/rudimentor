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
    }

    /** Snapshot of the running engine. Fields are decoded from a native int array. */
    data class Snapshot(
        val sampleRate: Int,
        val inputFrame: Long,
        val tickCount: Long,
        val envelope: Float,
        val threshold: Float,
        val peak: Float,
        val clickAudible: Boolean,
        val running: Boolean,
    )

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

    private val snapshotBuffer = IntArray(10)
    private val hitBuffer = LongArray(HIT_DRAIN_CAPACITY * 3)
    private val tickBuffer = LongArray(TICK_DRAIN_CAPACITY * 2)

    fun start(): Boolean = nativeStart()
    fun stop() = nativeStop()

    fun setBpm(bpm: Int) = nativeSetBpm(bpm)
    fun setClickAudible(audible: Boolean) = nativeSetClickAudible(audible)
    fun setSensitivity(sensitivity01: Float) = nativeSetSensitivity(sensitivity01)
    fun setInputLatencyMillis(millis: Float) = nativeSetInputLatencyMillis(millis)

    fun snapshot(): Snapshot {
        nativeSnapshot(snapshotBuffer)
        val inputFrame = ((snapshotBuffer[2].toLong() and 0xFFFFFFFFL) shl 32) or
                (snapshotBuffer[1].toLong() and 0xFFFFFFFFL)
        val tickCount = ((snapshotBuffer[4].toLong() and 0xFFFFFFFFL) shl 32) or
                (snapshotBuffer[3].toLong() and 0xFFFFFFFFL)
        return Snapshot(
            sampleRate = snapshotBuffer[0],
            inputFrame = inputFrame,
            tickCount = tickCount,
            envelope = snapshotBuffer[5] / 1_000_000f,
            threshold = snapshotBuffer[6] / 1_000_000f,
            peak = snapshotBuffer[7] / 1_000_000f,
            clickAudible = snapshotBuffer[8] != 0,
            running = snapshotBuffer[9] != 0,
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
    private external fun nativeSetClickAudible(audible: Boolean)
    private external fun nativeSetSensitivity(sensitivity: Float)
    private external fun nativeSetInputLatencyMillis(millis: Float)
    private external fun nativeSnapshot(out: IntArray)
    private external fun nativeDrainHits(out: LongArray): Int
    private external fun nativeDrainTicks(out: LongArray): Int

    private companion object {
        const val HIT_DRAIN_CAPACITY = 32
        const val TICK_DRAIN_CAPACITY = 32
    }
}
