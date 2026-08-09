#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>

#include <oboe/Oboe.h>

class MetronomeEngine : public oboe::AudioStreamDataCallback,
                        public oboe::AudioStreamErrorCallback {
public:
    bool start();
    void stop();
    void setBpm(int bpm);
    void setPattern(int beatCount, int accentMask);
    int64_t tickCount() const;

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) override;

private:
    bool openStream();
    static constexpr int kMinBpm = 30;
    static constexpr int kMaxBpm = 240;
    static constexpr int kMinBeats = 4;
    static constexpr int kMaxBeats = 8;
    static constexpr int kPatternShift = 8;
    static constexpr int kClickFrames = 960;

    mutable std::mutex streamMutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<int> bpm_{120};
    std::atomic<int> pattern_{(4 << kPatternShift) | 1};
    std::atomic<int64_t> tickCount_{0};
    std::atomic<bool> running_{false};
    int32_t sampleRate_ = 48000;
    int64_t renderedFrames_ = 0;
    double nextTickFrame_ = 0.0;
    int clickFrame_ = kClickFrames;
    bool clickAccent_ = false;
    double phase_ = 0.0;
};
