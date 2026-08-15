package com.rudimentor.app.ui.levels

import com.rudimentor.app.data.levels.Family
import com.rudimentor.app.data.levels.Level
import com.rudimentor.app.data.levels.PracticeRank

/**
 * Level names live in the UI, not in the course data: a family package describes what to play
 * and how fast, so every label here is derived from those fields.
 */

/** e.g. `Single Strokes · 90 BPM`. */
internal fun Level.headline(family: Family): String =
    "${family.name} · ${target(PracticeRank.Practice).bpm} BPM"

/** The map node caption under the pad, e.g. `90 BPM`. */
internal fun Level.mapCaption(): String = "${target(PracticeRank.Practice).bpm} BPM"

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

private fun String.humanized(): String = replace('_', ' ')
