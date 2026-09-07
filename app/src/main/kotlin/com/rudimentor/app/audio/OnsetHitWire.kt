package com.rudimentor.app.audio

/** Long-array ABI shared with OnsetWire.h; pure so it can be tested without loading JNI. */
internal object OnsetHitWire {
    const val STRIDE = 24

    fun decode(data: LongArray, offset: Int, stride: Int = STRIDE): NativeMicLab.HitEvent {
        require(stride == 3 || stride == STRIDE)
        require(offset >= 0 && offset <= data.size - stride)
        fun level(slot: Int): Float = data[offset + slot] / 1_000_000f
        val diagnostics = if (stride == STRIDE && data[offset + 3] == OnsetDiagnostics.VERSION.toLong()) {
            OnsetDiagnostics(
                sequence = data[offset + 4],
                sampleRate = data[offset + 5].toInt(),
                peakFrame = data[offset + 6],
                armFrame = data[offset + 7],
                commitFrame = data[offset + 8],
                previousPeakGapFrames = data[offset + 9],
                previousPeakEnvelope = level(10),
                preArmEnvelope = level(11),
                armEnvelope = level(12),
                minimumEnvelopeBeforeArm = level(13),
                commitEnvelope = level(14),
                candidateSignalPeak = level(15),
                adaptiveThresholdAtArm = level(16),
                postHitFloorAtArm = level(17),
                effectiveThresholdAtPeak = level(18),
                effectiveThresholdAtCommit = level(19),
                thresholdFactorAtArm = level(20),
                thresholdFloorAtArm = level(21),
                medianWindowAtArm = data[offset + 22].toInt(),
                refractoryFramesAtArm = data[offset + 23].toInt(),
            )
        } else {
            null
        }
        return NativeMicLab.HitEvent(
            frame = data[offset],
            envelope = level(1),
            threshold = level(2),
            diagnostics = diagnostics,
        )
    }
}
