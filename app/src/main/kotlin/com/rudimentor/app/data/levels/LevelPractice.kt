package com.rudimentor.app.data.levels

import com.rudimentor.app.audio.Beat
import com.rudimentor.app.audio.BeatGrid
import com.rudimentor.app.audio.BeatRow
import com.rudimentor.app.audio.BeatState
import com.rudimentor.app.audio.Bpm
import com.rudimentor.app.audio.Hand

/**
 * A level laid out on the metronome: the grid to load and the click rate to run it at.
 *
 * The metronome clicks once per step, so its tempo is strokes per minute, not the beat tempo
 * of the level: a level at 60 bpm with two notes a beat runs here at 120. [capped] is true
 * when that rate is above what the click engine plays and was clamped to [Bpm.MAX].
 */
data class LevelMetronome(
    val grid: BeatGrid,
    val bpm: Int,
    val capped: Boolean,
)

/**
 * The level as a metronome loop (decision 228): its sticking, its density, one steady tempo.
 *
 * Only what the metronome can say is carried over. Ramps and density switches are plans over
 * time and the metronome holds one tempo, so a ramp starts from its first tempo and a switch
 * from its first density -- speeding up is the player's own stepper. A rest is a muted step.
 * With more than one note a beat the first note of every beat is accented, so the pulse is
 * still heard under the strokes.
 *
 * A transition lesson becomes one row per phase, as long as the phase if it fits on a row, so
 * the switch comes where the level puts it. A single pattern gets the shortest row that holds
 * whole patterns and whole beats at once, otherwise the accents would drift across the loop.
 * A row longer than four is then cut into rows of four, see [wordRows].
 *
 * Null for a level with unison steps: a beat on the metronome is struck by one hand.
 */
fun Level.toMetronome(target: RankTarget): LevelMetronome? {
    if (phases.isEmpty() || phases.any { phase -> phase.steps.isEmpty() || phase.steps.any { it.hands.size > 1 } }) {
        return null
    }
    val density = target.hitsPerBeat.coerceAtLeast(1)
    val rows = phases.take(BeatGrid.MAX_ROWS).map { phase ->
        val phaseStrokes = phase.beatCount * density
        val length = when {
            phased && phaseStrokes in 1..BeatRow.MAX_BEATS -> phaseStrokes
            else -> {
                val whole = lcm(phase.steps.size, density)
                if (whole <= BeatRow.MAX_BEATS) whole else phase.steps.size.coerceAtMost(BeatRow.MAX_BEATS)
            }
        }
        BeatRow(
            beats = List(length) { index ->
                val step = phase.steps[index % phase.steps.size]
                Beat(
                    state = when {
                        step.rest -> BeatState.Mute
                        density > 1 && index % density == 0 -> BeatState.Accent
                        else -> BeatState.Normal
                    },
                    hand = if (PatternHand.Left in step.hands) Hand.Left else Hand.Right,
                )
            },
        )
    }
    val strokesPerMinute = target.bpm * density
    return LevelMetronome(
        grid = BeatGrid(wordRows(rows, density)),
        bpm = Bpm.clamp(strokesPerMinute),
        capped = strokesPerMinute > Bpm.MAX,
    )
}

/**
 * A long row cut into rows of four, the words of the practice track (decisions 236, 240):
 * eight strokes on one line came out as two small groups side by side, while as two rows of
 * the drum they are full-size pads and the barrel turns on every word. The loop plays the
 * same -- the metronome runs the rows one after another. Only where four strokes are whole
 * beats (one, two or four a beat) and the drum still holds every row; otherwise the rows stay.
 */
private fun wordRows(rows: List<BeatRow>, density: Int): List<BeatRow> {
    if (WORD % density != 0) return rows
    val split = rows.flatMap { row ->
        if (row.size > WORD && row.size % WORD == 0) {
            row.beats.chunked(WORD).map { BeatRow(beats = it) }
        } else {
            listOf(row)
        }
    }
    return if (split.size <= BeatGrid.MAX_ROWS) split else rows
}

private const val WORD = 4

private fun lcm(a: Int, b: Int): Int {
    tailrec fun gcd(x: Int, y: Int): Int = if (y == 0) x else gcd(y, x % y)
    return a / gcd(a, b) * b
}
