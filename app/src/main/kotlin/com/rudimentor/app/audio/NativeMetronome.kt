package com.rudimentor.app.audio

internal object NativeMetronome {
    init {
        System.loadLibrary("rudimentor_audio")
    }

    fun start(): Boolean = nativeStart()

    fun stop() = nativeStop()

    fun setBpm(bpm: Int) = nativeSetBpm(Bpm.clamp(bpm))

    /** The engine plays a flat step sequence; rows only exist in the UI. */
    fun setSequence(sequence: IntArray) = nativeSetSequence(sequence)

    fun tickCount(): Long = nativeGetTickCount()

    private external fun nativeStart(): Boolean

    private external fun nativeStop()

    private external fun nativeSetBpm(bpm: Int)

    private external fun nativeSetSequence(sequence: IntArray)

    private external fun nativeGetTickCount(): Long
}
