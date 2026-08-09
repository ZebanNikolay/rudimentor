package com.rudimentor.app.audio

object Bpm {
    const val MIN = 30
    const val MAX = 240
    const val DEFAULT = 120

    fun clamp(value: Int): Int = value.coerceIn(MIN, MAX)

    fun adjust(current: Int, delta: Int): Int = clamp(current + delta)
}
