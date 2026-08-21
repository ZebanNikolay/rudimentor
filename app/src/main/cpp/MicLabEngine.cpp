#include "MicLabEngine.h"

#include <algorithm>
#include <cmath>

#include <android/log.h>

namespace {
constexpr char kLogTag[] = "RudiMentorMicLab";
constexpr double kTwoPi = 6.283185307179586;

// Wide-band click, so the detector sees the same kind of transient as a stick
// hit if the phone speaker leaks into the mic. The frequency does not really
// matter for the mic lab; the click just needs to be short and loud.
constexpr double kClickFrequency = 1320.0;
constexpr double kClickAccentFrequency = 1760.0;
}  // namespace

MicLabEngine::MicLabEngine() {
    OnsetDetector::Params params;
    detector_.setParams(params);
}

MicLabEngine::~MicLabEngine() {
    stop();
}

bool MicLabEngine::start() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (running_.load(std::memory_order_acquire)) {
        return true;
    }
    if (!openStreams()) {
        return false;
    }

    outputFrames_ = 0;
    outputFramesPublished_.store(0, std::memory_order_release);
    inputFrames_.store(0, std::memory_order_release);
    inputFrameZero_.store(-1, std::memory_order_release);
    nextTickFrame_ = static_cast<double>(sampleRate_) * 0.30;  // brief warm-up
    step_ = 0;
    clickFrame_ = kClickFrames;
    phase_ = 0.0;
    tickCount_.store(0, std::memory_order_release);
    hitsWrite_.store(0, std::memory_order_release);
    hitsRead_.store(0, std::memory_order_release);
    ticksWrite_.store(0, std::memory_order_release);
    ticksRead_.store(0, std::memory_order_release);

    detector_.reset(sampleRate_);
    running_.store(true, std::memory_order_release);

    // Start input first so that by the time the output callback fires, the
    // mic buffer is already filling. FullDuplexStream is overkill here: we
    // only need the frame counter to be shared, and both streams share the
    // same sample rate, which is enough for our timing model.
    if (inputStream_) {
        const oboe::Result r = inputStream_->requestStart();
        if (r != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                                "input requestStart failed: %s", oboe::convertToText(r));
            running_.store(false, std::memory_order_release);
            closeStreams();
            return false;
        }
    }

    if (outputStream_) {
        const oboe::Result r = outputStream_->requestStart();
        if (r != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                                "output requestStart failed: %s", oboe::convertToText(r));
            running_.store(false, std::memory_order_release);
            closeStreams();
            return false;
        }
    }

    return true;
}

void MicLabEngine::stop() {
    std::lock_guard<std::mutex> lock(streamMutex_);
    running_.store(false, std::memory_order_release);
    closeStreams();
}

void MicLabEngine::setBpm(int bpm) {
    bpm_.store(std::clamp(bpm, kMinBpm, kMaxBpm), std::memory_order_release);
}

void MicLabEngine::setClickAudible(bool audible) {
    clickAudible_.store(audible, std::memory_order_release);
}

void MicLabEngine::setSensitivity(float sensitivity01) {
    // sensitivity01 in [0..1]: 0 = permissive (low factor, low floor),
    // 1 = strict. Values chosen empirically for a stick on a mesh pad.
    const float clamped = std::clamp(sensitivity01, 0.0f, 1.0f);
    OnsetDetector::Params params = detector_.params();
    params.thresholdFactor = 1.6f + clamped * 3.5f;    // 1.6 .. 5.1
    params.thresholdFloor = 0.004f + clamped * 0.020f; // 0.004 .. 0.024
    detector_.setParams(params);
}

void MicLabEngine::setInputLatencyMillis(float millis) {
    // Positive number = subtract this many frames from the reported hit
    // frame, i.e. compensate for the microphone path. Applied at drain time.
    const float frames = (millis / 1000.0f) * static_cast<float>(sampleRate_);
    inputLatencyFrames_.store(frames, std::memory_order_release);
}

int MicLabEngine::drainHits(HitEvent *outHits, int maxHits) {
    if (outHits == nullptr || maxHits <= 0) {
        return 0;
    }
    const float latencyFrames = inputLatencyFrames_.load(std::memory_order_acquire);
    const int64_t frameZero = inputFrameZero_.load(std::memory_order_acquire);
    int copied = 0;
    uint32_t read = hitsRead_.load(std::memory_order_relaxed);
    const uint32_t write = hitsWrite_.load(std::memory_order_acquire);
    while (read != write && copied < maxHits) {
        HitEvent event = hits_[read % kEventCapacity];
        // Re-anchor to the output stream's t=0 before applying any
        // user-facing latency compensation, so the slider only has to
        // correct for genuine acoustic/round-trip latency, not for the
        // input-vs-output stream-start skew.
        if (frameZero >= 0) {
            event.frame -= frameZero;
        }
        event.frame -= static_cast<int64_t>(latencyFrames);
        outHits[copied++] = event;
        ++read;
    }
    hitsRead_.store(read, std::memory_order_release);
    return copied;
}

