package com.rudimentor.app.util

import android.os.Debug
import android.os.SystemClock
import kotlin.math.roundToInt

/**
 * Catches the one long stall a practice run reports.
 *
 * Every attempt freezes once, for something close to half a second, roughly a third of
 * the way in -- reproducible on every level. Nothing in the poll loop explains it, so
 * instead of guessing this watches the loop itself: the poll runs on the main thread at a
 * fixed interval, so the wall-clock gap between two polls *is* how long the main thread
 * was blocked. Anything past [thresholdMs] is written down together with the runtime's
 * garbage-collector counters, which is what tells a blocking GC pause apart from a
 * one-off load (fonts, class init, a native call) on the same thread.
 *
 * Diagnostics only: nothing here changes what the attempt does, and reading the counters
 * costs a map lookup that only happens when a stall was already detected.
 */
class StallWatch(private val thresholdMs: Long = DEFAULT_THRESHOLD_MS) {

    private var lastTickMs = 0L
    private var lastGcCount = 0L
    private var lastGcTimeMs = 0L
    private var lastBlockingCount = 0L
    private var lastBlockingTimeMs = 0L
    private var stalls = 0

    /** Call once when the run starts, before the first poll. */
    fun start() {
        lastTickMs = SystemClock.uptimeMillis()
        stalls = 0
        readCounters()
    }

    /**
     * Call once per poll. Returns a log line when this poll came in later than the
     * expected [intervalMs] by more than [thresholdMs], or null on a healthy poll.
     */
    fun tick(atMs: Float, intervalMs: Long): String? {
        val now = SystemClock.uptimeMillis()
        val previous = lastTickMs
        lastTickMs = now
        if (previous == 0L) return null
        val stall = now - previous - intervalMs
        if (stall < thresholdMs) return null

        val gcCount = lastGcCount
        val gcTime = lastGcTimeMs
        val blockingCount = lastBlockingCount
        val blockingTime = lastBlockingTimeMs
        readCounters()
        stalls += 1
        return "stall #$stalls: main thread blocked ${stall} ms at ${atMs.roundToInt()} ms, " +
            "gc +${lastGcCount - gcCount} runs / ${lastGcTimeMs - gcTime} ms, " +
            "blocking gc +${lastBlockingCount - blockingCount} runs / " +
            "${lastBlockingTimeMs - blockingTime} ms"
    }

    private fun readCounters() {
        val stats = runCatching { Debug.getRuntimeStats() }.getOrNull() ?: return
        lastGcCount = stats.stat(STAT_GC_COUNT)
        lastGcTimeMs = stats.stat(STAT_GC_TIME)
        lastBlockingCount = stats.stat(STAT_BLOCKING_COUNT)
        lastBlockingTimeMs = stats.stat(STAT_BLOCKING_TIME)
    }

    private fun Map<String, String>.stat(key: String): Long = this[key]?.toLongOrNull() ?: 0L

    companion object {
        /**
         * A poll that is this much later than its interval is a stall. Two dropped frames
         * at 60 Hz are still normal scheduling noise; 100 ms is visible to the player.
         */
        const val DEFAULT_THRESHOLD_MS = 100L

        private const val STAT_GC_COUNT = "art.gc.gc-count"
        private const val STAT_GC_TIME = "art.gc.gc-time"
        private const val STAT_BLOCKING_COUNT = "art.gc.blocking-gc-count"
        private const val STAT_BLOCKING_TIME = "art.gc.blocking-gc-time"
    }
}
