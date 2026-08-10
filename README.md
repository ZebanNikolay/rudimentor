# RudiMentor

A practice-pad drum trainer for Android.

Pick a level (rudiment pattern + tempo), practice along with the metronome while a running R/L cursor guides your sticking, and track your progress level by level.

## Current MVP

- **Metronome** — a low-latency metronome with a 30–240 BPM range, ±10/±20 controls, and an editable 4–8 beat pattern
- **Pattern accents** — in abstract mode, tap any beat to switch between a stronger accented click and the regular click
- **Custom sticking** — in R/L mode, tap each beat independently to build any right/left sequence
- **Prototype Lab** — compare ten numbered, source-backed interactive beat indicators and choose the persisted style used by Metronome
- **Developer palettes** — switch among P1–P6 Material 3 role schemes generated from published color seeds
- **Levels** — R/L sticking patterns with a running cursor, repetition counter, and locally stored progress

## Tech

- Kotlin, Jetpack Compose, Material 3
- Custom C++ audio engine built on [Oboe](https://github.com/google/oboe) with sample-accurate tick scheduling in the audio callback (no timer-based scheduling), designed with future microphone-based scoring in mind

The audio callback schedules ticks against its rendered-frame counter and chooses the configured accent and independent R/L timbre at that exact frame. It logs every tick as `tick`, `frame`, `bpm`, `beat`, `accent`, and `hand` under the `RudiMentorAudio` log tag. Kotlin receives the completed tick count through JNI for UI animation; it does not schedule audio.

Preferences DataStore persists the selected tracker style, developer palette, abstract/R-L mode, 4–8 beat length, accents, and per-beat hands through a repository and ViewModel. UI composables never access DataStore directly.

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
- [Kr0oked/Metronome](https://github.com/Kr0oked/Metronome) — visual reference only (GPL-3.0; no code or assets reused)
- [Material Color Utilities](https://github.com/material-foundation/material-color-utilities) — generated Material 3 color roles (Apache-2.0)
- [Radix Colors](https://www.radix-ui.com/colors) and [Open Color](https://yeun.github.io/open-color/) — published palette seeds

## License

Apache License 2.0 — see [LICENSE](LICENSE).
