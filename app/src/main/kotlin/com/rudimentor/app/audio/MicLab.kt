package com.rudimentor.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * High-level controller around [NativeMicLab] that:
 *   1. Owns the native engine lifecycle.
 *   2. Polls hits + ticks at ~120 Hz on a background coroutine.
 *   3. Pairs each hit with the nearest known tick and emits a
 *      [MicLabEvent.Hit] with the signed offset in milliseconds.
 *   4. Publishes tick events for the UI's metronome track.
 *   5. Keeps a stable [status] snapshot for HUD chrome (envelope, threshold,
 *      last-hit stats).
 *
 * The class is dev-only glue: everything user-visible lives in the UI layer.
 */
class MicLab(
    private val native: NativeMicLab = NativeMicLab(),
) {

    /** Union type for the UI event stream. */
    sealed interface MicLabEvent {
        data class Tick(val index: Long, val frame: Long) : MicLabEvent
        data class Hit(
            val frame: Long,
            val nearestTickFrame: Long,
            val nearestTickIndex: Long,
            val offsetMs: Float,
            val envelope: Float,
            val threshold: Float,
            /**
             * Whether the onset passed the loudness gate of [MicThreshold]. A quiet onset
             * is still reported -- the calibration meter and the log want to see the room
             * noise -- but it must not be measured as a stroke (decision 158).
             */
            val loud: Boolean,
        ) : MicLabEvent
    }

    data class Status(
        val running: Boolean = false,
        /**
         * How many times the streams were reopened after the audio device changed under
         * them. Oboe closes both streams when headphones are connected or disconnected and
         * never reopens them, so the click used to disappear for good; a screen that is
         * measuring has to know it happened, because the measurement belongs to the output
         * that just went away (decision 190).
         */
        val restarts: Int = 0,
        val sampleRate: Int = 0,
        val tickCount: Long = 0,
        val bpm: Int = DEFAULT_BPM,
        val envelope: Float = 0f,
        val threshold: Float = 0f,
        val peak: Float = 0f,
        // Off by default: without headphones the mic hears its own click
        // and scores it as a hit (see MicLabEngine's clickAudible_ comment).
        val clickAudible: Boolean = false,
        val sensitivity: Float = DEFAULT_SENSITIVITY,
        val inputLatencyMs: Float = DEFAULT_LATENCY_MS,
        val micThresholdLevel: Float = MicThreshold.MIN_LEVEL,
        val hitCount: Int = 0,
        val meanOffsetMs: Float = 0f,
        val stdDevMs: Float = 0f,
        /**
         * Stream-start skew of this run, in milliseconds. Every hit frame is re-anchored by
         * it, so a round trip measured here is only worth as much as the skew it was
         * measured under: calibration stores it alongside the number so the attempt that
         * follows can correct for its own skew (decision 164).
         */
        val streamSkewMs: Float = 0f,
        /**
         * Measured input half of the path, in milliseconds: how late the stroke reaches the
         * detector. Not to be confused with [inputLatencyMs], which is the compensation the
         * engine has been told to take off. This one is measured, and it is what splits a
         * round trip into the half that moves the stroke and the half that moves the picture
         * and the click (decision 188). 0 means the stream reported nothing.
         */
        val micPathLatencyMs: Float = 0f,
        /**
         * Measured output half as the OS reports it, in milliseconds. Over A2DP this phone
         * answers a few milliseconds and means it, so the number is kept for the log and
         * never used to place the picture on its own (decision 188).
         */
        val outputPathLatencyMs: Float = 0f,
        /**
         * Latest line of the clock diagnostic, refreshed about once a second while the
         * streams run: does the note grid tick at the same real rate as the stroke grid
         * (decision 188). Diagnostics only, nothing is corrected by it.
         */
        val clockDrift: StreamClockDrift.Reading? = null,
    )

    private val _events = MutableSharedFlow<MicLabEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<MicLabEvent> = _events.asSharedFlow()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var pollJob: Job? = null
    private var restarts = 0
    private var restartAtNanos = 0L

    private val pendingTicks = ArrayDeque<NativeMicLab.TickEvent>()
    private val recentOffsets = LinkedList<Float>()
    private val hitScratch = ArrayList<NativeMicLab.HitEvent>(32)
    private val tickScratch = ArrayList<NativeMicLab.TickEvent>(32)

    /** Clock-rate diagnostic of the current round, fed once per poll (decision 188). */
    private val clockDrift = StreamClockDrift()

    /**
     * Opens the stream and starts polling it.
     *
     * Returns whether the engine came up, the same way [PracticeSession.start] does: the
     * calibration screen has to tell the learner that the microphone is busy instead of
     * waiting for strokes that can never arrive (decision 154).
     */
    fun start(scope: CoroutineScope): Boolean {
        if (pollJob != null) {
            return true
        }
        clockDrift.reset()
        restarts = 0
        restartAtNanos = 0L
        val ok = native.start()
        if (!ok) {
            _status.value = _status.value.copy(running = false)
            return false
        }
        pollJob = scope.launch(Dispatchers.Default) {
            while (true) {
                pollOnce()
                delay(POLL_INTERVAL_MS)
            }
        }
        return true
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        native.stop()
        clockDrift.reset()
        restarts = 0
        restartAtNanos = 0L
        pendingTicks.clear()
        recentOffsets.clear()
        _status.value = Status(
            bpm = _status.value.bpm,
            sensitivity = _status.value.sensitivity,
            micThresholdLevel = _status.value.micThresholdLevel,
            inputLatencyMs = _status.value.inputLatencyMs,
            clickAudible = _status.value.clickAudible,
        )
    }

    fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(MIN_BPM, MAX_BPM)
        native.setBpm(clamped)
        _status.value = _status.value.copy(bpm = clamped)
    }

    fun setClickAudible(audible: Boolean) {
        native.setClickAudible(audible)
        _status.value = _status.value.copy(clickAudible = audible)
    }

    fun setSensitivity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        native.setSensitivity(clamped)
        _status.value = _status.value.copy(sensitivity = clamped)
    }

    /**
     * Sets the loudness gate quiet onsets are dropped by (decision 158). Zero disables it,
     * which is what the threshold probe needs while it is listening to the room.
     */
    fun setMicThresholdLevel(level: Float) {
        _status.value = _status.value.copy(micThresholdLevel = MicThreshold.clamp(level))
    }

    fun setInputLatencyMs(value: Float) {
        // Upper bound is the one the settings allow (AppSettings.LATENCY_MAX_MS). It used to
        // be 300, which silently swallowed 5 ms of a 305 ms Bluetooth round trip in the
        // sound check while the level itself applied all of it (decision 188).
        val clamped = value.coerceIn(-100f, 600f)
        native.setInputLatencyMillis(clamped)
        _status.value = _status.value.copy(inputLatencyMs = clamped)
    }

    /**
     * The native stream numbers, as [PracticeSession.streamInfo] reports them for an
     * attempt. The calibration log states the audio path it measured (decision 157).
     */
    fun streamInfo(): NativeMicLab.StreamInfo = native.streamInfo()

    fun resetStats() {
        recentOffsets.clear()
        _status.value = _status.value.copy(hitCount = 0, meanOffsetMs = 0f, stdDevMs = 0f)
    }

    private suspend fun pollOnce() {
        tickScratch.clear()
        hitScratch.clear()
        native.drainTicks(tickScratch)
        native.drainHits(hitScratch)

        for (tick in tickScratch) {
            pendingTicks.addLast(tick)
            _events.emit(MicLabEvent.Tick(tick.index, tick.frame))
        }
        trimPendingTicks()

        val snapshot = native.snapshot()
        if (!snapshot.running) reopenStreams()
        val framesPerMs = snapshot.sampleRate / 1000f
        val clockReading = clockDrift.sample(native.clockProbe(), snapshot.sampleRate)
        val bpm = _status.value.bpm
        val framesPerBeat = if (bpm > 0) snapshot.sampleRate * 60.0 / bpm else 0.0
        val gate = _status.value.micThresholdLevel
        if (framesPerMs > 0f) {
            for (hit in hitScratch) {
                val nearest = nearestTick(hit.frame, framesPerBeat)
                val nearestFrame = nearest?.frame ?: hit.frame
                val nearestIndex = nearest?.index ?: -1L
                val deltaFrames = (hit.frame - nearestFrame).toFloat()
                val offsetMs = deltaFrames / framesPerMs
                val loud = MicThreshold.passes(hit.envelope, gate)
                if (loud) addOffset(offsetMs)
                _events.emit(
                    MicLabEvent.Hit(
                        frame = hit.frame,
                        nearestTickFrame = nearestFrame,
                        nearestTickIndex = nearestIndex,
                        offsetMs = offsetMs,
                        envelope = hit.envelope,
                        threshold = hit.threshold,
                        loud = loud,
                    )
                )
            }
        }

        val mean = if (recentOffsets.isEmpty()) 0f else recentOffsets.average().toFloat()
        val variance = if (recentOffsets.size < 2) {
            0f
        } else {
            val m = mean
            var s = 0.0
            for (v in recentOffsets) {
                val d = v - m
                s += d * d
            }
            (s / (recentOffsets.size - 1)).toFloat()
        }
        val stdDev = kotlin.math.sqrt(variance.toDouble()).toFloat()

        _status.value = _status.value.copy(
            running = snapshot.running,
            restarts = restarts,
            sampleRate = snapshot.sampleRate,
            tickCount = snapshot.tickCount,
            envelope = snapshot.envelope,
            threshold = snapshot.threshold,
            peak = snapshot.peak,
            clickAudible = snapshot.clickAudible,
            hitCount = recentOffsets.size,
            meanOffsetMs = mean,
            stdDevMs = stdDev,
            streamSkewMs = snapshot.streamSkewMs,
            micPathLatencyMs = snapshot.inputLatencyMs,
            outputPathLatencyMs = snapshot.outputLatencyMs,
            clockDrift = clockReading ?: _status.value.clockDrift,
        )
    }

    /**
     * Reopens the streams after the engine stopped on its own. That happens on every audio
     * route change: Oboe reports the disconnect, closes both streams and leaves them closed,
     * which is why the click went silent for good when a Bluetooth pair connected while a
     * screen was open. Reopening is attempted from this polling coroutine and never from the
     * error callback, and it is spaced out because a pair of headphones takes seconds to
     * become usable (decision 190).
     */
    private fun reopenStreams() {
        val now = System.nanoTime()
        if (restarts >= MAX_RESTARTS) return
        if (restartAtNanos != 0L && now - restartAtNanos < RESTART_INTERVAL_MS * 1_000_000L) return
        restartAtNanos = now
        native.stop()
        if (!native.start()) return
        restarts += 1
        clockDrift.reset()
        pendingTicks.clear()
        recentOffsets.clear()
    }

    private fun trimPendingTicks() {
        while (pendingTicks.size > TICK_HISTORY) {
            pendingTicks.removeFirst()
        }
    }

    // Real ticks only become known to Kotlin once the native engine has
    // already fired them (the output callback publishes a tick only after
    // its frame counter passes nextTickFrame_). So a hit that lands before
    // the *upcoming* beat has no real tick to be compared against yet -
    // it can only ever be measured against the previous, already-fired
    // tick, which makes every early hit look "late by almost a full beat"
    // instead of "early". To fix that we also synthesize a projected next
    // tick (lastTick.frame + framesPerBeat, stepped forward if more than
    // one beat has elapsed, e.g. after a pause) and let it compete as a
    // candidate alongside the real, already-seen ticks.
    private fun nearestTick(frame: Long, framesPerBeat: Double): NativeMicLab.TickEvent? {
        if (pendingTicks.isEmpty()) return null
        var best: NativeMicLab.TickEvent = pendingTicks.first()
        var bestDelta = abs(frame - best.frame)
        for (t in pendingTicks) {
            val delta = abs(frame - t.frame)
            if (delta < bestDelta) {
                bestDelta = delta
                best = t
            }
        }
        if (framesPerBeat > 0.0) {
            val last = pendingTicks.last()
            var projectedFrame = last.frame + framesPerBeat
            var projectedIndex = last.index + 1
            var guard = 0
            while (projectedFrame < frame - framesPerBeat / 2.0 && guard < 64) {
                projectedFrame += framesPerBeat
                projectedIndex += 1
                guard++
            }
            val delta = abs(frame - projectedFrame.toLong())
            if (delta < bestDelta) {
                best = NativeMicLab.TickEvent(frame = projectedFrame.toLong(), index = projectedIndex)
            }
        }
        return best
    }

    private fun addOffset(offsetMs: Float) {
        recentOffsets.addLast(offsetMs)
        while (recentOffsets.size > OFFSET_WINDOW) {
            recentOffsets.removeFirst()
        }
    }

    companion object {
        const val MIN_BPM = 40
        const val MAX_BPM = 240
        const val DEFAULT_BPM = 60
        const val DEFAULT_SENSITIVITY = 0.35f
        // Residual output+input round-trip latency after the start-skew fix
        // (decision 52). Measured via self-loopback (speaker click -> mic),
        // which times Lout+Lin -- the same two delays a real stick hit synced
        // to the audible click goes through, so the same offset applies to
        // real playing too. Still device-specific; the calibration-wizard
        // TODO remains the proper per-device fix.
        const val DEFAULT_LATENCY_MS = 24f

        private const val POLL_INTERVAL_MS = 8L
        private const val TICK_HISTORY = 64
        private const val OFFSET_WINDOW = 32

        /** Spacing between attempts to reopen a closed stream, in milliseconds. */
        private const val RESTART_INTERVAL_MS = 700L

        /** Roughly half a minute of attempts, which covers a Bluetooth pair connecting. */
        private const val MAX_RESTARTS = 40

        fun accuracyBucketMs(offsetMs: Float): Int = abs(offsetMs.roundToInt())
    }
}
