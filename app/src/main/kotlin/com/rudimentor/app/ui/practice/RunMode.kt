package com.rudimentor.app.ui.practice

enum class RunMode { Challenge, Practice }

data class PracticeSummary(val durationMs: Long, val hits: Long)

internal fun formatPracticeDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000L
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    val minutes = (totalSeconds / 60 % 60).toString().padStart(2, '0')
    return if (totalSeconds >= 3_600L) {
        "${totalSeconds / 3_600L}:$minutes:$seconds"
    } else {
        "$minutes:$seconds"
    }
}
