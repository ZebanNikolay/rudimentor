package com.rudimentor.app.ui.levels

import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PracticeRank

/**
 * Level names live in the UI, not in the course data: a family package describes what to play
 * and how fast, so every label here is derived from those fields. Labels take the rank the
 * learner selected on the map, because a level has one target per rank.
 */

/** e.g. `Single Strokes · 90 BPM`. */
internal fun Level.headline(family: Family, rank: PracticeRank): String =
    "${family.name} · ${target(rank).bpm} BPM"

/** The map node caption under the pad, e.g. `90 BPM`. */
internal fun Level.mapCaption(rank: PracticeRank): String = "${target(rank).bpm} BPM"

/** Level detail body copy: the family goal plus the required technique. */
internal fun Level.blurb(family: Family): String = buildString {
    append(family.description)
    append(' ')
    append(technique.strokeStyle.humanized().replaceFirstChar(Char::uppercaseChar))
    append(" strokes, ")
    append(technique.dynamics.humanized())
    append(" dynamics, accents: ")
    append(technique.accents.humanized())
    append('.')
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
