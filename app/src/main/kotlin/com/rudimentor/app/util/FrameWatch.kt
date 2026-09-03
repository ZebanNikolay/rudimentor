package com.rudimentor.app.util

import android.view.Choreographer
import kotlin.math.roundToInt

/**
 * Catches a freeze the player sees but [StallWatch] cannot.
 *
 * [StallWatch] measures the practice poll loop: it only fires when the main thread was
 * *blocked*, so its silence means the loop kept its schedule. It says nothing about the
 * picture. A run whose frames take 300 ms each -- a shader compile, a slow layout pass, a
 * stalled render thread -- keeps every coroutine on time while the lane visibly stops.
 * Six dev.55 attempt logs came back with no stall record at all, so the freeze Nikolai
 * reports has to be looked for one level lower: at the frames themselves.
 *
 * This subscribes to the frame clock and measures the gap between two consecutive frames.
 * The gap is what the eye actually experiences: at 120 Hz a frame is 8 ms, so anything
 * past [thresholdMs] is a visible hitch, not scheduling noise.
 *
 * Diagnostics only. The callback does arithmetic on two longs and re-posts itself; it adds
 * no work to a healthy frame, and it never changes what the attempt does.
 */
class FrameWatch(private val thresholdMs: Long = DEFAULT_THRESHOLD_MS) {

    private var lastFrameNs = 0L
    private var frames = 0
    private var hitches = 0
    private var worstMs = 0L
    private var running = false
    private var onHitch: ((String) -> Unit)? = null
    private var positionMs: () -> Float = { 0f }

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val previous = lastFrameNs
            lastFrameNs = frameTimeNanos
            if (previous != 0L) {
                frames += 1
                val gapMs = (frameTimeNanos - previous) / 1_000_000L
                if (gapMs >= thresholdMs) {
                    hitches += 1
                    if (gapMs > worstMs) worstMs = gapMs
                    onHitch?.invoke(
                        "frame hitch #$hitches: picture froze $gapMs ms at " +
                            "${positionMs().roundToInt()} ms",
                    )
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /**
     * Starts watching. [positionMs] is read only when a hitch is found, so the report
     * carries the place in the attempt the player would point at.
     */
    fun start(positionMs: () -> Float, onHitch: (String) -> Unit) {
        if (running) return
        this.positionMs = positionMs
        this.onHitch = onHitch
        lastFrameNs = 0L
        frames = 0
        hitches = 0
        worstMs = 0L
        running = true
        Choreographer.getInstance().postFrameCallback(callback)
    }

    /**
     * Stops watching and returns the one line the attempt log keeps even when nothing
     * froze -- a clean run has to be provable, otherwise the next report is guesswork
     * again.
     */
    fun stop(): String {
        running = false
        onHitch = null
        Choreographer.getInstance().removeFrameCallback(callback)
        return "frames $frames · hitches $hitches (over $thresholdMs ms) · worst $worstMs ms"
    }

    companion object {
        /**
         * A frame gap this long is a hitch. Four dropped frames at 60 Hz is already a
         * visible stutter, and the freeze being chased is reported as roughly half a
         * second, so the threshold is low enough to catch the small ones on the way.
         */
        const val DEFAULT_THRESHOLD_MS = 64L
    }
}
