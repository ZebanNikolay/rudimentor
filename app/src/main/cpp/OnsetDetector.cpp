#include "OnsetDetector.h"

#include <algorithm>
#include <cmath>
#include <cstring>

void OnsetDetector::reset(int32_t /*sampleRate*/) {
    hpPrevIn_ = 0.0f;
    hpPrevOut_ = 0.0f;
    envelope_ = 0.0f;
    refractory_ = 0;
    prevEnvelope_ = 0.0f;
    peakEnvelope_ = 0.0f;
    peakThreshold_ = 0.0f;
    peakFrame_ = 0;
    rising_ = false;
    settled_ = true;
    settleThreshold_ = 0.0f;
    medianHead_ = 0;
    medianSize_ = 0;
    std::memset(medianRing_, 0, sizeof(medianRing_));
    lastEnvelope_.store(0.0f, std::memory_order_release);
    lastThreshold_.store(0.0f, std::memory_order_release);
    lastPeak_.store(0.0f, std::memory_order_release);
}

void OnsetDetector::setParams(const Params &params) {
    params_ = params;
    params_.medianWindow = std::clamp(params_.medianWindow, 32, kMedianCapacity);
}

void OnsetDetector::pushMedianSample(float value) {
    medianRing_[medianHead_] = value;
    medianHead_ = (medianHead_ + 1) % params_.medianWindow;
    if (medianSize_ < params_.medianWindow) {
        medianSize_++;
    }
}

float OnsetDetector::currentMedian() const {
    // Copy + nth_element is O(n log n) worst case but n is <= 2048 and this
    // runs once per audio buffer, not once per sample. Good enough; we can
    // switch to an order-statistic tree later if profiling flags it.
    if (medianSize_ == 0) {
        return 0.0f;
    }
    float scratch[kMedianCapacity];
    std::memcpy(scratch, medianRing_, sizeof(float) * medianSize_);
    const int mid = medianSize_ / 2;
    std::nth_element(scratch, scratch + mid, scratch + medianSize_);
    return scratch[mid];
}

int OnsetDetector::process(const float *samples, int32_t numFrames, int64_t startFrame,
                           OnsetDetector::Onset *out, int outCapacity) {
    if (samples == nullptr || numFrames <= 0) {
        return 0;
    }

    int emitted = 0;

    // Update the adaptive threshold once per buffer, using the median of the
    // most recent |x| samples we saw. Cheaper than per-sample sort, and the
    // buffer is short (typically 96..960 frames) so the threshold reacts fast
    // enough.
    const float median = currentMedian();
    const float threshold = std::max(params_.thresholdFloor,
                                     median * params_.thresholdFactor);

    float lastEnvSnapshot = envelope_;
    float peakInBuffer = 0.0f;

    for (int32_t i = 0; i < numFrames; ++i) {
        const float x = samples[i];

        // One-pole highpass: y = a * (y_prev + x - x_prev).
        const float hp = params_.highpassCoeff * (hpPrevOut_ + x - hpPrevIn_);
        hpPrevIn_ = x;
        hpPrevOut_ = hp;

        const float rectified = std::fabs(hp);
        peakInBuffer = std::max(peakInBuffer, rectified);
        pushMedianSample(rectified);

        // Asymmetric envelope follower.
        if (rectified > envelope_) {
            envelope_ += params_.attackCoeff * (rectified - envelope_);
        } else {
            envelope_ += params_.releaseCoeff * (rectified - envelope_);
        }

        const int64_t frame = startFrame + i;

        if (refractory_ > 0) {
            --refractory_;
            prevEnvelope_ = envelope_;
            continue;
        }

        // The adaptive threshold (median of the last ~10 ms) collapses back
        // toward the noise floor much faster than the envelope decays after
        // a hit (release is deliberately slow so transients stay visible).
        // If we let a new rising edge arm as soon as refractory_ hits 0, any
        // wiggle in that still-elevated decay tail re-triggers immediately,
        // turning one physical strike into a burst of onsets. So: after
        // refractory_ elapses, require the envelope to actually settle back
        // under `settleFactor * peakEnvelope` before we start watching for a
        // new rise at all.
        if (!settled_) {
            if (envelope_ <= settleThreshold_) {
                settled_ = true;
            } else {
                prevEnvelope_ = envelope_;
                continue;
            }
        }

        // Peak picking: detect a local maximum of the envelope that also
        // crossed the adaptive threshold. We watch for a rising edge over
        // `threshold`, track the local max, and commit when the envelope
        // turns back down.
        if (!rising_) {
            if (envelope_ > threshold && envelope_ > prevEnvelope_) {
                rising_ = true;
                peakEnvelope_ = envelope_;
                peakThreshold_ = threshold;
                peakFrame_ = frame;
            }
        } else {
            if (envelope_ >= peakEnvelope_) {
                peakEnvelope_ = envelope_;
                peakFrame_ = frame;
            } else if (envelope_ < peakEnvelope_ * 0.85f) {
                // Envelope decayed enough to call the peak done.
                if (emitted < outCapacity) {
                    out[emitted++] = Onset{peakFrame_, peakEnvelope_, peakThreshold_};
                }
                rising_ = false;
                refractory_ = params_.refractoryFrames;
                settled_ = false;
                settleThreshold_ = peakEnvelope_ * params_.settleFactor;
            }
        }

        prevEnvelope_ = envelope_;
        lastEnvSnapshot = envelope_;
    }

    lastEnvelope_.store(lastEnvSnapshot, std::memory_order_release);
    lastThreshold_.store(threshold, std::memory_order_release);
    lastPeak_.store(peakInBuffer, std::memory_order_release);

    return emitted;
}
