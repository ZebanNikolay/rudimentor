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
        /**
         * Onsets the loudness gate dropped since the previous poll (decision 158).
         * Scoring never sees them; the log does, so a stroke that was gated away can be
         * told apart from a stroke that was never played.
         */
        val quietHits: List<Hit> = emptyList(),
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
        /**
         * Input half of the path, in milliseconds: what the stroke itself is late by. The
         * stream reports it, or [LatencyModel.DEFAULT_MIC_MS] stands in (decision 188).
         */
        val micLatencyMs: Float = 0f,
        /**
         * How far ahead of the render clock the picture is drawn, in milliseconds -- the
         * output half of the path. Equal to [micLatencyMs] subtracted from the round trip
         * once the round trip is measured, so that what the player sees and what the player
         * hears land on the hit line together (decision 188).
         */
        val visualShiftMs: Float = 0f,
        /**
         * Stream-start skew of this run, in milliseconds: how far the input counter had
         * advanced when the output stream first ran. The native drain subtracts it from
         * every hit, so a run whose skew differs from the run the trim was calibrated in
         * needs the difference corrected -- [appliedLatencyMs] carries that correction, and
         * this field is what makes it visible in the attempt log (decision 164).
         */
        val streamSkewMs: Float = 0f,
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

    /** Last measured microphone half, used until this run's stream reports its own. */
    private var storedMicLatencyMs: Float = 0f

    /**
     * Loudness an onset has to reach to be scored at all (decision 158). The onset
     * detector's own threshold is adaptive and sits on its floor in a noisy room, where it
     * reported room noise as strokes and the scoring counted it.
     */
    private var micThresholdLevel: Float = MicThreshold.DEFAULT_LEVEL

    /**
     * Output latency the first anchored poll of the attempt reported, the reference the
     * drift of a calibrated trim is measured from. Null until the stream reports one.
     */
    private var anchorOutputLatencyMs: Float? = null

    /**
     * Stream-start skew that held while [baseLatencyMs] was measured, or null when it is
     * unknown -- a guessed trim, a hand-set one, or one measured before decision 164. The
     * skew of the current run is subtracted from every hit by the native drain, so a
     * calibrated round trip is short by exactly the difference of the two skews; an unknown
     * skew means no correction, the behaviour those trims already had.
     */
    private var calibrationSkewMs: Float? = null

    /** Skew of this run, as the engine last reported it. Diagnostics for the log. */
    private var streamSkewMs: Float = 0f
    private val hitScratch = ArrayList<NativeMicLab.HitEvent>(32)
    private val tickScratch = ArrayList<NativeMicLab.TickEvent>(32)
    private val hitPositions = ArrayList<Hit>(8)
    private val quietPositions = ArrayList<Hit>(8)

    /**
     * Starts the engine. The click is silent by default: without headphones the
     * microphone hears it and scores it as a stick hit.
     */
    fun start(
        bpm: Int,
        clickAudible: Boolean = false,
        inputLatencyMs: Float = MicLab.DEFAULT_LATENCY_MS,
        latencyCalibrated: Boolean = false,
        calibrationSkewMs: Float? = null,
        /**
         * Microphone half of the round trip as it was last measured, or 0 when unknown. Used
         * only while this run's own stream has not reported its input latency yet, so the
         * picture does not start on a guess and then jump (decision 188).
         */
        storedMicLatencyMs: Float = 0f,
        sensitivity: Float = MicLab.DEFAULT_SENSITIVITY,
        micThresholdLevel: Float = MicThreshold.DEFAULT_LEVEL,
        tempoPlan: IntArray = IntArray(0),
    ): Boolean {
        if (running) return true
        this.bpm = bpm.coerceIn(MicLab.MIN_BPM, MicLab.MAX_BPM)
        this.tempoPlan = IntArray(tempoPlan.size) {
            tempoPlan[it].coerceIn(MicLab.MIN_BPM, MicLab.MAX_BPM)
        }
        anchorFrame = null
        anchorOutputLatencyMs = null
        streamSkewMs = 0f
        this.calibrationSkewMs = calibrationSkewMs
        this.latencyCalibrated = latencyCalibrated
        this.storedMicLatencyMs = storedMicLatencyMs
        this.micThresholdLevel = MicThreshold.clamp(micThresholdLevel)
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

    fun setInputLatencyMs(
        millis: Float,
        calibrated: Boolean = latencyCalibrated,
        calibrationSkewMs: Float? = this.calibrationSkewMs,
    ) {
        baseLatencyMs = millis.coerceIn(LATENCY_TRIM_MIN_MS, LATENCY_TRIM_MAX_MS)
        latencyCalibrated = calibrated
        this.calibrationSkewMs = calibrationSkewMs
        appliedLatencyMs = baseLatencyMs
        native.setInputLatencyMillis(baseLatencyMs)
    }

    fun setSensitivity(value: Float) = native.setSensitivity(value.coerceIn(0f, 1f))

    fun setMicThresholdLevel(level: Float) {
        micThresholdLevel = MicThreshold.clamp(level)
    }

    /**
     * Audio stream parameters and fault counters. This takes the engine lock, so
     * it is meant for the start and the end of an attempt, never for the poll loop.
     */
    fun streamInfo(): NativeMicLab.StreamInfo = native.streamInfo()

    fun poll(): Poll {
        tickScratch.clear()
        hitScratch.clear()
        hitPositions.clear()
        quietPositions.clear()
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
        // The skew of this run is kept for the log only. Correcting the trim by the
        // difference between it and the skew of the calibration run was tried in dev.40 and
        // the field data refused it: two runs 552 ms apart in skew were late by 145 ms and
        // 135 ms, so the residual does not follow the skew at all and subtracting the
        // difference would have thrown a run half a second off (decision 165).
        streamSkewMs = snapshot.streamSkewMs
        val target = if (latencyCalibrated) {
            (
                baseLatencyMs +
                    (outputLatencyMs - (anchorOutputLatencyMs ?: outputLatencyMs))
                ).coerceIn(LATENCY_TRIM_MIN_MS, LATENCY_TRIM_MAX_MS)
        } else {
            baseLatencyMs + outputLatencyMs
        }
        if (kotlin.math.abs(target - appliedLatencyMs) > LATENCY_STEP_MS) {
            appliedLatencyMs = target
            native.setInputLatencyMillis(appliedLatencyMs)
        }
        // How far the notes are drawn ahead of the render clock: the output half of the
        // path, and nothing else (decision 188).
        //
        // A measured round trip decides it, split by the input latency the input stream
        // reports. What must not decide it is `calculateLatencyMillis()` on the output
        // stream: over A2DP this phone answers 4 ms, which is not zero, so the split was
        // never reached -- the picture stood on the render clock while the strokes had the
        // whole round trip taken off them. A player following the picture was then judged
        // a full output latency early, and the dev.48 log scored a run of 39 near-perfect
        // strokes as 0.0 %. The OS number is still worth its drift: A2DP latency moves
        // during a run, and the picture has to move with it.
        val reportedMicMs = if (snapshot.inputLatencyMs > 0f) {
            snapshot.inputLatencyMs
        } else {
            storedMicLatencyMs
        }
        val micLatencyMs = LatencyModel.micPart(reportedMicMs, baseLatencyMs)
        val visualShiftMs = if (latencyCalibrated) {
            (
                LatencyModel.outputPart(baseLatencyMs, micLatencyMs) +
                    (outputLatencyMs - (anchorOutputLatencyMs ?: outputLatencyMs))
                ).coerceAtLeast(0f)
        } else {
            outputLatencyMs
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
            val entry = Hit(
                // The native drain already subtracted the anchor-relative latency
                // compensation (trim + measured output latency), so the hit lands
                // on the same presentation timeline the notes are drawn on.
                positionMs = (hit.frame - anchor) / framesPerMs,
                envelope = hit.envelope,
                threshold = hit.threshold,
            )
            if (MicThreshold.passes(hit.envelope, micThresholdLevel)) {
                hitPositions.add(entry)
            } else {
                quietPositions.add(entry)
            }
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
            quietHits = if (quietPositions.isEmpty()) emptyList() else ArrayList(quietPositions),
            envelope = snapshot.envelope,
            threshold = snapshot.threshold,
            peak = snapshot.peak,
            running = snapshot.running,
            clockSkewMs = (snapshot.inputFrame - snapshot.outputFrame) / framesPerMs,
            outputLatencyMs = outputLatencyMs,
            appliedLatencyMs = appliedLatencyMs,
            streamSkewMs = streamSkewMs,
            micLatencyMs = micLatencyMs,
            visualShiftMs = visualShiftMs,
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
         * Where the input half of the path now comes from: measured per run, no longer a
         * constant. Kept as an alias so the number has one home (decision 188).
         */
        const val INPUT_PART_MS = LatencyModel.DEFAULT_MIC_MS
    }
}
