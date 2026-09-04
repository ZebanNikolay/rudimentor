package com.rudimentor.app.audio

internal object NativeMetronome {
    init {
        System.loadLibrary("rudimentor_audio")
    }

    fun start(): Boolean = nativeStart()

    fun stop() = nativeStop()

    /** False after the engine closed its stream on an error, even if [stop] was never called. */
    fun isRunning(): Boolean = nativeIsRunning()

    fun setBpm(bpm: Int) = nativeSetBpm(Bpm.clamp(bpm))

    /** The engine plays a flat step sequence; rows only exist in the UI. */
    fun setSequence(sequence: IntArray) = nativeSetSequence(sequence)

    fun tickCount(): Long = nativeGetTickCount()

    private external fun nativeStart(): Boolean

    private external fun nativeStop()
    private external fun nativeIsRunning(): Boolean

    private external fun nativeSetBpm(bpm: Int)

    private external fun nativeSetSequence(sequence: IntArray)

    private external fun nativeGetTickCount(): Long
}
