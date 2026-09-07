package com.rudimentor.app.telemetry

import com.rudimentor.app.audio.OnsetDiagnostics

/** Additive event schema: absent evidence is explicit, never inferred from the poll peak. */
internal fun TelemetryJson.onset(d: OnsetDiagnostics?): TelemetryJson {
    int("onsetDiagnosticsVersion", if (d == null) 0 else OnsetDiagnostics.VERSION)
    if (d == null) return this
    long("onsetSequence", d.sequence)
        .int("onsetSampleRate", d.sampleRate)
        .long("onsetArmFrame", d.armFrame)
        .long("onsetPeakFrame", d.peakFrame)
        .long("onsetCommitFrame", d.commitFrame)
        .long("onsetPreviousPeakGapFrames", d.previousPeakGapFrames.takeIf { it >= 0 })
        .num("onsetPreviousPeakEnv", d.previousPeakEnvelope.takeIf { it >= 0f } ?: Float.NaN, 6)
        .num("onsetPreArmEnv", d.preArmEnvelope, 6)
        .num("onsetArmEnv", d.armEnvelope, 6)
        .num("onsetMinEnvBeforeArm", d.minimumEnvelopeBeforeArm, 6)
        .num("onsetCommitEnv", d.commitEnvelope, 6)
        .num("candidateSignalPeak", d.candidateSignalPeak, 6)
        .num("onsetAdaptiveThresholdAtArm", d.adaptiveThresholdAtArm, 6)
        .num("onsetPostHitFloorAtArm", d.postHitFloorAtArm, 6)
        .num("onsetEffectiveThresholdAtPeak", d.effectiveThresholdAtPeak, 6)
        .num("onsetEffectiveThresholdAtCommit", d.effectiveThresholdAtCommit, 6)
        .num("onsetThresholdFactorAtArm", d.thresholdFactorAtArm, 6)
        .num("onsetThresholdFloorAtArm", d.thresholdFloorAtArm, 6)
        .int("onsetMedianWindowAtArm", d.medianWindowAtArm)
        .int("onsetRefractoryFramesAtArm", d.refractoryFramesAtArm)
    return this
}
