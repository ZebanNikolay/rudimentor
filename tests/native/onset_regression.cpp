// Host-only regression runner. Compile against the frozen pre-diagnostics source
// to generate the oracle, and current source with CHECK_DIAGNOSTICS to verify it.
#include "OnsetDetector.h"
#ifdef CHECK_DIAGNOSTICS
#include "OnsetWire.h"
#endif

#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <string>
#include <vector>

namespace {
constexpr int64_t kStartFrame = int64_t{1} << 40;
constexpr double kPi = 3.14159265358979323846;

struct Fixture {
    std::string name;
    std::vector<float> samples;
};

std::vector<Fixture> fixtures(int rate) {
    std::vector<Fixture> out;
    const auto blank = [rate]() { return std::vector<float>(rate * 3 / 5, 0.0f); };
    out.push_back({"silence", blank()});
    auto impulse = blank();
    impulse[rate / 10 + 3] = 0.8f;
    out.push_back({"impulse", impulse});
    for (const double gap : {0.020, 0.040, 0.043, 0.050, 0.060, 1.0 / 12.0, 0.125}) {
        auto pair = impulse;
        pair[rate / 10 + 3 + static_cast<int>(rate * gap)] = 0.8f;
        out.push_back({"pair-" + std::to_string(gap), pair});
    }
    for (const float ratio : {0.04f, 0.2f}) {
        auto pair = impulse;
        pair[rate * 15 / 100 + 3] = 0.8f * ratio;
        out.push_back({"weak-second-" + std::to_string(ratio), pair});
        std::swap(pair[rate / 10 + 3], pair[rate * 15 / 100 + 3]);
        out.push_back({"weak-first-" + std::to_string(ratio), pair});
    }
    for (const double decay : {0.040, 0.100}) {
        auto tail = blank();
        for (int i = rate / 10; i < static_cast<int>(tail.size()); ++i) {
            const double t = static_cast<double>(i - rate / 10) / rate;
            tail[i] = static_cast<float>(0.4 * std::exp(-t / decay) *
                    (std::sin(2 * kPi * 80 * t) + std::sin(2 * kPi * 140 * t)));
        }
        out.push_back({"beating-" + std::to_string(decay), tail});
        tail[rate / 10 + rate / 12] += 0.8f;
        out.push_back({"tail-plus-real-" + std::to_string(decay), tail});
    }
    auto train = blank();
    for (int i = rate / 10; i < rate / 2; i += rate / 12) train[i] = 0.5f;
    out.push_back({"train-83ms", train});
    auto clipped = blank();
    for (int i = rate / 10; i < rate / 10 + rate / 200; ++i) {
        clipped[i] = (i % 8 < 4) ? 1.0f : -1.0f;
    }
    out.push_back({"clipped", clipped});
    auto sustained = blank();
    for (int i = rate / 10; i < rate / 2; ++i) sustained[i] = (i % 2) ? 0.5f : -0.5f;
    out.push_back({"sustained", sustained});
    uint32_t seed = 12345;
    auto noise = blank();
    for (float &sample : noise) {
        seed = 1664525u * seed + 1013904223u;
        sample = static_cast<float>(static_cast<int>(seed >> 16) - 32768) / 32768.0f * 0.02f;
    }
    out.push_back({"noise", noise});
    return out;
}

struct Fingerprint {
    uint64_t value = 14695981039346656037ull;
    void word(uint64_t v) {
        for (int i = 0; i < 8; ++i) {
            value ^= v & 255u;
            value *= 1099511628211ull;
            v >>= 8;
        }
    }
    void number(float f) {
        uint32_t bits;
        std::memcpy(&bits, &f, sizeof(bits));
        word(bits);
    }
};

#ifdef CHECK_DIAGNOSTICS
void checkEvidence(const Fixture &fixture, const OnsetDetector::Params &params,
                   int rate, int block, const std::vector<OnsetDetector::Onset> &events) {
    // Independent sample replay: no detector internals or callback snapshots.
    std::vector<float> hp(fixture.samples.size()), env(fixture.samples.size());
    float previousInput = 0.0f, previousHp = 0.0f, envelope = 0.0f;
    for (size_t i = 0; i < fixture.samples.size(); ++i) {
        const float x = fixture.samples[i];
        const float y = params.highpassCoeff * (previousHp + x - previousInput);
        previousInput = x;
        previousHp = y;
        hp[i] = std::fabs(y);
        envelope += (hp[i] > envelope ? params.attackCoeff : params.releaseCoeff) * (hp[i] - envelope);
        env[i] = envelope;
    }
    std::vector<float> adaptive(hp.size()), postFloor(hp.size());
    const int irregular[] = {5, 189, 32, 2048};
    int callback = 0;
    for (size_t start = 0; start < hp.size();) {
        const size_t length = std::min(static_cast<size_t>(
                block ? block : irregular[callback++ % 4]), hp.size() - start);
        const size_t from = start > static_cast<size_t>(params.medianWindow)
                ? start - params.medianWindow : 0;
        std::vector<float> history(hp.begin() + from, hp.begin() + start);
        std::sort(history.begin(), history.end());
        const float median = history.empty() ? 0.0f : history[history.size() / 2];
        const float threshold = std::max(params.thresholdFloor, median * params.thresholdFactor);
        std::fill(adaptive.begin() + start, adaptive.begin() + start + length, threshold);
        start += length;
    }
    float floor = 0.0f;
    size_t eventIndex = 0;
    for (size_t i = 0; i < hp.size(); ++i) {
        floor += params.releaseCoeff * (0.0f - floor);
        postFloor[i] = floor;
        if (eventIndex < events.size() &&
                events[eventIndex].diagnostics.commitFrame == kStartFrame + static_cast<int64_t>(i)) {
            floor = events[eventIndex++].envelope * params.postHitFloorFactor;
        }
    }
    int64_t sequence = 0, previousFrame = 0;
    size_t previousCommit = 0;
    float previousPeak = -1.0f;
    for (const auto &event : events) {
        const auto &d = event.diagnostics;
        const size_t arm = d.armFrame - kStartFrame;
        const size_t peak = d.peakFrame - kStartFrame;
        const size_t commit = d.commitFrame - kStartFrame;
        assert(d.sequence == ++sequence);
        assert(d.sampleRate == rate);
        assert(d.armFrame <= d.peakFrame && d.peakFrame < d.commitFrame);
        assert(d.peakFrame == event.frame);
        assert(d.previousPeakGapFrames == (sequence == 1 ? -1 : event.frame - previousFrame));
        assert(d.previousPeakEnvelope == previousPeak);
        assert(d.preArmEnvelope == (arm == 0 ? 0.0f : env[arm - 1]));
        assert(d.armEnvelope == env[arm]);
        assert(event.envelope == env[peak]);
        assert(d.commitEnvelope == env[commit]);
        assert(d.minimumEnvelopeBeforeArm == (sequence == 1 ? 0.0f :
                *std::min_element(env.begin() + previousCommit, env.begin() + arm)));
        assert(d.candidateSignalPeak == *std::max_element(hp.begin() + arm, hp.begin() + commit + 1));
        assert(event.threshold == std::max(d.adaptiveThresholdAtArm, d.postHitFloorAtArm));
        assert(d.adaptiveThresholdAtArm == adaptive[arm]);
        assert(d.postHitFloorAtArm == postFloor[arm]);
        assert(d.effectiveThresholdAtPeak == std::max(adaptive[peak], postFloor[peak]));
        assert(d.effectiveThresholdAtCommit == std::max(adaptive[commit], postFloor[commit]));
        assert(d.armEnvelope > event.threshold && d.armEnvelope > d.preArmEnvelope);
        assert(d.commitEnvelope < event.envelope * 0.85f);
        assert(d.thresholdFactorAtArm == params.thresholdFactor);
        assert(d.thresholdFloorAtArm == params.thresholdFloor);
        assert(d.medianWindowAtArm == params.medianWindow);
        assert(d.refractoryFramesAtArm == params.refractoryFrames);
        previousFrame = event.frame;
        previousPeak = event.envelope;
        previousCommit = commit;
    }
}

void checkDropAndReset() {
    OnsetDetector detector;
    detector.reset(48000);
    auto input = fixtures(48000)[1].samples;
    assert(detector.process(input.data(), input.size(), 0, nullptr, 0) == 0);
    OnsetDetector::Onset events[16];
    const auto count = detector.process(input.data(), input.size(), input.size(), events, 16);
    assert(count == 1);
    assert(events[0].diagnostics.sequence == 2);
    assert(events[0].diagnostics.previousPeakGapFrames == static_cast<int64_t>(input.size()));
    detector.reset(44100);
    assert(detector.process(nullptr, 0, 0, events, 16) == 0);
    assert(detector.process(input.data(), input.size(), 0, events, 16) == 1);
    assert(events[0].diagnostics.sequence == 1);
    assert(events[0].diagnostics.sampleRate == 44100);
    assert(events[0].diagnostics.previousPeakGapFrames == -1);
    assert(events[0].diagnostics.previousPeakEnvelope == -1.0f);
}

void checkWire() {
    OnsetDetector::Diagnostics d{};
    d.sequence = 101; d.sampleRate = 48000;
    d.peakFrame = kStartFrame + 6; d.armFrame = kStartFrame + 7; d.commitFrame = kStartFrame + 8;
    d.previousPeakGapFrames = -1;
    d.previousPeakEnvelope = -1.0f; d.preArmEnvelope = 0.125f; d.armEnvelope = 0.25f;
    d.minimumEnvelopeBeforeArm = 0.0625f; d.commitEnvelope = 0.5f;
    d.candidateSignalPeak = 0.75f; d.adaptiveThresholdAtArm = 0.03125f;
    d.postHitFloorAtArm = 0.015625f; d.effectiveThresholdAtPeak = 0.375f;
    d.effectiveThresholdAtCommit = 0.625f; d.thresholdFactorAtArm = 2.5f;
    d.thresholdFloorAtArm = 0.0078125f; d.medianWindowAtArm = 512;
    d.refractoryFramesAtArm = 1920;
    const int64_t expected[] = {
        -25, 500000, 250000, 1, 101, 48000, kStartFrame + 6, kStartFrame + 7,
        kStartFrame + 8, -1, -1000000, 125000, 250000, 62500, 500000, 750000,
        31250, 15625, 375000, 625000, 2500000, 7812, 512, 1920,
    };
    int64_t packed[OnsetWire::kStride + 1]{};
    packed[OnsetWire::kStride] = 999;
    OnsetWire::pack(packed, -25, 0.5f, 0.25f, d);
    assert(std::equal(std::begin(expected), std::end(expected), packed));
    assert(packed[OnsetWire::kStride] == 999);
}
#endif
}