int MicLabEngine::drainTicks(TickEvent *outTicks, int maxTicks) {
    if (outTicks == nullptr || maxTicks <= 0) {
        return 0;
    }
    int copied = 0;
    uint32_t read = ticksRead_.load(std::memory_order_relaxed);
    const uint32_t write = ticksWrite_.load(std::memory_order_acquire);
    while (read != write && copied < maxTicks) {
        outTicks[copied++] = ticks_[read % kEventCapacity];
        ++read;
    }
    ticksRead_.store(read, std::memory_order_release);
    return copied;
}

MicLabEngine::Snapshot MicLabEngine::snapshot() const {
    return Snapshot{
            sampleRate_,
            inputFrames_.load(std::memory_order_acquire),
            outputFramesPublished_.load(std::memory_order_acquire),
            tickCount_.load(std::memory_order_acquire),
            detector_.lastEnvelope(),
            detector_.lastThreshold(),
            detector_.lastPeak(),
            clickAudible_.load(std::memory_order_acquire),
            running_.load(std::memory_order_acquire),
    };
}

void MicLabEngine::publishHit(const OnsetDetector::Onset &onset) {
    const uint32_t write = hitsWrite_.load(std::memory_order_relaxed);
    hits_[write % kEventCapacity] = HitEvent{onset.frame, onset.envelope, onset.threshold};
    hitsWrite_.store(write + 1, std::memory_order_release);
}

void MicLabEngine::publishTick(int64_t frame, int64_t index) {
    const uint32_t write = ticksWrite_.load(std::memory_order_relaxed);
    ticks_[write % kEventCapacity] = TickEvent{frame, index};
    ticksWrite_.store(write + 1, std::memory_order_release);
}

oboe::DataCallbackResult MicLabEngine::onAudioReady(
        oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
    if (audioStream->getDirection() == oboe::Direction::Input) {
        // Feed the detector; the frame index of the first sample in the
        // buffer is the current input frame counter.
        const int64_t startFrame = inputFrames_.load(std::memory_order_acquire);
        const auto *samples = static_cast<const float *>(audioData);

        OnsetDetector::Onset onsets[16];
        const int emitted = detector_.process(samples, numFrames, startFrame,
                                              onsets, 16);
        for (int i = 0; i < emitted; ++i) {
            publishHit(onsets[i]);
        }
        inputFrames_.store(startFrame + numFrames, std::memory_order_release);
        return running_.load(std::memory_order_acquire)
                ? oboe::DataCallbackResult::Continue
                : oboe::DataCallbackResult::Stop;
    }

    // Output stream: render the click and schedule ticks. We use the output
    // frame counter as the tick timestamp; the detector uses the input frame
    // counter for hits. Both counters advance at the same sample rate and
    // start ~simultaneously (input opened first), so the offset between them
    // is bounded by round-trip latency. That offset is what `setInputLatency`
    // compensates.
    // On the very first output callback, snapshot how far the input frame
    // counter has already advanced. This captures the stream-start skew
    // exactly once per start(), independent of how long the output stream
    // takes to spin up.
    if (outputFrames_ == 0 && inputFrameZero_.load(std::memory_order_acquire) < 0) {
        const int64_t skewFrames = inputFrames_.load(std::memory_order_acquire);
        inputFrameZero_.store(skewFrames, std::memory_order_release);
        // Diagnostic only: this is the stream-*start* skew (how far the input
        // counter had already advanced before the output stream's first
        // callback), not the full loopback round-trip latency. If a
        // self-loopback test still reports a large mean offset after this
        // value is subtracted, the residual is genuine output+input hardware
        // latency, not counter skew, and needs a different fix (device
        // timestamps or a nonzero default on the latency slider).
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
                            "stream-start skew: %lld frames (%.1f ms)",
                            static_cast<long long>(skewFrames),
                            static_cast<double>(skewFrames) * 1000.0 / sampleRate_);
    }

    auto *output = static_cast<float *>(audioData);
    const int32_t channelCount = audioStream->getChannelCount();
    const bool audible = clickAudible_.load(std::memory_order_acquire);
    const double framesPerBeat =
            static_cast<double>(sampleRate_) * 60.0 /
            static_cast<double>(bpm_.load(std::memory_order_acquire));

    for (int32_t f = 0; f < numFrames; ++f) {
        const int64_t frameIndex = outputFrames_ + f;
        if (static_cast<double>(frameIndex) >= nextTickFrame_) {
            tickCount_.fetch_add(1, std::memory_order_acq_rel);
            publishTick(frameIndex, step_);
            step_ += 1;
            clickFrame_ = 0;
            phase_ = 0.0;
            nextTickFrame_ += framesPerBeat;
        }

        float sample = 0.0f;
        if (clickFrame_ < kClickFrames && audible) {
            const double frequency = (step_ % 4 == 1)
                    ? kClickAccentFrequency
                    : kClickFrequency;
            const float envelope = std::exp(-7.0f * static_cast<float>(clickFrame_) /
                                            static_cast<float>(kClickFrames));
            sample = 0.55f * envelope * static_cast<float>(std::sin(phase_));
            phase_ += kTwoPi * frequency / static_cast<double>(sampleRate_);
        }
        if (clickFrame_ < kClickFrames) {
            ++clickFrame_;
        }

        for (int32_t c = 0; c < channelCount; ++c) {
            output[f * channelCount + c] = sample;
        }
    }

    outputFrames_ += numFrames;
    outputFramesPublished_.store(outputFrames_, std::memory_order_release);
    return running_.load(std::memory_order_acquire)
            ? oboe::DataCallbackResult::Continue
            : oboe::DataCallbackResult::Stop;
}

