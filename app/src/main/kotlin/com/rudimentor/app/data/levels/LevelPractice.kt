package com.rudimentor.app.data.levels

import com.rudimentor.app.audio.Beat
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Hand

fun Level.toPracticeGrid(): BeatGrid {
    require(supportsBeatGrid) { "$id uses multi-hand steps that BeatGrid does not support yet" }
    return BeatGrid(
        rows = listOf(
            BeatRow(
                beats = pattern.map { step ->
                    Beat(
                        state = if (step.accent) BeatState.Accent else BeatState.Normal,
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
