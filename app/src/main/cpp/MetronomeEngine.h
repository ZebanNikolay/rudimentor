#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>

#include <oboe/Oboe.h>

class MetronomeEngine : public oboe::AudioStreamDataCallback,
                        public oboe::AudioStreamErrorCallback {
public:
    /** Maximum number of steps: 8 rows x 16 beats. */
    static constexpr int kMaxSteps = 128;

    /** Step encoding shared with Kotlin: state = step & 3, hand = (step >> 2) & 1. */
    static constexpr int kStateNormal = 0;
    static constexpr int kStateAccent = 1;
    static constexpr int kStateMute = 2;

    MetronomeEngine();

    bool start();
    void stop();
    void setBpm(int bpm);
    void setSequence(const int *steps, int count);
    int64_t tickCount() const;
    /** False once the stream is closed by an error; the UI polls it to catch a silent stop. */
    bool isRunning() const;

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) override;

private:
    bool openStream();

    static constexpr int kMinBpm = 40;
    static constexpr int kMaxBpm = 240;
    static constexpr int kClickFrames = 960;

    mutable std::mutex streamMutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<int> bpm_{100};

    // Double buffered sequence: the UI thread fills the idle buffer and then
    // publishes it, so the audio callback never reads a half-written pattern.
    int sequences_[2][kMaxSteps]{};
    int sequenceLengths_[2]{};
    std::atomic<int> activeSequence_{0};
    std::mutex sequenceMutex_;

    std::atomic<int64_t> tickCount_{0};
    std::atomic<bool> running_{false};
    int32_t sampleRate_ = 48000;
    int64_t renderedFrames_ = 0;
    double nextTickFrame_ = 0.0;
    int64_t step_ = 0;
    int clickFrame_ = kClickFrames;
    bool clickAccent_ = false;
    bool clickLeftHand_ = false;
    double phase_ = 0.0;
};
