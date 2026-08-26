package com.rudimentor.app.telemetry

import com.rudimentor.app.audio.NativeMicLab

/**
 * The native stream numbers as the log records them.
 *
 * Kept here rather than beside a screen: both the attempt log and the calibration log
 * report the same audio path, and one mapping means the two cannot describe it
 * differently (decision 157).
 */
fun NativeMicLab.StreamInfo.toTelemetry(): TelemetryAudio = TelemetryAudio(
    sampleRate = sampleRate,
    outputBurstFrames = outputBurstFrames,
    outputBufferFrames = outputBufferFrames,
    inputBurstFrames = inputBurstFrames,
    inputBufferFrames = inputBufferFrames,
    inputCapacityFrames = inputCapacityFrames,
    outputExclusive = outputExclusive,
    inputExclusive = inputExclusive,
    inputPreset = inputPresetName,
    outputXRuns = outputXRuns,
    inputXRuns = inputXRuns,
    errorCount = errorCount,
    lastErrorCode = lastErrorCode,
)
