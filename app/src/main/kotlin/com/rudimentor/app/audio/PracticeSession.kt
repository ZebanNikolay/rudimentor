package com.rudimentor.app.audio

/**
 * The engine of one practice attempt: the metronome grid and the microphone on a
 * single shared clock.
 *
 * It reuses [NativeMicLab] as is -- that engine already runs one full-duplex Oboe
 * session with the click on the output stream and the onset detector on the input
 * stream, both on the same sample rate, which is exactly what beat-based scoring
 * needs (decision 89).
 *
 * The class is deliberately synchronous: the practice screen calls [poll] from its
 * own loop on the main thread, so no scoring state is ever touched from two
 * threads at once. One poll does a few array reads, so the cost is negligible.
 */
class PracticeSession(
    private val native: NativeMicLab = NativeMicLab(),
) {

    /** Everything one poll produced. */
    data class Poll(
        /** True once the first tick arrived and [positionMs] is meaningful. */
        val anchored: Boolean,
        /** Time since the first count-in beat, on the same clock as the note list. */
        val positionMs: Float,
        /** Stick hits detected since the previous poll, in the same milliseconds. */
        val hits: List<Float>,
        val envelope: Float,
        val threshold: Float,
        val running: Boolean,
        /**
         * Difference between the input and the output frame counter in
         * milliseconds. Diagnostics only: on a healthy stream pair it stays close
         * to constant, and a drift here means the two clocks are not one clock.
         */
        val clockSkewMs: Float = 0f,
    )

    var bpm: Int = MicLab.DEFAULT_BPM
        private set

    var running: Boolean = false
        private set

    private var anchorFrame: Long? = null
    private val hitScratch = ArrayList<NativeMicLab.HitEvent>(32)
    private val tickScratch = ArrayList<NativeMicLab.TickEvent>(32)
    private val hitPositions = ArrayList<Float>(8)

    /**
     * Starts the engine. The click is silent by default: without headphones the
     * microphone hears it and scores it as a stick hit.
     */
    fun start(
        bpm: Int,
        clickAudible: Boolean = false,
        inputLatencyMs: Float = MicLab.DEFAULT_LATENCY_MS,
        sensitivity: Float = MicLab.DEFAULT_SENSITIVITY,
    ): Boolean {
        if (running) return true
        this.bpm = bpm.coerceIn(MicLab.MIN_BPM, MicLab.MAX_BPM)
        anchorFrame = null
        hitScratch.clear()
        tickScratch.clear()
        native.setBpm(this.bpm)
        native.setClickAudible(clickAudible)
        native.setSensitivity(sensitivity.coerceIn(0f, 1f))
        native.setInputLatencyMillis(inputLatencyMs)
        running = native.start()
        return running
    }

    fun stop() {
        if (!running) return
        native.stop()
        running = false
        anchorFrame = null
    }

    fun setClickAudible(audible: Boolean) = native.setClickAudible(audible)

    fun setInputLatencyMs(millis: Float) =
        native.setInputLatencyMillis(millis.coerceIn(-100f, 300f))

    fun setSensitivity(value: Float) = native.setSensitivity(value.coerceIn(0f, 1f))

    fun poll(): Poll {
        tickScratch.clear()
        hitScratch.clear()
        hitPositions.clear()
        native.drainTicks(tickScratch)
        native.drainHits(hitScratch)
        val snapshot = native.snapshot()
        val framesPerMs = snapshot.sampleRate / 1000f
        val framesPerBeat = if (bpm > 0) snapshot.sampleRate * 60.0 / bpm else 0.0

        // The clock of the attempt starts at tick 0, not at engine start: the first
        // tick tells us where the grid actually landed, and its index lets us project
        // back to tick 0 even when Kotlin sees the second or third tick first.
        if (anchorFrame == null && framesPerBeat > 0.0) {
            val first = tickScratch.firstOrNull()
            if (first != null) {
                anchorFrame = first.frame - (first.index * framesPerBeat).toLong()
            }
        }

        val anchor = anchorFrame
        if (anchor == null || framesPerMs <= 0f) {
            return Poll(
                anchored = false,
                positionMs = 0f,
                hits = emptyList(),
                envelope = snapshot.envelope,
                threshold = snapshot.threshold,
                running = snapshot.running,
            )
        }

        for (hit in hitScratch) {
            hitPositions.add((hit.frame - anchor) / framesPerMs)
        }
        // The position runs on the output clock, the same clock the ticks -- and so
        // the anchor, and the hit frames after their re-anchoring -- are stamped on.
        // Reading the input counter here shifted the whole visual timeline by the
        // stream skew plus the input latency, so every late stroke read as a miss
        // while the notes were drawn ahead of their own sound (decision 98).
        return Poll(
            anchored = true,
            positionMs = (snapshot.outputFrame - anchor) / framesPerMs,
            hits = if (hitPositions.isEmpty()) emptyList() else ArrayList(hitPositions),
            envelope = snapshot.envelope,
            threshold = snapshot.threshold,
            running = snapshot.running,
            clockSkewMs = (snapshot.inputFrame - snapshot.outputFrame) / framesPerMs,
        )
    }

    companion object {
        /** Same 8 ms cadence the mic lab polls at (~120 Hz). */
        const val POLL_INTERVAL_MS = 8L
    }
}
