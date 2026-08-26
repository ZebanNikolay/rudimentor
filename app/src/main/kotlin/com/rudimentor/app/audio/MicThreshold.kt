package com.rudimentor.app.audio

import kotlin.math.log10
import kotlin.math.pow

/**
 * The loudness gate a detected onset has to pass before anything counts it as a stroke.
 *
 * The onset detector runs its own adaptive threshold, and in a noisy room that threshold
 * sits on its floor: the dev.37 field log shows `thr 0.0110` on almost every event, room
 * noise at an envelope of 0.012-0.020 and real pad strokes at 0.25-1.02. So the detector
 * let 97 noise events through as strokes, they were scored, and the real strokes became
 * "extra" -- 24 % accuracy on an attempt the learner played reasonably (decision 158).
 *
 * The two populations are 25-80x apart in loudness, which is exactly the case an absolute
 * gate solves: any level between them separates them for good. The level is a setting the
 * learner sets on the calibration screen, either by hand on the meter or by letting
 * [ThresholdProbe] measure the room and their own strokes.
 *
 * A level of [MIN_LEVEL] means "no gate", the old behaviour.
 */
object MicThreshold {

    /** No gate at all: every onset the detector reports is a stroke. */
    const val MIN_LEVEL = 0f

    /**
     * Ceiling of the gate. Above this only a very hard stroke would ever pass, so the
     * slider stops here instead of letting the learner mute their own pad.
     */
    const val MAX_LEVEL = 0.5f

    /**
     * Default gate: just above the room noise of the dev.37 log (0.012-0.020) and far
     * below a real stroke (0.25+). Quiet enough to keep a soft stroke on a soft pad.
     */
    const val DEFAULT_LEVEL = 0.02f

    /** How much of the gate the calibration round applies. See [softened]. */
    const val SOFT_FRACTION = 0.5f

    /** Bottom of the meter and of the slider, in dB relative to full scale. */
    const val FLOOR_DB = -60f

    fun clamp(level: Float): Float =
        if (!level.isFinite()) DEFAULT_LEVEL else level.coerceIn(MIN_LEVEL, MAX_LEVEL)

    /** Whether one onset is loud enough to be treated as a stroke. */
    fun passes(envelope: Float, level: Float): Boolean =
        level <= MIN_LEVEL || (envelope.isFinite() && envelope >= level)

    /**
     * The gate as the latency round should apply it: half the level the learner set.
     *
     * The measured gate sits between the room and the median stroke, which is right for
     * scoring an attempt. Inside the calibration round it was too strict: a stroke a little
     * softer than the ones the probe heard was dropped without a word, so the click ran on
     * and the "0 of 32" counter never moved (decision 160). Half the level is still several
     * times above room noise, which is all the round needs it for.
     */
    fun softened(level: Float): Float = clamp(clamp(level) * SOFT_FRACTION)

    /** Level in dB relative to full scale, floored at [FLOOR_DB]. */
    fun decibels(level: Float): Float {
        if (!level.isFinite() || level <= 0f) return FLOOR_DB
        return (20f * log10(level)).coerceIn(FLOOR_DB, 0f)
    }

    /**
     * Position of a level on the meter, 0..1.
     *
     * The meter is logarithmic on purpose. On the linear meter the practice HUD used, the
     * noise of the field log was 0.012 of full width -- less than a pixel of a 74 dp bar --
     * while a stroke filled the whole bar, so the learner could not see the noise the
     * detector was tripping over at all (decision 158).
     */
    fun toFraction(level: Float): Float =
        ((decibels(level) - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)

    /** Inverse of [toFraction]: what the slider position means as a level. */
    fun fromFraction(fraction: Float): Float {
        val safe = fraction.coerceIn(0f, 1f)
        if (safe <= 0f) return MIN_LEVEL
        val db = FLOOR_DB + safe * -FLOOR_DB
        return clamp(10f.pow(db / 20f))
    }
}
