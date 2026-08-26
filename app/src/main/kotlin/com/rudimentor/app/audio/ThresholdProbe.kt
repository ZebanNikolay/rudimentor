package com.rudimentor.app.audio

/**
 * Measures the gate level of [MicThreshold] instead of asking the learner to guess it:
 * a couple of seconds of silence tell it how loud the room is, a handful of strokes tell
 * it how loud the pad is, and the level goes between the two (decision 158).
 *
 * Free of Android and of the audio layer, so the whole rule is unit-testable.
 */
class ThresholdProbe(
    private val strokesNeeded: Int = STROKES_NEEDED,
) {

    /** Where the measurement is. The screen drives the transitions. */
    enum class Stage { Noise, Strokes, Done }

    /** What the probe measured, once it has both halves. */
    data class Result(
        /** Loudest thing heard while the learner was quiet. */
        val noiseLevel: Float,
        /** Typical loudness of a stroke: the median of the strokes played. */
        val strokeLevel: Float,
        /** The gate the settings should use. */
        val thresholdLevel: Float,
        /**
         * False when the strokes were not clearly louder than the room -- a phone lying
         * far from the pad, or a genuinely loud room. The screen says so instead of
         * quietly writing a level that gates real strokes away.
         */
        val separated: Boolean,
    )

    var stage: Stage = Stage.Noise
        private set

    private var noiseLevel = 0f
    private val strokes = ArrayList<Float>(STROKES_NEEDED)

    /** Feeds one envelope reading taken while the learner is asked to stay quiet. */
    fun addNoise(envelope: Float) {
        if (stage != Stage.Noise) return
        if (!envelope.isFinite()) return
        if (envelope > noiseLevel) noiseLevel = envelope
    }

    /** Ends the silent half. From here on the readings are strokes. */
    fun startStrokes() {
        if (stage == Stage.Noise) stage = Stage.Strokes
    }

    /**
     * Feeds one detected onset played as a stroke. Returns whether it was counted:
     * anything not clearly above the measured room noise is ignored, so the noise that
     * trips the detector cannot end up defining the stroke level it is meant to gate.
     */
    fun addStroke(envelope: Float): Boolean {
        if (stage != Stage.Strokes) return false
        if (!envelope.isFinite() || envelope <= 0f) return false
        if (envelope < noiseLevel * NOISE_SEPARATION) return false
        strokes.add(envelope)
        if (strokes.size >= strokesNeeded) stage = Stage.Done
        return true
    }

    /** Strokes counted so far, for the counter on the screen. */
    val strokeCount: Int get() = strokes.size

    /** Loudest thing heard during the silent half, for the meter's noise mark. */
    val measuredNoise: Float get() = noiseLevel

    /** The measurement, or null until enough strokes have been played. */
    fun result(): Result? {
        if (strokes.size < strokesNeeded) return null
        val strokeLevel = median(strokes)
        val separated = strokeLevel >= noiseLevel * NOISE_SEPARATION
        // Above the room by a healthy margin, and still far under a real stroke. The
        // stroke-relative floor is what keeps the gate usable in a silent room, where
        // three times "almost nothing" is still almost nothing.
        val fromNoise = noiseLevel * NOISE_MARGIN
        val fromStroke = strokeLevel * STROKE_FRACTION
        val ceiling = strokeLevel * STROKE_CEILING
        val level = maxOf(fromNoise, fromStroke).coerceAtMost(ceiling)
        return Result(
            noiseLevel = noiseLevel,
            strokeLevel = strokeLevel,
            thresholdLevel = MicThreshold.clamp(level),
            separated = separated,
        )
    }

    fun reset() {
        stage = Stage.Noise
        noiseLevel = 0f
        strokes.clear()
    }

    companion object {
        /** Strokes the second half asks for. Enough for a median, short enough to do twice. */
        const val STROKES_NEEDED = 8

        /** How long the silent half listens to the room. */
        const val NOISE_WINDOW_MS = 2_000L

        /** How much louder than the room a reading has to be to count as a stroke. */
        const val NOISE_SEPARATION = 4f

        /** How far above the measured room noise the gate is placed. */
        const val NOISE_MARGIN = 3f

        /** Floor of the gate as a fraction of a stroke, for a quiet room. */
        const val STROKE_FRACTION = 0.15f

        /** Ceiling of the gate as a fraction of a stroke, so soft strokes still pass. */
        const val STROKE_CEILING = 0.5f

        private fun median(values: List<Float>): Float {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[middle]
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2f
            }
        }
    }
}
