package com.rudimentor.app.ui.metronome

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Tuning constants for the beat drum. Kept in one place so the drum's
 * feel can be tweaked without hunting through composables.
 */
internal object DrumTuning {
    /** Easing curve used when the barrel spins from one row to the next. */
    val Easing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)

    /** How many focus slots fit into the barrel viewport at once. */
    const val VISIBLE_SLOTS = 3

    /**
     * Where the vertical fade begins/ends, as a fraction of the barrel height.
     * The fade only has to swallow rows leaving the barrel, so it starts past
     * the neighbours.
     */
    const val FADE_STOP = 0.15f

    /**
     * Row spacing as a fraction of the focus slot height. The neighbours must
     * clear the guide lines of the focus slot (±0.5 slot) yet stay out of the
     * edge fade, so the pitch sits between the two.
     */
    const val ROW_PITCH = 0.82f
}
