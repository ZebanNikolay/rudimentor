package com.rudimentor.app.ui.util

/**
 * Format an elapsed duration as `MM:SS`.
 *
 * The value is clamped to zero so that a stray negative delta from a clock
 * source can never render as `-01:59` in the toolbar.
 */
fun formatElapsed(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return "%02d:%02d".format(minutes, seconds)
}
