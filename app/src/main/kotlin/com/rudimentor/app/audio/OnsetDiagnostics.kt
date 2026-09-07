package com.rudimentor.app.audio

/**
 * Evidence from one committed native candidate, not a classification of a physical strike.
 * Frame values use the raw input clock; the attempt's compensated timestamp stays separate.
 * See docs/onset-diagnostics.md for windows, sentinels and the frozen detector parameters.
 */
data class OnsetDiagnostics(
    val sequence: Long,
    val sampleRate: Int,
    val peakFrame: Long,
    val armFrame: Long,
    val commitFrame: Long,
    val previousPeakGapFrames: Long,
    val previousPeakEnvelope: Float,
    val preArmEnvelope: Float,
    val armEnvelope: Float,
    val minimumEnvelopeBeforeArm: Float,
    val commitEnvelope: Float,
    val candidateSignalPeak: Float,
    val adaptiveThresholdAtArm: Float,
    val postHitFloorAtArm: Float,
    val effectiveThresholdAtPeak: Float,
    val effectiveThresholdAtCommit: Float,
    val thresholdFactorAtArm: Float,
    val thresholdFloorAtArm: Float,
    val medianWindowAtArm: Int,
    val refractoryFramesAtArm: Int,
) {
    companion object {
        const val VERSION = 1
        const val DETECTOR = "time-domain-v1"
    }
}
