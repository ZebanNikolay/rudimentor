#include "MetronomeEngine.h"

#include <algorithm>
#include <cmath>

#include <android/log.h>

namespace {
constexpr char kLogTag[] = "RudiMentorAudio";
constexpr double kTwoPi = 6.283185307179586;
}

MetronomeEngine::MetronomeEngine() {
    // A single accented right-hand beat, until the UI sends the real grid.
    sequences_[0][0] = MetronomeEngine::kStateAccent;
    sequenceLengths_[0] = 1;
    sequenceLengths_[1] = 0;
}

bool MetronomeEngine::start() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (running_.load(std::memory_order_acquire)) {
        return true;
    }
    if (!openStream()) {
        return false;
    }

    renderedFrames_ = 0;
    nextTickFrame_ = 0.0;
    step_ = 0;
    clickFrame_ = kClickFrames;
    clickAccent_ = false;
    clickLeftHand_ = false;
    phase_ = 0.0;
    tickCount_.store(0, std::memory_order_release);
    running_.store(true, std::memory_order_release);

    const oboe::Result result = stream_->requestStart();
    if (result == oboe::Result::OK) {
        return true;
    }

    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Could not start stream: %s",
                        oboe::convertToText(result));
    running_.store(false, std::memory_order_release);
    stream_->close();
    stream_.reset();
    return false;
}

void MetronomeEngine::stop() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    running_.store(false, std::memory_order_release);
    if (!stream_) {
        return;
    }
    stream_->stop();
    stream_->close();
    stream_.reset();
}

void MetronomeEngine::setBpm(int bpm) {
    bpm_.store(std::clamp(bpm, kMinBpm, kMaxBpm), std::memory_order_release);
}

void MetronomeEngine::setSequence(const int *steps, int count) {
    if (steps == nullptr || count <= 0) {
        return;
    }
    std::lock_guard<std::mutex> lock(sequenceMutex_);
    const int idle = 1 - activeSequence_.load(std::memory_order_acquire);
    const int length = std::min(count, kMaxSteps);
    for (int index = 0; index < length; ++index) {
        sequences_[idle][index] = steps[index];
    }
    sequenceLengths_[idle] = length;
    activeSequence_.store(idle, std::memory_order_release);
}

int64_t MetronomeEngine::tickCount() const {
    return tickCount_.load(std::memory_order_acquire);
}

oboe::DataCallbackResult MetronomeEngine::onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) {
    auto *output = static_cast<float *>(audioData);
    const int32_t channelCount = audioStream->getChannelCount();

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        if (static_cast<double>(renderedFrames_) >= nextTickFrame_) {
            const int buffer = activeSequence_.load(std::memory_order_acquire);
            const int length = sequenceLengths_[buffer];
            const int stepIndex = length > 0 ? static_cast<int>(step_ % length) : 0;
            const int encoded = length > 0 ? sequences_[buffer][stepIndex] : kStateNormal;
            const int state = encoded & 3;
            const bool muted = state == kStateMute;

            tickCount_.fetch_add(1, std::memory_order_acq_rel);
            step_ += 1;

            clickAccent_ = state == kStateAccent;
            clickLeftHand_ = ((encoded >> 2) & 1) == 1;
            clickFrame_ = muted ? kClickFrames : 0;
            phase_ = 0.0;
            nextTickFrame_ += static_cast<double>(sampleRate_) * 60.0 /
                              bpm_.load(std::memory_order_acquire);
        }

        float sample = 0.0F;
        if (clickFrame_ < kClickFrames) {
            const double baseFrequency = clickLeftHand_ ? 1120.0 : 1320.0;
            const double frequency = clickAccent_ ? baseFrequency + 440.0 : baseFrequency;
            const float amplitude = clickAccent_ ? 0.8F : 0.5F;
            const float envelope = std::exp(-7.0F * static_cast<float>(clickFrame_) /
                                            static_cast<float>(kClickFrames));
            sample = amplitude * envelope * static_cast<float>(std::sin(phase_));
            phase_ += kTwoPi * frequency / sampleRate_;
            ++clickFrame_;
        }

        for (int32_t channel = 0; channel < channelCount; ++channel) {
            output[frame * channelCount + channel] = sample;
        }
        ++renderedFrames_;
    }

    return running_.load(std::memory_order_acquire)
            ? oboe::DataCallbackResult::Continue
            : oboe::DataCallbackResult::Stop;
}

bool MetronomeEngine::isRunning() const {
    return running_.load(std::memory_order_acquire);
}

void MetronomeEngine::onErrorAfterClose(oboe::AudioStream *, oboe::Result error) {
    running_.store(false, std::memory_order_release);
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Audio stream closed: %s",
                        oboe::convertToText(error));
}

bool MetronomeEngine::openStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(oboe::ChannelCount::Mono);
    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    const oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        builder.setSharingMode(oboe::SharingMode::Shared);
        const oboe::Result fallbackResult = builder.openStream(stream_);
        if (fallbackResult != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Could not open stream: %s",
                                oboe::convertToText(fallbackResult));
            return false;
        }
    }

    sampleRate_ = stream_->getSampleRate();
    stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 2);
    return true;
}
