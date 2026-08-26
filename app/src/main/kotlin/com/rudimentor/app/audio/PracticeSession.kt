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

    /**
     * One detected stick hit: when it landed on the attempt clock, plus the
     * detector state that let it through. The detector numbers are diagnostics --
     * scoring only uses [positionMs] -- but they are what makes a practice log
     * useful when a stroke is missed or a ghost hit appears.
     */
    data class Hit(
        val positionMs: Float,
        val envelope: Float,
        val threshold: Float,
    )

    /** Everything one poll produced. */
    data class Poll(
        /** True once the first tick arrived and [positionMs] is meaningful. */
        val anchored: Boolean,
        /** Time since the first count-in beat, on the same clock as the note list. */
        val positionMs: Float,
        /** Stick hits detected since the previous poll, in the same milliseconds. */
        val hits: List<Hit>,
        val envelope: Float,
        val threshold: Float,
        /** Loudest envelope value the detector has seen since the last reset. */
        val peak: Float,
        val running: Boolean,
        /**
         * Difference between the input and the output frame counter in
         * milliseconds. Diagnostics only: on a healthy stream pair it stays close
         * to constant, and a drift here means the two clocks are not one clock.
         */
        val clockSkewMs: Float = 0f,
        /**
         * Measured output latency the timeline is corrected by, in milliseconds.
         * Diagnostics: on the speaker path it is tens of milliseconds, on Bluetooth
         * it is hundreds and it moves.
         */
        val outputLatencyMs: Float = 0f,
        /**
         * Compensation actually pushed into the detector, in milliseconds: the trim from
         * settings plus whatever the latency model added to it (decision 154).
         */
        val appliedLatencyMs: Float = 0f,
    )

    var bpm: Int = MicLab.DEFAULT_BPM
        private set

    /**
     * Tempo of every beat of the attempt, count-in included, or empty when the attempt
     * runs at the single [bpm]. A tempo ramp makes beats different lengths, so the
     * anchor of the timeline cannot be projected back from the first tick with one beat
     * length (decision 148).
     */
    var tempoPlan: IntArray = IntArray(0)
        private set

    var running: Boolean = false
        private set

    private var anchorFrame: Long? = null

    /**
     * The latency trim from settings. What the engine does with it depends on where it
     * came from (decision 154):
     *
     *  * guessed (`latencyCalibrated == false`) -- it is the residual of the speaker path
     *    only, so the measured output latency is added on top of it, as before
     *    (decision 147);
     *  * measured by the calibration screen -- it already *is* the whole round trip,
     *    output included, so adding the output latency again would compensate twice. Only
     *    the drift away from the latency that held while calibrating is applied.
     */
    private var baseLatencyMs: Float = MicLab.DEFAULT_LATENCY_MS
    private var appliedLatencyMs: Float = MicLab.DEFAULT_LATENCY_MS
    private var latencyCalibrated: Boolean = false

    /**
     * Output latency the first anchored poll of the attempt reported, the reference the
     * drift of a calibrated trim is measured from. Null until the stream reports one.
     */
    private var anchorOutputLatencyMs: Float? = null
    private val hitScratch = ArrayList<NativeMicLab.HitEvent>(32)
    private val tickScratch = ArrayList<NativeMicLab.TickEvent>(32)
    private val hitPositions = ArrayList<Hit>(8)

    /**
     * Starts the engine. The click is silent by default: without headphones the
     * microphone hears it and scores it as a stick hit.
     */
    fun start(
        bpm: Int,
        clickAudible: Boolean = false,
        inputLatencyMs: Float = MicLab.DEFAULT_LATENCY_MS,
        latencyCalibrated: Boolean = false,
        sensitivity: Float = MicLab.DEFAULT_SENSITIVITY,
        tempoPlan: IntArray = IntArray(0),
    ): Boolean {
        if (running) return true
        this.bpm = bpm.coerceIn(MicLab.MIN_BPM, MicLab.MAX_BPM)
        this.tempoPlan = IntArray(tempoPlan.size) {
            tempoPlan[it].coerceIn(MicLab.MIN_BPM, MicLab.MAX_BPM)
        }
        anchorFrame = null
        anchorOutputLatencyMs = null
        this.latencyCalibrated = latencyCalibrated
        hitScratch.clear()
        tickScratch.clear()
        native.setBpm(this.bpm)
        native.setTempoPlan(this.tempoPlan)
        native.setClickAudible(clickAudible)
        native.setSensitivity(sensitivity.coerceIn(0f, 1f))
        baseLatencyMs = inputLatencyMs
        appliedLatencyMs = inputLatencyMs
        native.setInputLatencyMillis(inputLatencyMs)
        running = native.start()
        return running
    }

    fun stop() {
        if (!running) return
        native.stop()
        running = false
        anchorFrame = null
        anchorOutputLatencyMs = null
    }

    fun setClickAudible(audible: Boolean) = native.setClickAudible(audible)

    fun setInputLatencyMs(millis: Float, calibrated: Boolean = latencyCalibrated) {
        baseLatencyMs = millis.coerceIn(LATENCY_TRIM_MIN_MS, LATENCY_TRIM_MAX_MS)
        latencyCalibrated = calibrated
        appliedLatencyMs = baseLatencyMs
        native.setInputLatencyMillis(baseLatencyMs)
    }

    fun setSensitivity(value: Float) = native.setSensitivity(value.coerceIn(0f, 1f))

    /**
     * Audio stream parameters and fault counters. This takes the engine lock, so
     * it is meant for the start and the end of an attempt, never for the poll loop.
     */
    fun streamInfo(): NativeMicLab.StreamInfo = native.streamInfo()

    fun poll(): Poll {
        tickScratch.clear()
        hitScratch.clear()
        hitPositions.clear()
        native.drainTicks(tickScratch)
        native.drainHits(hitScratch)
        val snapshot = native.snapshot()
        val framesPerMs = snapshot.sampleRate / 1000f

        // The clock of the attempt starts at tick 0, not at engine start: the first
        // tick tells us where the grid actually landed, and its index lets us project
        // back to tick 0 even when Kotlin sees the second or third tick first. Under a
        // tempo plan the beats before it are not all the same length, so the projection
        // sums their real lengths instead of multiplying one of them.
        if (anchorFrame == null && bpm > 0) {
            val first = tickScratch.firstOrNull()
            if (first != null) {
                anchorFrame = first.frame - framesBeforeBeat(first.index, snapshot.sampleRate)
            }
        }

        // Everything the player can perceive lives on the presentation clock, not on
        // the render clock: `outputFrame` counts frames written into the buffer, so a
        // click written now is only heard `outputLatencyMs` later. Drawing the notes
        // on the render clock is what put the audible click roughly half a note away
        // from its own note over Bluetooth, and drifting because A2DP latency drifts
        // (decision 147). The same shift has to reach the hit compensation, or a
        // stroke played to the corrected picture would read late by the same amount.
        //
        // A calibrated trim is the exception: it was measured through the very same
        // output path, so it already carries that latency. Adding it again put the hits
        // a whole round trip early -- the 190-230 ms offsets of the dev.36 field log
        // (decision 154). For it only the drift since the anchor is applied, and it is
        // applied to the picture and to the hits alike.
        val outputLatencyMs = snapshot.outputLatencyMs
        if (anchorOutputLatencyMs == null && outputLatencyMs > 0f) {
            anchorOutputLatencyMs = outputLatencyMs
        }
        val target = if (latencyCalibrated) {
            baseLatencyMs + (outputLatencyMs - (anchorOutputLatencyMs ?: outputLatencyMs))
        } else {
            baseLatencyMs + outputLatencyMs
        }
        if (kotlin.math.abs(target - appliedLatencyMs) > LATENCY_STEP_MS) {
            appliedLatencyMs = target
            native.setInputLatencyMillis(appliedLatencyMs)
        }
        // How far the notes are drawn ahead of the render clock. Normally that is the
        // measured output latency; when the OS reports none -- A2DP on this device gives
        // no timestamps -- a calibrated round trip still knows roughly how much of it
        // was the output side, which is better than drawing on the render clock.
        val visualShiftMs = when {
            outputLatencyMs > 0f -> outputLatencyMs
            latencyCalibrated -> (baseLatencyMs - INPUT_PART_MS).coerceAtLeast(0f)
            else -> 0f
        }

        val anchor = anchorFrame
        if (anchor == null || framesPerMs <= 0f) {
            return Poll(
                anchored = false,
                positionMs = 0f,
                hits = emptyList(),
                envelope = snapshot.envelope,
                threshold = snapshot.threshold,
                peak = snapshot.peak,
                running = snapshot.running,
            )
        }

        for (hit in hitScratch) {
            hitPositions.add(
                Hit(
                    // The native drain already subtracted the anchor-relative latency
                    // compensation (trim + measured output latency), so the hit lands
                    // on the same presentation timeline the notes are drawn on.
                    positionMs = (hit.frame - anchor) / framesPerMs,
                    envelope = hit.envelope,
                    threshold = hit.threshold,
                ),
            )
        }
        // The position runs on the output clock, the same clock the ticks -- and so
        // the anchor, and the hit frames after their re-anchoring -- are stamped on.
        // Reading the input counter here shifted the whole visual timeline by the
        // stream skew plus the input latency, so every late stroke read as a miss
        // while the notes were drawn ahead of their own sound (decision 101).
        return Poll(
            anchored = true,
            positionMs = (snapshot.outputFrame - anchor) / framesPerMs - visualShiftMs,
            hits = if (hitPositions.isEmpty()) emptyList() else ArrayList(hitPositions),
            envelope = snapshot.envelope,
            threshold = snapshot.threshold,
            peak = snapshot.peak,
            running = snapshot.running,
            clockSkewMs = (snapshot.inputFrame - snapshot.outputFrame) / framesPerMs,
            outputLatencyMs = outputLatencyMs,
            appliedLatencyMs = appliedLatencyMs,
        )
    }

    /** Frames the engine spends on the beats before [beat], at [sampleRate]. */
    private fun framesBeforeBeat(beat: Long, sampleRate: Int): Long {
        if (beat <= 0L || sampleRate <= 0) return 0L
        val plan = tempoPlan
        if (plan.isEmpty()) return (beat * sampleRate * 60.0 / bpm).toLong()
        var frames = 0.0
        for (i in 0 until beat) {
            frames += sampleRate * 60.0 / plan[(i % plan.size).toInt()]
        }
        return frames.toLong()
    }

    companion object {
        /** Same 8 ms cadence the mic lab polls at (~120 Hz). */
        const val POLL_INTERVAL_MS = 8L

        /**
         * How far the measured output latency has to move before the native hit
         * compensation is re-pushed. Small enough to follow Bluetooth drift, large
         * enough not to write an atomic on every poll.
         */
        private const val LATENCY_STEP_MS = 2f

        /** Range the native hit compensation accepts, in milliseconds. */
        private const val LATENCY_TRIM_MIN_MS = -100f
        private const val LATENCY_TRIM_MAX_MS = 400f

        /**
         * Rough input side of a round trip: microphone buffer plus onset detection. Only
         * used to split a calibrated round trip when the OS reports no output latency
         * at all (decision 154).
         */
        const val INPUT_PART_MS = 25f
    }
}
