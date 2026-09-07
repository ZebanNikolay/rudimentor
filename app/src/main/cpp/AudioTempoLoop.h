#pragma once

#include <cstdint>

/** Pure tempo math, mirrored by audio/AudioTempoLoop.kt. */
namespace AudioTempoLoop {
constexpr int kMaxPlanBeats = 512;

inline int normalizedLoopStart(int loopStart, int planSize) {
    return loopStart >= 0 && loopStart < planSize ? loopStart : 0;
}

/**
 * Fractional frames before a beat: one prefix, then a repeating suffix.
 * cumulativeFrames has planSize + 1 entries, starting with zero. Computing from
 * absolute beat indices avoids fractional rounding drift across long sessions;
 * this is O(1) in the audio callback, with no allocation or loop over past beats.
 */
inline double framesBeforeBeat(int64_t beat, const double *cumulativeFrames,
                              int planSize, int loopStart) {
    if (beat <= 0 || cumulativeFrames == nullptr || planSize <= 0 ||
        planSize > kMaxPlanBeats) {
        return 0.0;
    }
    const int start = normalizedLoopStart(loopStart, planSize);
    if (beat < start) {
        return cumulativeFrames[static_cast<int>(beat)];
    }
    const int suffixSize = planSize - start;
    const int64_t suffixBeats = beat - start;
    const int64_t cycles = suffixBeats / suffixSize;
    const int remainder = static_cast<int>(suffixBeats % suffixSize);
    // Separate expressions also keep the operations aligned with JVM Double math
    // (no fused multiply-add at an integer-frame rounding boundary).
    const double cycleFrames =
            static_cast<double>(cycles) * (cumulativeFrames[planSize] - cumulativeFrames[start]);
    const double beforeRemainder = cumulativeFrames[start] + cycleFrames;
    return beforeRemainder + (cumulativeFrames[start + remainder] - cumulativeFrames[start]);
}
}  // namespace AudioTempoLoop
