package com.rudimentor.app.ui.levels

import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.LevelModifier
import com.rudimentor.app.data.levels.LevelType
import com.rudimentor.app.data.levels.PracticeRank
import com.rudimentor.app.data.levels.RankTarget
import kotlin.math.roundToInt

/**
 * Level names live in the UI, not in the course data: a family package describes what to play
 * and how fast, so every label here is derived from those fields. Labels take the rank the
 * learner selected on the map, because a level has one target per rank.
 *
 * A level is named after its *track* — the letters of its code — not after its family: naming
 * it after the family made every level of the map read `Single Strokes` (decision 152). The
 * track names follow the family maps in `learning/course/maps`, so the app and the course
 * documents call the same exercise the same thing.
 */

/** `singles.ST-07` -> `ST`: the track a level belongs to. */
internal val Level.trackCode: String get() = displayCode.substringBeforeLast('-')

/** The name of the exercise, e.g. `Single strokes`, `Diddle in middle`, `Endurance`. */
internal fun Level.title(family: Family): String =
    TRACK_TITLES["${family.id}.$trackCode"]
        ?: TRACK_TITLES[trackCode]
        ?: type.displayName

/** The one-line hint on the map: what to play next and how fast, e.g. `Triplets · 60 BPM`. */
internal fun Level.headline(family: Family, rank: PracticeRank): String =
    "${title(family)} · ${target(rank).bpm} BPM"

/** The line under the title: the map the level sits on, plus what it changes, e.g. `Singles · Weak hand`. */
internal fun Level.subtitle(family: Family): String =
    (listOf(family.name) + modifiers.sortedBy { it.ordinal }.map { it.displayName })
        .joinToString(SEPARATOR)

/** The map node caption under the pad, e.g. `90 BPM`. */
internal fun Level.mapCaption(rank: PracticeRank): String = "${target(rank).bpm} BPM"

/**
 * What this level trains, in one sentence. Derived from the kind of lesson and its modifiers:
 * the family description used to be repeated on every level of the family, which said nothing
 * about the level itself.
 */
internal fun Level.purpose(): String {
    val weak = LevelModifier.Weak in modifiers
    val endurance = LevelModifier.Endurance in modifiers
    return when (type) {
        LevelType.Steady -> when {
            weak -> "Lead with the weak hand and keep the figure as even as the strong one."
            endurance -> "Hold the figure for a long attempt without letting it drift."
            else -> "Hold the figure even and relaxed at a fixed tempo."
        }
        LevelType.Isolation -> if (weak) {
            "The weak hand alone, in long blocks: evenness with no help from the other hand."
        } else {
            "One hand at a time, in long blocks: even strokes without alternation."
        }
        LevelType.Unison -> "Both hands on one spot, struck together and landing as one note."
        LevelType.Transition -> "Change sticking mid-attempt without breaking the pulse."
        LevelType.SubdivisionSwitch -> "Change note density mid-attempt while the pulse stays put."
        LevelType.TempoRamp -> "Follow the tempo up and back down without losing the figure."
        LevelType.Dynamics -> "Shape the accents while the unaccented strokes stay even."
    }
}

/** The required execution, as one line: `Full rebound · even · accents: none`. */
internal fun Level.techniqueLine(): String = listOf(
    technique.strokeStyle.humanized().replaceFirstChar(Char::uppercaseChar),
    technique.dynamics.humanized(),
    "accents: ${technique.accents.humanized()}",
).joinToString(SEPARATOR)

/**
 * How long one official attempt takes at [target], in seconds: every beat of the plan lasts as
 * long as the tempo of that beat, so a ramp is not the plain `beats / bpm` a fixed level is.
 * A timed lesson states its own duration.
 */
internal fun Level.attemptSeconds(target: RankTarget): Int {
    durationSeconds?.let { return it }
    val plan = tempoPlan(target)
    if (plan.isEmpty()) return 0
    return plan.sumOf { bpm -> 60.0 / bpm }.roundToInt()
}

