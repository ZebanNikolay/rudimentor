package com.rudimentor.app.audio

data class BeatPattern(
    val accents: List<Boolean>,
    val hands: List<Hand>,
) {
    init {
        require(accents.size in MIN_BEATS..MAX_BEATS)
        require(hands.size == accents.size)
    }

    val size: Int = accents.size

    val accentMask: Int = accents.foldIndexed(0) { index, mask, isAccent ->
        if (isAccent) mask or (1 shl index) else mask
    }

    val leftHandMask: Int = hands.foldIndexed(0) { index, mask, hand ->
        if (hand == Hand.Left) mask or (1 shl index) else mask
    }

    fun toggleAccent(index: Int): BeatPattern = copy(
        accents = accents.mapIndexed { beatIndex, isAccent ->
            if (beatIndex == index) !isAccent else isAccent
        },
    )

    fun toggleHand(index: Int): BeatPattern = copy(
        hands = hands.mapIndexed { beatIndex, hand ->
            if (beatIndex == index) hand.other() else hand
        },
    )

    fun addBeat(): BeatPattern = if (size == MAX_BEATS) {
        this
    } else {
        copy(
            accents = accents + false,
            hands = hands + Hand.defaultFor(size),
        )
    }

    fun removeBeat(): BeatPattern = if (size == MIN_BEATS) {
        this
    } else {
        copy(
            accents = accents.dropLast(1),
            hands = hands.dropLast(1),
        )
    }

    fun resized(newSize: Int): BeatPattern {
        val targetSize = newSize.coerceIn(MIN_BEATS, MAX_BEATS)
        return BeatPattern(
            accents = List(targetSize) { index -> accents.getOrElse(index) { false } },
            hands = List(targetSize) { index -> hands.getOrElse(index) { Hand.defaultFor(index) } },
        )
    }

    companion object {
        const val MIN_BEATS = 4
        const val MAX_BEATS = 8

        fun default(): BeatPattern = BeatPattern(
            accents = List(MIN_BEATS) { index -> index == 0 },
            hands = List(MIN_BEATS, Hand::defaultFor),
        )
    }
}

enum class Hand(val label: String) {
    Right("R"),
    Left("L"),
    ;

    fun other(): Hand = if (this == Right) Left else Right

    companion object {
        fun defaultFor(index: Int): Hand = if (index % 2 == 0) Right else Left
    }
}
