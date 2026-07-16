# Testing & CI

Goal: every PR proves, automatically, that the app (1) still works across
Android versions, (2) still works on de-Googled Android (GrapheneOS/CalyxOS
conditions), and (3) still passes F-Droid's FOSS bar. Designed so an AI coding
agent can read a red check and know exactly what to fix.

## Test pyramid

### 1. JVM unit tests (seconds, run on every push)
- All of `domain/` — timer math, checkpoint scheduling, phrase generation —
  against **fake clocks** (`kotlinx-coroutines-test` virtual time). This is
  where time bugs die: DST transitions, midnight rollover, pause/resume
  drift, checkpoint sets longer than the timer, 12/24 h phrasing, leap
  seconds-adjacent formatting, etc.
- Voice-pack manifest parsing/validation and token-composition tables per
  language.

### 2. Robolectric (fast Android-ish, every push)
- `TtsSpeaker` state machine against shadowed `TextToSpeech` (init success,
  init failure, **engine absent**, engine dying mid-utterance).
- `AnnouncerService` lifecycle: starts when armed, stops when idle,
  notification content, behavior when `POST_NOTIFICATIONS` denied.
- DataStore persistence round-trips (process-death restore).

### 3. Screenshot tests — **Roborazzi** (every push)
- Every screen × light/dark/black themes × font scale 100 %/200 % ×
  12 h/24 h. Runs on the JVM (no emulator), diffs committed goldens; catches
  layout regressions and the "clock shimmies when digits change" class of bug.

### 4. Instrumented tests on emulators (merge-blocking matrix)
GitHub Actions + `reactivecircus/android-emulator-runner`, KVM-enabled Linux
runners:

| API | Image | Why |
|---|---|---|
| 26 | `default` (AOSP, **no Google APIs**) | minSdk floor |
| 30 | `aosp_atd` | mid-range, fast ATD image |
| 33 | `aosp_atd` | POST_NOTIFICATIONS + per-app language era |
| 35/36 | `default` AOSP | targetSdk, FGS-type enforcement |

Key points:
- **AOSP images without Google APIs are the de-Googled proxy** (see below).
- Compose UI tests: navigation, dropdown intervals, timer start→checkpoint→
  countdown→end (with a fake `Speaker` recording utterances — asserting on
  *actual audio* is flaky; we assert the utterance stream, which is our own
  seam), stopwatch laps, settings persistence, process-death recovery
  (`am kill` + relaunch).
- **TTS-absent path** (default state of these images): assert the "install a
  FOSS engine" card appears and nothing crashes.
- **TTS-present path**: one matrix leg installs **eSpeak NG** (F-Droid APK,
  pinned version + SHA-256, cached) via adb before tests, then asserts real
  `TextToSpeech` init reaches `Ready` and an utterance completes. This is the
  exact RHVoice/eSpeak-on-GrapheneOS user path.
- Full matrix runs on PRs touching `src/`; a nightly cron runs the matrix
  plus longer soak tests (30-min timer fast-forwarded via debug hooks).

### 5. Performance gates
- Macrobenchmark job (nightly + on-demand label): startup time, and JankStats
  on the clock screen for 60 s — fails if frame-drop rate exceeds threshold
  or if idle recomposition exceeds ~1/s.
- APK size check on every PR: build `assembleRelease` (unsigned), comment the
  size delta, **fail > 4 MB** or unexplained +100 KB.

## GrapheneOS / CalyxOS strategy (honest version)

Neither project publishes CI-consumable emulator images, so "run CI on
GrapheneOS" isn't realistically automatable. What actually matters — and what
we CAN automate — is the set of conditions those OSes create:

1. **No Google Play Services** → covered: our emulator images have no GMS,
   and a static CI check greps merged manifest + dependency tree for any
   `com.google.android.gms`/Firebase reference (fails the build if found).
2. **No TTS engine installed** → covered explicitly (see above).
3. **FOSS TTS engines only (RHVoice/eSpeak NG)** → covered by the eSpeak leg.
4. **Aggressive permission UX / sensors off** → we request almost nothing;
   the POST_NOTIFICATIONS-denied path is tested in Robolectric + emulator.
5. **Exploit-hardened runtime (hardened malloc etc., GrapheneOS)** → mostly
   affects native code; **we ship none**. CI asserts the APK contains no
   `lib/` directory, which is the strongest automatable guarantee here.

Plus: a `TESTING.md` manual smoke checklist (10 min) for a real
GrapheneOS/CalyxOS device before each release tag — realistically you or a
tester on the F-Droid forum runs this; the checklist makes it delegable.

## F-Droid compliance, automated per PR

A dedicated `fdroid-compliance` CI job:

1. **`fdroid scanner`** (from `fdroidserver`, runs in their docker image) on
   the source tree + built APK — the same scanner F-Droid runs; catches
   binary blobs, known proprietary signatures, prebuilt jars.
2. **Dependency license audit** — Gradle license report checked against an
   allowlist (Apache-2.0/MIT/BSD/EPL…); unknown license = red.
3. **Gradle dependency verification** — `verification-metadata.xml` checksums;
   new/changed artifacts must be explicitly re-pinned in the PR.
4. **REUSE lint** (`reuse lint`) — every file, including icons/fonts/audio,
   carries SPDX license + copyright info. This is how "all art assets are
   open" stays true instead of aspirational.
5. **Manifest guard** — fails if the permission set ever grows beyond the
   approved list (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
   `POST_NOTIFICATIONS`) or if `INTERNET` ever appears. The absence of
   INTERNET is our privacy policy, enforced by CI.
6. **Reproducible-build check** (nightly, not per-PR): build twice, diff APKs
   (`diffoscope`); reproducibility is what lets F-Droid publish with our own
   signature later.

## Workflow layout

```
.github/workflows/
  pr.yml        # unit + robolectric + roborazzi + lint/ktlint + apk-size + fdroid-compliance
  emulator.yml  # instrumented matrix (PR label / merge-queue + nightly)
  nightly.yml   # full matrix, macrobenchmark, reproducible-build diff, soak
  release.yml   # tag → build, checklist gate, artifacts for Play upload
```

Everything a bot can act on: checks output plain, single-cause failure
messages; goldens update via a `/update-screenshots` label job so an agent
can regenerate them in-PR after an intentional UI change.
