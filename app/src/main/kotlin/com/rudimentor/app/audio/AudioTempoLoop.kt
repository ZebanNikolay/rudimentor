package com.rudimentor.app.audio

import kotlin.math.ceil

/** Pure counterpart of native AudioTempoLoop.h; no Android or native library needed. */
internal object AudioTempoLoop {
    /** Must match AudioTempoLoop::kMaxPlanBeats on the native side. */
    const val MAX_PLAN_BEATS = 512

    fun boundedPlan(plan: IntArray): IntArray = IntArray(plan.size.coerceAtMost(MAX_PLAN_BEATS)) {
        Bpm.clamp(plan[it])
    }

    /** An empty suffix cannot repeat. Invalid requests retain legacy whole-plan looping. */
    fun normalizedLoopStart(loopStart: Int, planSize: Int): Int =
        if (loopStart in 0 until planSize) loopStart else 0

    /**
     * Integer frame offset from tick 0 to [beat]. Native emits a tick on the first
     * integer frame at or after its fractional deadline, including its 300 ms warm-up.
     * Sum at most one plan, never all elapsed beats: the first tick Kotlin sees may
     * follow many lost ticks, or belong to a much later loop.
     */
    fun framesBeforeBeat(
        beat: Long,
        sampleRate: Int,
        bpm: Int,
        plan: IntArray,
        loopStart: Int = 0,
    ): Long {
        if (beat <= 0L || sampleRate <= 0) return 0L
        val size = plan.size.coerceAtMost(MAX_PLAN_BEATS)
        val elapsed = if (size == 0) {
            // Convert before multiplying: a Long beat index must not overflow first.
            beat.toDouble() * (sampleRate * 60.0 / Bpm.clamp(bpm))
        } else {
            val prefix = DoubleArray(size + 1)
            for (i in 0 until size) {
                prefix[i + 1] = prefix[i] + sampleRate * 60.0 / Bpm.clamp(plan[i])
            }
            val start = normalizedLoopStart(loopStart, size)
            if (beat < start.toLong()) {
                prefix[beat.toInt()]
            } else {
                val suffixSize = size - start
                val suffixBeats = beat - start
                val cycles = suffixBeats / suffixSize
                val remainder = (suffixBeats % suffixSize).toInt()
                prefix[start] + cycles.toDouble() * (prefix[size] - prefix[start]) +
                    (prefix[start + remainder] - prefix[start])
            }
        }
        val warmUp = sampleRate * 0.30
        return (ceil(warmUp + elapsed) - ceil(warmUp)).toLong()
    }

    /** Subtract integral clocks first; never narrow the absolute frame delta to Float. */
    fun positionMsExact(frame: Long, anchor: Long, sampleRate: Int): Double =
        if (sampleRate > 0) (frame - anchor).toDouble() / (sampleRate / 1000.0) else 0.0
}
