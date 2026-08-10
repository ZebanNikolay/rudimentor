package com.rudimentor.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Metronome(
    private val scope: CoroutineScope,
) {
    private val mutableTicks = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    private var tickObserver: Job? = null

    val ticks: SharedFlow<Long> = mutableTicks

    fun start(): Boolean {
        if (!NativeMetronome.start()) return false

        tickObserver?.cancel()
        tickObserver = scope.launch {
            var deliveredTick = 0L
            while (isActive) {
                val latestTick = NativeMetronome.tickCount()
                while (deliveredTick < latestTick) {
                    deliveredTick += 1
                    mutableTicks.emit(deliveredTick)
                }
                delay(TICK_OBSERVER_INTERVAL_MS)
            }
        }
        return true
    }

    fun stop() {
        tickObserver?.cancel()
        tickObserver = null
        NativeMetronome.stop()
    }

    fun setBpm(bpm: Int) {
        NativeMetronome.setBpm(bpm)
    }

    fun setGrid(grid: BeatGrid) {
        NativeMetronome.setSequence(grid.toSequence())
    }

    companion object {
        private const val TICK_OBSERVER_INTERVAL_MS = 8L
    }
}