/**
 * One block of the sticking map: the pattern of the block and how much of the attempt it is.
 *
 * The map is text only (decision 153): the pad grid above it drew the same information a
 * second time and took a third of the screen. Sizes are for *one pass* through the block
 * chain, and a subdivision plan is read at the beats of that first pass, so a level whose
 * density cycles across passes states the density it starts with.
 */
internal data class StickingBlock(
    val index: Int,
    val beats: Int,
    /** The pattern of the block, grouped for reading: `RLRL RLRL`. */
    val sticking: String,
    val notes: Int,
    /** How many times the pattern runs inside the block; null when it does not divide evenly. */
    val cycles: Int?,
    /** Notes per beat; null when the density changes inside the block. */
    val hitsPerBeat: Int?,
)

/** Every block one pass of an attempt plays, in order. A one-pattern level has one block. */
internal fun Level.stickingBlocks(target: RankTarget): List<StickingBlock> {
    var beat = 0
    val blocks = mutableListOf<StickingBlock>()
    phases.forEach { phase ->
        if (phase.steps.isEmpty()) return@forEach
        var notes = 0
        val densities = mutableSetOf<Int>()
        repeat(phase.beatCount) {
            val hits = target.hitsPerBeatAtBeat(beat)
            densities += hits
            notes += hits
            beat += 1
        }
        blocks += StickingBlock(
            index = phase.index,
            beats = phase.beatCount,
            sticking = phase.steps
                .chunked(STICKING_GROUP)
                .joinToString(" ") { group -> group.joinToString("") { it.label } },
            notes = notes,
            cycles = notes.takeIf { it > 0 && it % phase.steps.size == 0 }?.div(phase.steps.size),
            hitsPerBeat = densities.singleOrNull(),
        )
    }
    return blocks
}

/** How many times one attempt plays the whole block chain: phase passes times ramp passes. */
internal fun Level.attemptPasses(target: RankTarget): Int = phaseRepeats * target.attemptRepeats

/** `43 s` under a minute, `2:07` above it. */
internal fun formatSeconds(seconds: Int): String = if (seconds < 60) {
    "$seconds s"
} else {
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

internal val PracticeRank.displayName: String
    get() = when (this) {
        PracticeRank.Practice -> "Practice"
        PracticeRank.Groove -> "Groove"
        PracticeRank.Stage -> "Stage"
    }

/** The approved rank model: the same map is walked at one, two and four hits per beat. */
internal val PracticeRank.hitsPerBeat: Int
    get() = when (this) {
        PracticeRank.Practice -> 1
        PracticeRank.Groove -> 2
        PracticeRank.Stage -> 4
    }

private fun String.humanized(): String = replace('_', ' ')

private const val SEPARATOR = " · "

/** Sticking is read in fours: `RLRL RLRL` instead of `RLRLRLRL`. */
private const val STICKING_GROUP = 4

/**
 * Track code -> exercise name. A key may be prefixed with the family id when the same code
 * means a different figure per family: `ST` is the base sticking of its family.
 */
private val TRACK_TITLES: Map<String, String> = mapOf(
    // Base sticking of each family.
    "singles.ST" to "Single strokes",
    "doubles.ST" to "Double strokes",
    // Paradiddle family figures and the inversions of the single paradiddle.
    "SP" to "Single paradiddle",
    "DP" to "Double paradiddle",
    "TP" to "Triple paradiddle",
    "PDD" to "Paradiddle-diddle",
    "PIM" to "Diddle in middle",
    "PIS" to "Diddle at start",
    "DI" to "Inverted doubles",
    // Cross-family tracks.
    "IS" to "Isolation",
    "IW" to "Weak-hand isolation",
    "UN" to "Unison",
    "WK" to "Weak-hand lead",
    "SS" to "Subdivision switch",
    "RM" to "Tempo ramp",
    "EN" to "Endurance",
    "TS" to "Triplets",
    "TR" to "Sticking transition",
)
