package com.rudimentor.app.audio

object Bpm {
    const val MIN = 40
    // The same ceiling as the native engines (kMaxBpm): a higher value here was clamped
    // out of sight on start, so the display and the click disagreed above 240.
    const val MAX = 240
    const val DEFAULT = 100

    /** The ± buttons move in tens; a practice pad does not need finer steps. */
    const val STEP = 10

    fun clamp(value: Int): Int = value.coerceIn(MIN, MAX)

    fun adjust(current: Int, delta: Int): Int = clamp(current + delta)

    fun canDecrease(current: Int): Boolean = current > MIN

    fun canIncrease(current: Int): Boolean = current < MAX
}
