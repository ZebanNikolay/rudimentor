#pragma once

#include "OnsetDetector.h"

// Long-array ABI shared with OnsetHitWire.kt. Packing runs on the polling
// thread, not the audio callback. The original frame/env/thr slots stay first.
namespace OnsetWire {
constexpr int kStride = 24;

template<typename Long>
void pack(Long *out, int64_t correctedFrame, float envelope, float threshold,
          const OnsetDetector::Diagnostics &d) {
    const auto fixed = [](float value) { return static_cast<Long>(value * 1.0e6f); };
    out[0] = correctedFrame;
    out[1] = fixed(envelope);
    out[2] = fixed(threshold);
    out[3] = OnsetDetector::Diagnostics::kVersion;
    out[4] = d.sequence;
    out[5] = d.sampleRate;
    out[6] = d.peakFrame;
    out[7] = d.armFrame;
    out[8] = d.commitFrame;
    out[9] = d.previousPeakGapFrames;
    out[10] = fixed(d.previousPeakEnvelope);
    out[11] = fixed(d.preArmEnvelope);
    out[12] = fixed(d.armEnvelope);
    out[13] = fixed(d.minimumEnvelopeBeforeArm);
    out[14] = fixed(d.commitEnvelope);
    out[15] = fixed(d.candidateSignalPeak);
    out[16] = fixed(d.adaptiveThresholdAtArm);
    out[17] = fixed(d.postHitFloorAtArm);
    out[18] = fixed(d.effectiveThresholdAtPeak);
    out[19] = fixed(d.effectiveThresholdAtCommit);
    out[20] = fixed(d.thresholdFactorAtArm);
    out[21] = fixed(d.thresholdFloorAtArm);
    out[22] = d.medianWindowAtArm;
    out[23] = d.refractoryFramesAtArm;
}
}
