# Event-local onset diagnostics v1

Diagnostics only: no change to detector acceptance, timestamps, loudness gate,
debounce, matching, scoring, UI, calibration, or recording policy. No PCM/audio
is recorded. No shadow classifier or physical labels are produced.

## Export contract

Normal practice **full exports** include these fields by default, on the existing
`hit` and `quiet` JSONL records. No new switch or separate event stream is required.
The short human summary is unchanged.

- Both `session` and `attempt` have top-level `onsetDiagnosticsVersion: 1`.
  This is a **schema capability**, not proof of a complete trace or labelled data.
  Use this marker rather than `build` to index logs with the new schema.
- `session.onsetDetector: "time-domain-v1"` identifies the unchanged detector.
- Each available event has `onsetDiagnosticsVersion: 1`. Existing Kotlin callers
  can omit diagnostics (`null` default); those events explicitly report version
  `0` and omit evidence fields. Historical exports have no marker.
- Legacy `atMs`, `env`, `thr`, `peak`, outcomes and summary fields retain their
  values and meanings. Readers must tolerate additional fields.
- `pollPeak` is an explicit alias of legacy `hit.peak`: max `abs(highpass)` in the
  **last completed input callback** seen by the snapshot, NOT this candidate,
  NOT the maximum since reset, and NOT necessarily between successive polls.
- `env` is the candidate's peak envelope. `thr` is the **effective threshold
  latched at ARM**, not the adaptive threshold at peak or at commit.

## Per-event fields and provenance

All frame fields are exact 64-bit integers on the **raw input sample clock**.
They are not re-anchored or latency-corrected. Divide frame differences by
`onsetSampleRate / 1000` for milliseconds; do not subtract raw frames from `atMs`.
Only the existing event `frame`/`atMs` follows the old compensation path.

| JSON field | Meaning |
| --- | --- |
| `onsetSequence` | 1-based native **commit** sequence since detector reset, including commits discarded because the detector output array is full. Not a physical-strike ID. |
| `onsetSampleRate` | Rate supplied at reset. The detector still uses sample-based parameters; this does not normalize them. |
| `onsetArmFrame` | First eligible rising-envelope sample above the effective threshold. Not an estimate of first physical contact. |
| `onsetPeakFrame` | Raw frame of the final maximum envelope; equal maxima move the peak to the later sample, as before. |
| `onsetCommitFrame` | Sample where envelope first falls below 85% of that peak. Refractory begins after this sample, not after peak. |
| `onsetPreviousPeakGapFrames` | This raw peak minus the preceding **committed native** peak; `null` after reset. Independent of Kotlin gating, log ordering and latency changes. |
| `onsetPreviousPeakEnv` | Envelope of that preceding native peak, or `null` after reset. |
| `onsetPreArmEnv` | Envelope of the sample immediately before ARM (0 for the reset boundary). |
| `onsetArmEnv` | Envelope at ARM. |
| `onsetMinEnvBeforeArm` | Minimum envelope from preceding COMMIT through the sample before ARM, inclusive; reset contributes initial 0. It spans refractory/settle. This is **not** necessarily the latest local valley or a fixed-time attack window. |
| `onsetCommitEnv` | Envelope at COMMIT. Peak envelope remains in legacy `env`. |
| `candidateSignalPeak` | Exact running maximum of `abs(highpass(input))` over **ARM..COMMIT inclusive**, before wire quantization. One bounded candidate window, not one proven physical strike. It excludes pre-ARM samples; merged attacks inside this window share a maximum. |
| `onsetAdaptiveThresholdAtArm` | `max(thresholdFloor, median * thresholdFactor)` from ARM's callback. Median is based on absolute high-pass samples preceding that callback, updated once per callback. |
| `onsetPostHitFloorAtArm` | Independently decayed post-hit floor on the ARM sample, after that sample's decay. |
| `onsetEffectiveThresholdAtPeak` | `max(adaptive, postHitFloor)` observed on the final peak sample, not the latched `thr`. |
| `onsetEffectiveThresholdAtCommit` | Same observation on COMMIT, **before** the floor is reseeded for the next event. |
| `onsetThresholdFactorAtArm`, `onsetThresholdFloorAtArm` | Actual parameter values when ARM executes, not recomputed from a rounded UI sensitivity. |
| `onsetMedianWindowAtArm`, `onsetRefractoryFramesAtArm` | Actual configured sample counts when ARM executes. |

All new levels have six fractional digits. JNI encodes levels as signed integer
`value * 1e6`, truncating toward zero, as for the old fields; precision below
1e-6 is not retained. Legacy levels still use their old serialization precision.
No predecessor is represented natively as gap/envelope `-1`, translated to JSON
`null`. Zero remains a known zero. Non-finite Kotlin levels serialize as `null`.

The rest of `time-domain-v1` is unchanged: high-pass coefficient 0.997, attack
0.30, release 0.0015, settle factor 0.35, post-hit floor factor 0.5, commit ratio
0.85. Default median window is 512 samples; refractory is 1920 samples. Practice
sensitivity maps to factor `1.6 + sensitivity * 3.5` and floor
`0.004 + sensitivity * 0.020`. These are sample-based constants at every rate,
not guaranteed 40 ms refractory outside 48 kHz. Changes to this algorithm need a
new detector identifier; changes to field semantics need a new diagnostic version.