void MicLabEngine::onErrorAfterClose(oboe::AudioStream *, oboe::Result error) {
    running_.store(false, std::memory_order_release);
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "stream closed: %s",
                        oboe::convertToText(error));
}

bool MicLabEngine::openStreams() {
    // Output first, to lock in the sample rate.
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output);
    outBuilder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    outBuilder.setSharingMode(oboe::SharingMode::Exclusive);
    outBuilder.setFormat(oboe::AudioFormat::Float);
    outBuilder.setChannelCount(oboe::ChannelCount::Mono);
    outBuilder.setDataCallback(this);
    outBuilder.setErrorCallback(this);

    oboe::Result outResult = outBuilder.openStream(outputStream_);
    if (outResult != oboe::Result::OK) {
        outBuilder.setSharingMode(oboe::SharingMode::Shared);
        outResult = outBuilder.openStream(outputStream_);
    }
    if (outResult != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "open output failed: %s", oboe::convertToText(outResult));
        return false;
    }
    sampleRate_ = outputStream_->getSampleRate();
    outputStream_->setBufferSizeInFrames(outputStream_->getFramesPerBurst() * 2);

    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input);
    inBuilder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    inBuilder.setSharingMode(oboe::SharingMode::Shared);
    inBuilder.setFormat(oboe::AudioFormat::Float);
    inBuilder.setChannelCount(oboe::ChannelCount::Mono);
    inBuilder.setSampleRate(sampleRate_);
    // UNPROCESSED bypasses AGC / noise suppression, which would blur the
    // transients we rely on. Fallback stays automatic if the device does
    // not honour the preset.
    inBuilder.setInputPreset(oboe::InputPreset::Unprocessed);
    inBuilder.setDataCallback(this);
    inBuilder.setErrorCallback(this);
    // Larger input capacity so the input side never overflows if the output
    // callback runs a bit late; capacity does not affect latency.
    inBuilder.setBufferCapacityInFrames(outputStream_->getBufferCapacityInFrames() * 2);

    oboe::Result inResult = inBuilder.openStream(inputStream_);
    if (inResult != oboe::Result::OK) {
        inBuilder.setInputPreset(oboe::InputPreset::VoiceRecognition);
        inResult = inBuilder.openStream(inputStream_);
    }
    if (inResult != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
                            "open input failed: %s", oboe::convertToText(inResult));
        closeStreams();
        return false;
    }
    inputStream_->setBufferSizeInFrames(inputStream_->getFramesPerBurst() * 2);

    // Set a modest starting sensitivity; the UI can override.
    setSensitivity(0.35f);
    return true;
}

void MicLabEngine::closeStreams() {
    if (outputStream_) {
        outputStream_->stop();
        outputStream_->close();
        outputStream_.reset();
    }
    if (inputStream_) {
        inputStream_->stop();
        inputStream_->close();
        inputStream_.reset();
    }
}
