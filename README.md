# RudiMentor

A practice-pad drum trainer for Android.

Pick a level (rudiment pattern + tempo), practice along with the metronome while a running R/L cursor guides your sticking, and track your progress level by level.

## Current MVP

- **Metronome** — a low-latency metronome with a 30–240 BPM range, ±1/±5 controls, and an editable 4–8 beat pattern
- **Pattern accents** — in abstract mode, tap any beat to switch between a stronger accented click and the regular click
- **Custom sticking** — in R/L mode, tap each beat independently to build any right/left sequence
- **Levels** — R/L sticking patterns with a running cursor, repetition counter, and locally stored progress

## Tech

- Kotlin, Jetpack Compose, Material 3
- Custom C++ audio engine built on [Oboe](https://github.com/google/oboe) with sample-accurate tick scheduling in the audio callback (no timer-based scheduling), designed with future microphone-based scoring in mind

The audio callback schedules ticks against its rendered-frame counter and chooses the configured accent at that exact frame. It logs every tick as `tick`, `frame`, `bpm`, `beat`, and `accent` under the `RudiMentorAudio` log tag. Kotlin receives the completed tick count through JNI for UI animation; it does not schedule audio.

## Build

Install Android SDK 35, NDK, and CMake 3.22.1, then run:

```shell
./gradlew assembleDebug
```

Debug builds use Android's default debug key unless `RUDIMENTOR_DEBUG_KEYSTORE` points to the project's private development keystore. Maintainers should use that stable keystore when producing installable development updates so Android recognizes each APK as the same app:

```shell
RUDIMENTOR_DEBUG_KEYSTORE=/private/path/rudimentor-dev-debug.keystore ./gradlew assembleDebug
```

The optional `RUDIMENTOR_DEBUG_KEYSTORE_PASSWORD`, `RUDIMENTOR_DEBUG_KEY_ALIAS`, and `RUDIMENTOR_DEBUG_KEY_PASSWORD` variables override the standard Android debug-key defaults. The generated APK name and the version shown on the main screen both identify its version and build number.

Open the project in Android Studio to install missing SDK components automatically and run it on a device with Android 8.1 or newer.

## References

- [google/oboe](https://github.com/google/oboe) — audio engine and samples (RhythmGame, MinimalOboe)
- [Building a musical game using Oboe](https://developer.android.com/codelabs/musicalgame-using-oboe) — engine architecture codelab
- [Tack](https://github.com/patzly/tack-android) — metronome feature reference (GPLv3; architectural reference only, no code reused)

## License

Apache License 2.0 — see [LICENSE](LICENSE).