## Bounded implementation

`OnsetDetector` keeps one fixed-size diagnostic record for the open candidate plus
scalar history. A min/max observation is updated per sample and copied at ARM/
COMMIT. No callback allocation, lock, formatting, file I/O, sample history or
additional ring is introduced. Detector decision branches never read diagnostics.

The existing 16-onset callback array and 128-hit engine ring carry this record.
JNI packing and Kotlin object/string construction happen on the polling thread.
On the host ABI the diagnostic record is 104 bytes and the full onset is 120
bytes: about 1.6 KiB added to the callback array and 13 KiB to the hit ring.
`OnsetWire.h` and `OnsetHitWire.kt` share a 24-long stride (192 bytes/event);
the original corrected frame/env/thr remain slots 0–2. Both native and Kotlin
must ship together: this is an internal ABI, not mixed-binary compatibility.
The pure decoder accepts legacy triples for tests/offline adapters; unknown
diagnostic versions become unavailable rather than being misinterpreted.

MicLab/calibration consume the same updated `NativeMicLab.drainHits` decoder
but keep their existing env/threshold/timing behavior. This change adds evidence
to **practice** exports, not calibration exports.

The existing 4,000-event log cap and closing result/abort behavior remain in force.
New fields enlarge each retained hit by roughly 0.8–1 KB, at most roughly 4 MB
of additional UTF-8 per maximally hit-filled trace; no per-sample trace is saved.
Existing export retention is unchanged.

`Copy all` refuses text above 200,000 UTF-16 code units and handles clipboard
failures without truncation or a crash. Its existing notice directs the user to
`Share`, which transfers the complete file by URI rather than clipboard payload.

## What this cannot establish

- Events exist only after COMMIT. A second attack while a candidate is open can
  merge into it. Attacks suppressed by refractory/settle/threshold, or an open
  candidate stopped before commit, have no separate evidence record.
- There are no rejected-candidate records, rejection reasons, audio labels,
  hand identification, synchrony classifier, calibrated velocity, fixed-window
  RMS or spectral features. One event does not prove that both hands coincided.
  A weak rerise does not prove a rebound, room reflection, click leakage or intent.
- Sequence/gap refer to native commits, not adjacent exported lines. Practice
  drains accepted and quiet hits separately, so JSON line order need not be
  chronological. Pre-anchor/count-in filtering, the existing ring/output limits,
  stopping, and the log event cap can omit records.
- IDs reset when the detector/streams reset. An in-attempt reopen can restart
  them. Gaps or non-monotonic IDs signal incomplete/reset/overwritten history but
  are **not** a drop counter or a count of suppressed physical attacks.
- No existing ring-overrun policy, parameter-threading behavior, callback
  scheduling or hardware latency is changed by these observations. Host tests
  are not a measurement of live Android callback performance.

## Regression checks

Run from the repository root:

```sh
python3 tools/test_onset_diagnostics.py
python3 tools/test_onset_diagnostics.py --sanitize
```

The host runner uses 21 deterministic synthetic fixtures, four sample rates
(32/44.1/48/96 kHz), four callback schedules (64/192/960 and irregular
5/189/32/2048), and three sensitivity settings: **1,008 configurations**.
Inputs cover silence, impulses, close pairs (including 40/43/50 ms), weak/strong
orders, beating tails, tail plus a real impulse, 83.333 ms trains, clipping,
sustain and deterministic noise.

`tests/native/onset-baseline.csv` stores event counts and an FNV-1a fingerprint
of every legacy emitted frame/envelope/threshold and every callback snapshot,
including per-buffer event counts. It was generated from the untouched source
in main `9f5ff138226a851b5c306b4d3534bc218f327ed7`, **not** from the instrumented
detector. The test enforces exact host parity, including existing missed/false
events; it does not claim the original detector is correct.

Original file SHA-256:

```text
OnsetDetector.cpp 81f9fb698ca3c8210752e834ed9f4d40bf2d259f044af48070507f299f363267
OnsetDetector.h   ff8dff01c7bfd1633b0038a090c7ee79be08f404a1a3b8af6975fe64ebcd1638
```

The optional `--baseline-dir <original-source-directory>` first checks both
hashes and independently verifies the saved oracle. It never updates the oracle.
Compilation uses g++ C++17, `-O2 -ffp-contract=off`; platform/libm differences may
require investigating a fingerprint mismatch rather than regenerating the file.

The instrumented runner also replays high-pass/envelope samples independently to
check candidate windows, min/max values, ARM/peak/COMMIT frames, independently
reconstructed callback median/post-hit/effective thresholds, gaps, reset,
discarded-commit IDs and all JNI slots. Kotlin unit tests cover batch stride,
64-bit raw frames vs corrected frames, legacy/unknown defaults, per-event vs
poll-peak serialization, all hit outcomes plus quiet, nulls and the event cap.

Android compile/unit/lint and native compilation are separate gates; this runner
does not build an APK, bump a version, access a device, or access the network.
