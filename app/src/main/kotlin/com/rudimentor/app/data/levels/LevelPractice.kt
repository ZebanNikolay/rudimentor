package com.rudimentor.app.data.levels

import com.rudimentor.app.audio.Beat
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Hand

/**
 * Maps the repeating pattern of a lesson onto a single BeatGrid row.
 *
 * The package describes accents per lesson (`technique.accents`) instead of per step, so
 * every beat starts unaccented until the practice engine moves to the beat-based model.
 */
fun Level.toPracticeGrid(): BeatGrid {
    require(supportsBeatGrid) { "$id uses multi-hand steps that BeatGrid does not support yet" }
    return BeatGrid(
        rows = listOf(
            BeatRow(
                beats = pattern.map { step ->
                    Beat(
                        state = BeatState.Normal,
                        hand = when (step.hands.single()) {
                            PatternHand.Right -> Hand.Right
                            PatternHand.Left -> Hand.Left
                        },
                    )
                },
            ),
        ),
    )
}