int main() {
#ifdef CHECK_DIAGNOSTICS
    checkDropAndReset();
    checkWire();
#endif
    for (const int rate : {32000, 44100, 48000, 96000}) {
        for (const int block : {64, 192, 960, 0}) {
            for (const float sensitivity : {0.0f, 0.35f, 1.0f}) {
                for (const auto &fixture : fixtures(rate)) {
                    OnsetDetector detector;
                    OnsetDetector::Params params;
                    params.thresholdFactor = 1.6f + sensitivity * 3.5f;
                    params.thresholdFloor = 0.004f + sensitivity * 0.020f;
                    detector.setParams(params);
                    detector.reset(rate);
                    Fingerprint fingerprint;
                    std::vector<OnsetDetector::Onset> allEvents;
                    const int irregular[] = {5, 189, 32, 2048};
                    int callback = 0;
                    for (int start = 0; start < static_cast<int>(fixture.samples.size());) {
                        const int length = std::min(block ? block : irregular[callback++ % 4],
                                static_cast<int>(fixture.samples.size()) - start);
                        OnsetDetector::Onset events[16];
                        const int count = detector.process(fixture.samples.data() + start, length,
                                kStartFrame + start, events, 16);
                        fingerprint.word(count);
                        for (int i = 0; i < count; ++i) {
                            fingerprint.word(events[i].frame);
                            fingerprint.number(events[i].envelope);
                            fingerprint.number(events[i].threshold);
                            allEvents.push_back(events[i]);
                        }
                        fingerprint.number(detector.lastEnvelope());
                        fingerprint.number(detector.lastThreshold());
                        fingerprint.number(detector.lastPeak());
                        start += length;
                    }
#ifdef CHECK_DIAGNOSTICS
                    checkEvidence(fixture, params, rate, block, allEvents);
#endif
                    std::cout << fixture.name << ',' << rate << ',' << block << ',' << sensitivity
                              << ',' << allEvents.size() << ',' << fingerprint.value << '\n';
                }
            }
        }
    }
}
