# RudiMentor

A practice-pad drum trainer for Android.

Pick a level (rudiment pattern + tempo), practice along with the metronome while a running R/L cursor guides your sticking, and track your progress level by level.

## Planned MVP

- **Metronome** — a simple, low-latency metronome (development starts here)
- **Levels** — R/L sticking patterns with a running cursor, repetition counter, and locally stored progress

## Tech

- Kotlin, Jetpack Compose, Material 3
- Custom C++ audio engine built on [Oboe](https://github.com/google/oboe) with sample-accurate tick scheduling in the audio callback (no timer-based scheduling), designed with future microphone-based scoring in mind

## References

- [google/oboe](https://github.com/google/oboe) — audio engine and samples (RhythmGame, MinimalOboe)
- [Building a musical game using Oboe](https://developer.android.com/codelabs/musicalgame-using-oboe) — engine architecture codelab
- [Tack](https://github.com/patzly/tack-android) — metronome feature reference (GPLv3; architectural reference only, no code reused)

## License

Apache License 2.0 — see [LICENSE](LICENSE).
