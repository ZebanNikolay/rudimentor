package com.rudimentor.app.audio

data class BeatPattern(
    val accents: List<Boolean>,
) {
    init {
        require(accents.size in MIN_BEATS..MAX_BEATS)
    }

    val size: Int = accents.size

    val accentMask: Int = accents.foldIndexed(0) { index, mask, isAccent ->
        if (isAccent) mask or (1 shl index) else mask
    }

    fun toggleAccent(index: Int): BeatPattern = copy(
        accents = accents.mapIndexed { beatIndex, isAccent ->
            if (beatIndex == index) !isAccent else isAccent
        },
    )

    fun addBeat(): BeatPattern = if (size == MAX_BEATS) {
        this
    } else {
        copy(accents = accents + false)
    }

    fun removeBeat(): BeatPattern = if (size == MIN_BEATS) {
        this
    } else {
        copy(accents = accents.dropLast(1))
    }

    companion object {
        const val MIN_BEATS = 4
        const val MAX_BEATS = 8

        fun default(): BeatPattern = BeatPattern(
            accents = List(MIN_BEATS) { index -> index == 0 },
        )
    }
}
