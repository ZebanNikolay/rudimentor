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
        ) : MicLabEvent
    }

    data class Status(
        val running: Boolean = false,
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
        val hitCount: Int = 0,
        val meanOffsetMs: Float = 0f,
        val stdDevMs: Float = 0f,
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

    private val pendingTicks = ArrayDeque<NativeMicLab.TickEvent>()
    private val recentOffsets = LinkedList<Float>()
    private val hitScratch = ArrayList<NativeMicLab.HitEvent>(32)
    private val tickScratch = ArrayList<NativeMicLab.TickEvent>(32)

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
        pendingTicks.clear()
        recentOffsets.clear()
        _status.value = Status(
            bpm = _status.value.bpm,
            sensitivity = _status.value.sensitivity,
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

    fun setInputLatencyMs(value: Float) {
        val clamped = value.coerceIn(-100f, 300f)
        native.setInputLatencyMillis(clamped)
        _status.value = _status.value.copy(inputLatencyMs = clamped)
    }

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
        val framesPerMs = snapshot.sampleRate / 1000f
        val bpm = _status.value.bpm
        val framesPerBeat = if (bpm > 0) snapshot.sampleRate * 60.0 / bpm else 0.0
        if (framesPerMs > 0f) {
            for (hit in hitScratch) {
                val nearest = nearestTick(hit.frame, framesPerBeat)
                val nearestFrame = nearest?.frame ?: hit.frame
                val nearestIndex = nearest?.index ?: -1L
                val deltaFrames = (hit.frame - nearestFrame).toFloat()
                val offsetMs = deltaFrames / framesPerMs
                addOffset(offsetMs)
                _events.emit(
                    MicLabEvent.Hit(
                        frame = hit.frame,
                        nearestTickFrame = nearestFrame,
                        nearestTickIndex = nearestIndex,
                        offsetMs = offsetMs,
                        envelope = hit.envelope,
                        threshold = hit.threshold,
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
            sampleRate = snapshot.sampleRate,
            tickCount = snapshot.tickCount,
            envelope = snapshot.envelope,
            threshold = snapshot.threshold,
            peak = snapshot.peak,
            clickAudible = snapshot.clickAudible,
            hitCount = recentOffsets.size,
            meanOffsetMs = mean,
            stdDevMs = stdDev,
        )
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

        fun accuracyBucketMs(offsetMs: Float): Int = abs(offsetMs.roundToInt())
    }
}
