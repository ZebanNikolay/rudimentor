#pragma once

#include <atomic>
#include <cstdint>

/**
 * Time-domain onset detector for a single-mic drum pad.
 *
 * The detector runs in the audio callback with no allocations. Its job is to
 * emit a monotonic timestamp (in audio frames) for every stick hit picked up
 * by the microphone. Frames use the input stream's sample counter, so they can
 * be compared directly to the metronome tick frames.
 *
 * Pipeline (Bello onset-tutorial recipe for percussive signals):
 *   1. One-pole high-pass to remove DC / low-frequency rumble.
 *   2. Fast attack, slow release envelope follower.
 *   3. Peak pick against `median * factor + floor`, with a refractory window
 *      that stops a single transient from producing multiple onsets.
 *
 * All parameters are compile-time constants for now; the mic-lab UI exposes
 * the derived envelope + threshold so we can eyeball them and later promote
 * the good ones into settings.
 */
class OnsetDetector {
public:
    struct Params {
        // Envelope follower (attack << release keeps sharp transients visible).
        float attackCoeff = 0.30f;
        float releaseCoeff = 0.0015f;

        // Median-based adaptive threshold.
        int medianWindow = 512;          // ~10 ms at 48 kHz
        float thresholdFactor = 2.6f;    // envelope must exceed factor * median
        float thresholdFloor = 0.010f;   // minimum envelope to fire at all

        // Minimum gap between two onsets: at 40 ms the fastest diddle stays
        // resolvable, and a single stick strike never fires twice.
        int refractoryFrames = 1920;     // 40 ms @ 48 kHz

        // Highpass cutoff time constant (samples). Larger = lower cutoff.
        float highpassCoeff = 0.997f;    // ~25 Hz @ 48 kHz
    };

    struct Onset {
        int64_t frame;    // absolute input frame index
        float envelope;   // envelope value at the peak
        float threshold;  // adaptive threshold at the peak
    };

    void reset(int32_t sampleRate);
    void setParams(const Params &params);
    Params params() const { return params_; }

    /**
     * Feed one callback buffer. `startFrame` is the absolute frame index of
     * the first sample in the buffer. Returns the number of onsets written
     * into `out`, up to `outCapacity`. Excess onsets are discarded.
     */
    int process(const float *samples, int32_t numFrames, int64_t startFrame,
                Onset *out, int outCapacity);

    // Snapshots for the UI. These are updated every buffer; reads may lag a
    // frame or two but the mic lab does not need sample accuracy for the HUD.
    float lastEnvelope() const { return lastEnvelope_.load(std::memory_order_acquire); }
    float lastThreshold() const { return lastThreshold_.load(std::memory_order_acquire); }
    float lastPeak() const { return lastPeak_.load(std::memory_order_acquire); }

private:
    static constexpr int kMedianCapacity = 2048;

    void pushMedianSample(float value);
    float currentMedian() const;

    Params params_{};

    // Highpass state.
    float hpPrevIn_ = 0.0f;
    float hpPrevOut_ = 0.0f;

    // Envelope follower state.
    float envelope_ = 0.0f;

    // Ring buffer of recent |x| samples plus a running sorted copy is too
    // expensive; instead we keep a coarse histogram of the last N samples and
    // walk it every step. `medianWindow` <= kMedianCapacity is enforced.
    float medianRing_[kMedianCapacity]{};
    int medianHead_ = 0;
    int medianSize_ = 0;

    // Peak pick state.
    int refractory_ = 0;
    float prevEnvelope_ = 0.0f;
    float peakEnvelope_ = 0.0f;
    float peakThreshold_ = 0.0f;
    int64_t peakFrame_ = 0;
    bool rising_ = false;

    std::atomic<float> lastEnvelope_{0.0f};
    std::atomic<float> lastThreshold_{0.0f};
    std::atomic<float> lastPeak_{0.0f};
};
