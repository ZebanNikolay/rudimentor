package com.rudimentor.app.data.levels

/**
 * A plain-text dump of everything the app knows about one level.
 *
 * The course package is the source of truth, but the level screen only shows what a
 * learner needs: a headline, a pattern preview, one target tempo. When a level behaves
 * in a way the screen does not explain -- a subdivision switch that changes density
 * mid-attempt, a phased level that swaps sticking -- the fields behind that behaviour
 * are invisible. This dump prints them, and the debug build shows it behind a button on
 * the level screen.
 *
 * Kept free of Android and of Compose on purpose: the format is covered by a unit test,
 * and the same text is what Nikolai copies out of the phone.
 *
 * Strings are hard-coded English, the rule every dev surface follows.
 */

/** Human-readable name of the lesson type, plus what that type actually does. */
private val LevelType.debugName: String
    get() = when (this) {
        LevelType.Steady -> "steady - one density for the whole attempt"
        LevelType.Isolation -> "isolation - one hand carries the figure"
        LevelType.Unison -> "unison - both hands strike together"
        LevelType.Transition -> "transition - sticking changes between blocks"
        LevelType.SubdivisionSwitch -> "subdivision_switch - density changes between blocks"
        LevelType.TempoRamp -> "tempo_ramp - tempo changes between blocks"
        LevelType.Dynamics -> "dynamics - accent pattern is the lesson"
    }

/** `R R L L` -- one letter per step, a rest prints as the pattern's own rest label. */
private fun List<PatternStep>.sticking(): String =
    if (isEmpty()) "(none)" else joinToString(" ") { it.label }

private fun StringBuilder.line(label: String, value: Any?) {
    if (value == null) return
    append(label).append(": ").append(value).append('\n')
}

/**
 * Every field of [level] that the level screen hides, in reading order: what the level is,
 * what it plays, how long it lasts, and what each rank asks for.
 */
fun describeLevel(level: Level, family: Family): String = buildString {
    val lesson = level.lesson

    append("== ").append(level.id).append(" ==\n")
    line("family", "${family.id} (${family.name})")
    line("type", level.type.debugName)
    line("modifiers", level.modifiers.joinToString(", ") { it.storageName }.ifEmpty { "none" })
    line("map", "row ${level.row}, column ${level.column.storageName}")
    line(
        "prerequisites",
        level.prerequisiteIds.sorted().joinToString(", ").ifEmpty { "none - open from the start" },
    )
    line("playable", "${level.playable} (beat grid: ${level.supportsBeatGrid})")
    lesson.intentionalRollback?.let { line("intentional rollback", it) }
    if (lesson.midCycleSwitch) line("mid-cycle switch", "yes - density changes inside a sticking cycle")

    append('\n')
    append("-- pattern --\n")
    line("mode", level.patternMode.storageName)
    line("lead hand", level.leadHand.storageName)
    if (level.phased) {
        line("phases", "${level.phases.size} blocks, chain repeats ${level.phaseRepeats}x per pass")
        level.phases.forEach { phase ->
            line("  block ${phase.index + 1}", "${phase.beatCount} beats  ${phase.steps.sticking()}")
        }
    } else {
        line("sticking", level.pattern.sticking())
    }
    lesson.weakFocus?.let { focus ->
        line(
            "weak focus",
            "${focus.strategy.storageName}, authored ${focus.authoredWeakHand.storageName}" +
                if (focus.adaptToUser) ", adapts to the user's weak hand" else "",
        )
    }

    append('\n')
    append("-- technique --\n")
    line("stroke", level.technique.strokeStyle)
    line("dynamics", level.technique.dynamics)
    line("accents", level.technique.accents)

    append('\n')
    append("-- execution --\n")
    line("beats", lesson.execution.beatCount)
    line("duration", lesson.execution.durationSeconds?.let { "${it}s (timed)" })
    line("completion", lesson.execution.completionMode?.storageName)

    level.rankTargets.forEach { target ->
        append('\n')
        append("-- rank ").append(target.rank.storageName).append(" --\n")
        val densities = (0 until level.beatCount).map(target::hitsPerBeatAtBeat).distinct()
        line("bpm", target.bpm)
        line(
            "hits per beat",
            if (densities.size > 1) densities.joinToString(" / ") + " (per block)" else target.hitsPerBeat,
        )
        line("notes per attempt", level.noteCount(target))
        line("attempt repeats", target.attemptRepeats)
        target.subdivisionPlan?.let { plan ->
            line(
                "subdivision plan",
                "blocks of ${plan.blockBeats} beats at ${plan.hitsPerBeat.joinToString(" -> ")} hits per beat",
            )
        }
        target.tempoRampPlan?.let { plan ->
            line("tempo ramp", "${plan.mode}, ${plan.direction}, repeats ${plan.repeatCount}x")
            plan.phases.forEachIndexed { index, phase ->
                line("  step ${index + 1}", "${phase.bpm} bpm for ${phase.beatCount} beats")
            }
        }
        line("density exception", target.densityException)
        // The click is set once per attempt, so a ramp is still played flat. Without this
        // line the level looks like a steady one on the phone and the gap is invisible.
        target.tempoRampPlan?.let {
            line(
                "ENGINE",
                "plays ${target.bpm} bpm for the whole attempt - the ramp is not followed yet",
            )
        }
    }
}
