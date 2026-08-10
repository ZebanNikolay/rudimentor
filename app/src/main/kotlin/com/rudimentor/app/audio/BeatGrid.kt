package com.rudimentor.app.audio

/** Which hand strikes the pad. Right is a square pad, left is a circle. */
enum class Hand(val label: String) {
    Right("R"),
    Left("L"),
    ;

    fun other(): Hand = if (this == Right) Left else Right

    companion object {
        /** Default sticking of the grid: R L R L R R L L, repeated. */
        private val DEFAULT_STICKING = listOf(
            Right, Left, Right, Left, Right, Right, Left, Left,
        )

        fun defaultFor(index: Int): Hand =
            DEFAULT_STICKING[index.mod(DEFAULT_STICKING.size)]

        fun fromLabel(label: Char): Hand = if (label == 'L') Left else Right
    }
}

/** A beat is either played, accented or silent; a short tap cycles through these. */
enum class BeatState {
    Normal,
    Accent,
    Mute,
    ;

    fun next(): BeatState = when (this) {
        Normal -> Accent
        Accent -> Mute
        Mute -> Normal
    }

    companion object {
        fun fromCode(code: Char): BeatState = when (code) {
            '1' -> Accent
            '2' -> Mute
            else -> Normal
        }
    }

    val code: Char
        get() = when (this) {
            Normal -> '0'
            Accent -> '1'
            Mute -> '2'
        }
}

data class Beat(
    val state: BeatState = BeatState.Normal,
    val hand: Hand = Hand.Right,
)

/** One line of the drum: between 1 and 16 beats. */
data class BeatRow(
    val beats: List<Beat>,
) {
    init {
        require(beats.size in MIN_BEATS..MAX_BEATS) {
            "A row holds $MIN_BEATS..$MAX_BEATS beats, got ${beats.size}"
        }
    }

    val size: Int get() = beats.size

    fun resized(newSize: Int): BeatRow {
        val target = newSize.coerceIn(MIN_BEATS, MAX_BEATS)
        if (target == size) return this
        return BeatRow(
            List(target) { index ->
                beats.getOrElse(index) { Beat(hand = Hand.defaultFor(index)) }
            },
        )
    }

    fun mapBeat(index: Int, transform: (Beat) -> Beat): BeatRow =
        if (index !in beats.indices) {
            this
        } else {
            BeatRow(beats.mapIndexed { i, beat -> if (i == index) transform(beat) else beat })
        }

    companion object {
        const val MIN_BEATS = 1
        const val MAX_BEATS = 16

        fun default(size: Int): BeatRow = BeatRow(
            List(size.coerceIn(MIN_BEATS, MAX_BEATS)) { index ->
                Beat(
                    state = if (index == 0) BeatState.Accent else BeatState.Normal,
                    hand = Hand.defaultFor(index),
                )
            },
        )
    }
}

/**
 * The looped drum: 1..8 rows, each with its own length. Playback walks the rows
 * top to bottom and starts over, so the grid flattens into one step sequence.
 */
data class BeatGrid(
    val rows: List<BeatRow>,
) {
    init {
        require(rows.size in MIN_ROWS..MAX_ROWS) {
            "The drum holds $MIN_ROWS..$MAX_ROWS rows, got ${rows.size}"
        }
    }

    val rowCount: Int get() = rows.size

    val totalSteps: Int get() = rows.sumOf { it.size }

    fun row(index: Int): BeatRow = rows[index.mod(rowCount)]

    fun withRowCount(count: Int): BeatGrid {
        val target = count.coerceIn(MIN_ROWS, MAX_ROWS)
        if (target == rowCount) return this
        return BeatGrid(
            List(target) { index ->
                rows.getOrElse(index) { BeatRow.default(DEFAULT_ROW_LENGTHS[index.mod(DEFAULT_ROW_LENGTHS.size)]) }
            },
        )
    }

    fun withRowLength(rowIndex: Int, length: Int): BeatGrid = mapRow(rowIndex) {
        it.resized(length)
    }

    fun cycleState(rowIndex: Int, beatIndex: Int): BeatGrid = mapRow(rowIndex) { row ->
        row.mapBeat(beatIndex) { it.copy(state = it.state.next()) }
    }

    fun toggleHand(rowIndex: Int, beatIndex: Int): BeatGrid = mapRow(rowIndex) { row ->
        row.mapBeat(beatIndex) { it.copy(hand = it.hand.other()) }
    }

    private fun mapRow(rowIndex: Int, transform: (BeatRow) -> BeatRow): BeatGrid =
        if (rowIndex !in rows.indices) {
            this
        } else {
            copy(rows = rows.mapIndexed { index, row -> if (index == rowIndex) transform(row) else row })
        }

    /**
     * Flattens the grid into steps for the audio engine: `state or (hand shl 2)`.
     * The engine only needs to know what each step sounds like, not where it sits.
     */
    fun toSequence(): IntArray {
        val sequence = IntArray(totalSteps)
        var step = 0
        rows.forEach { row ->
            row.beats.forEach { beat ->
                sequence[step++] = beat.state.ordinal or (beat.hand.ordinal shl 2)
            }
        }
        return sequence
    }

    /** Maps a global step index back to a (row, beat) position. */
    fun locate(step: Int): Position? {
        if (totalSteps == 0) return null
        var remaining = step.mod(totalSteps)
        rows.forEachIndexed { rowIndex, row ->
            if (remaining < row.size) return Position(rowIndex, remaining)
            remaining -= row.size
        }
        return null
    }

    data class Position(val row: Int, val beat: Int)

    companion object {
        const val MIN_ROWS = 1
        const val MAX_ROWS = 8

        /** Uneven default lengths make the looping drum immediately legible. */
        val DEFAULT_ROW_LENGTHS = listOf(4, 6, 4, 3)

        fun default(): BeatGrid = BeatGrid(DEFAULT_ROW_LENGTHS.map(BeatRow::default))
    }
}
