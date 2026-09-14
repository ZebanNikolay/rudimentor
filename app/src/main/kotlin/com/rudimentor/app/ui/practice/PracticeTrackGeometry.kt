package com.rudimentor.app.ui.practice

internal const val DEFAULT_TRACK_VISIBLE_MS = 1400f

/** Use the whole attempt, not the moving viewport, so density changes cannot pump the zoom. */
internal fun minimumPracticeNoteIntervalMs(notes: List<PracticeNote>): Double {
    var minimum = Double.POSITIVE_INFINITY
    for (index in 1 until notes.size) {
        val interval = notes[index].timeMs.toDouble() - notes[index - 1].timeMs.toDouble()
        if (interval > 0.0) minimum = minOf(minimum, interval)
    }
    return minimum
}

internal fun practiceTrackPixelsPerMs(
    widthPx: Float,
    noteSidePx: Float,
    noteGapPx: Float,
    minimumIntervalMs: Double,
): Float {
    val defaultScale = widthPx / DEFAULT_TRACK_VISIBLE_MS
    if (!minimumIntervalMs.isFinite() || minimumIntervalMs <= 0.0) return defaultScale
    return maxOf(defaultScale, ((noteSidePx + noteGapPx) / minimumIntervalMs).toFloat())
}
