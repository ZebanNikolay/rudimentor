// Standalone host test (no Oboe/Android dependency):
// Compile with C++17, -Wall -Wextra -Werror and -Iapp/src/main/cpp.
#include "AudioTempoLoop.h"

#include <array>
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>

int main() {
    const std::array<double, 5> frames{0, 48000, 96000, 120000, 132000};
    const std::array<double, 8> suffix{0, 48000, 96000, 120000, 132000, 156000, 168000, 192000};
    const std::array<double, 8> legacy{0, 48000, 96000, 120000, 132000, 180000, 228000, 252000};
    for (int beat = 0; beat < 8; ++beat) {
        assert(AudioTempoLoop::framesBeforeBeat(beat, frames.data(), 4, 2) == suffix[beat]);
        assert(AudioTempoLoop::framesBeforeBeat(beat, frames.data(), 4, 0) == legacy[beat]);
    }
    assert(AudioTempoLoop::framesBeforeBeat(5, frames.data(), 4, 3) == 144000);
    for (int invalid : {-1, 4, 5, std::numeric_limits<int>::max(), std::numeric_limits<int>::min()}) {
        assert(AudioTempoLoop::normalizedLoopStart(invalid, 4) == 0);
        assert(AudioTempoLoop::framesBeforeBeat(7, frames.data(), 4, invalid) == legacy[7]);
    }
    assert(AudioTempoLoop::normalizedLoopStart(0, 0) == 0);
    assert(AudioTempoLoop::framesBeforeBeat(-1, frames.data(), 4, 2) == 0);
    assert(AudioTempoLoop::framesBeforeBeat(1, nullptr, 0, 2) == 0);
    assert(AudioTempoLoop::framesBeforeBeat(1, frames.data(), 513, 2) == 0);
    assert(AudioTempoLoop::framesBeforeBeat(2000000000002LL, frames.data(), 4, 2)
           == 36000000000096000.0);

    // Fractional deadlines retain fractional frames for many hours without an
    // accumulated per-loop round-off; emitted integer ticks recover tick 0.
    const int sampleRate = 44101;
    const double warmUp = sampleRate * 0.30;
    const std::array<int, 5> bpms{137, 137, 83, 199, 211};
    std::array<double, 6> fractional{};
    for (int i = 0; i < 5; ++i) {
        fractional[i + 1] = fractional[i] + sampleRate * 60.0 / bpms[i];
    }
    for (int64_t beat : {1LL, 2LL, 4LL, 5LL, 6LL, 129LL, 1000001LL, 4294967333LL}) {
        const double before = AudioTempoLoop::framesBeforeBeat(beat, fractional.data(), 5, 2);
        const double after = AudioTempoLoop::framesBeforeBeat(beat + 1, fractional.data(), 5, 2);
        const int index = beat < 2 ? static_cast<int>(beat)
                                  : 2 + static_cast<int>((beat - 2) % 3);
        assert(std::abs(after - before - sampleRate * 60.0 / bpms[index]) < 0.02);
        const int64_t tick = static_cast<int64_t>(std::ceil(warmUp + before));
        const int64_t offset = static_cast<int64_t>(std::ceil(warmUp + before) - std::ceil(warmUp));
        assert(tick - offset == static_cast<int64_t>(std::ceil(warmUp)));
    }
    std::cout << "AudioTempoLoop native prefix/suffix, capacity, Long and fractional-frame checks passed\n";
}
