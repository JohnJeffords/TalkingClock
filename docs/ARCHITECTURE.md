# Architecture

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin (JVM target 17) | Android default; best tooling |
| UI | Jetpack Compose + Material 3 | Modern, testable, fully FOSS (AndroidX is Apache-2.0) |
| Min / target SDK | minSdk **26** (Android 8.0), targetSdk = latest stable (35 now, track yearly Play requirement) | ~98 % device coverage; small CI matrix |
| State | ViewModel + `StateFlow`, unidirectional data flow | Plain and testable |
| Persistence | Preferences DataStore (settings, presets), plain files in app storage (voice packs) | No DB needed; Room would be dead weight |
| DI | Manual (one `AppContainer` created in `Application`) | Hilt/Koin add build time, APK size, and magic this app doesn't need |
| Background | One foreground service (`AnnouncerService`) | See below |
| Build | Gradle (Kotlin DSL), version catalog, R8 + resource shrinking | Reproducible, small |
| Nav | Compose Navigation, 3 destinations + settings | Boring on purpose |

**Dependency policy (F-Droid-critical):** AndroidX + Kotlin/coroutines only.
No Play Services, no Firebase, no crash reporting SDK, no ad/analytics SDK, no
update checkers, no JitPack-hosted mystery libs. Every new dependency needs a
license check (Apache/MIT/BSD-compatible) and a size justification. Gradle
**dependency verification** (checksums) is enabled so CI fails on tampered or
swapped artifacts.

## Package layout (single module)

```
app/src/main/kotlin/<appid>/
  TalkingClockApp.kt        // Application: builds AppContainer
  MainActivity.kt           // single activity, hosts Compose nav
  ui/                       // screens & shared components
    clock/   ClockScreen.kt, ClockViewModel.kt
    timer/   TimerScreen.kt, TimerViewModel.kt
    stopwatch/ StopwatchScreen.kt, StopwatchViewModel.kt
    settings/ SettingsScreen.kt, SettingsViewModel.kt
    theme/
  domain/                   // pure Kotlin, zero Android imports — fully unit-testable
    time/    TickSource.kt, TimeFormatter.kt
    speech/  Phrasebook.kt          // instant -> "It is 2:23 PM" (per style/locale)
    timer/   TimerEngine.kt, AnnouncementSchedule.kt
    stopwatch/ StopwatchEngine.kt
  speech/                   // Android TTS + voice packs behind one interface
    Speaker.kt              // interface: speak(Utterance), warmUp(), state: Flow
    TtsSpeaker.kt           // android.speech.tts.TextToSpeech impl
    VoicePackSpeaker.kt     // stitched-clip playback (see VOICE_PACKS.md)
    VoicePackStore.kt       // import/validate/list packs (SAF, app-private files)
  service/
    AnnouncerService.kt     // foreground service; owns engines while backgrounded
  data/
    SettingsRepository.kt   // DataStore
```

The `domain/` package is the heart: timer math, checkpoint scheduling, and
phrase generation are **pure functions of (state, now)** with no Android
classes, so they get exhaustive JVM unit tests with fake clocks.

## Timekeeping rules (the correctness core)

1. **Clock display/announcements** use wall-clock time (`System.currentTimeMillis`
   via a `Clock` abstraction) — they must follow time-zone and NTP changes.
   Listen for `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` broadcasts
   (runtime-registered, no manifest receiver) to re-align instantly.
2. **Timer and stopwatch NEVER use wall-clock time.** They store anchor points
   from `SystemClock.elapsedRealtime()` (monotonic, ticks through Doze, ignores
   clock changes). State is `{anchorElapsed, accumulatedMs, isRunning}` — pause
   and resume are pure arithmetic; there is no accumulating drift because
   nothing is "counted", elapsed time is always re-derived.
3. **Ticking**: a coroutine computes `delay(msUntilNextSecondBoundary)` each
   loop — self-correcting alignment, one state emission per second. The
   stopwatch's sub-second digits are driven by `withFrameNanos` **only while
   running AND the screen is visible**; everything else in the app updates at
   1 Hz.
4. Timer/stopwatch state is persisted (DataStore) on every transition, so
   process death (or a reboot, for the stopwatch's paused state) restores
   correctly. After reboot a *running* timer can't be resurrected honestly
   (elapsedRealtime reset), so it restores as paused-at-last-known with a
   "was interrupted by reboot" note. No exact-alarm APIs — that keeps us out
   of `SCHEDULE_EXACT_ALARM` policy land entirely.

## AnnouncerService (foreground service)

One service for all three features, started only when something needs to
outlive the UI (speak interval active, timer running, stopwatch running with
announcements). Stops itself when nothing does — **the notification only
exists while something is genuinely running.**

- Type: `mediaPlayback` (declared in manifest; API 34+ requires a typed FGS,
  and spoken audio is exactly what this type is for).
- Manifest permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
  Runtime: `POST_NOTIFICATIONS` requested with an explanation screen the first
  time the user starts something backgroundable. **If denied, the service
  still runs and still speaks** (FGS doesn't require the permission to run on
  any supported API level) — the user just won't see the notification; the
  app shows an in-app banner explaining that, and never nags more than once.
- Notification: "Speaking clock · every 5 min" / "Timer · 12:34 remaining
  (updated at ~1 Hz max, throttled to 1/30 s when battery saver is on)" with
  Pause/Stop actions. Channel per feature so users can silence what they want.
- Engines (`TimerEngine`, tick loops) live in the service when it's running
  and are observed by ViewModels; single source of truth either way, handed
  off via the shared `AppContainer`.

## Speech pipeline

```
Phrasebook (pure text)  →  Speaker interface  →  TtsSpeaker (system TTS)
                                              ↘  VoicePackSpeaker (stitched clips)
```

- **Init is async and can fail.** `TtsSpeaker` exposes
  `state: Flow<SpeakerState>` = `Initializing / Ready / NoEngine / Error`.
  UI reacts: the speak controls show a spinner during init and the
  "install a FOSS TTS engine" card on `NoEngine` (the GrapheneOS/CalyxOS
  default state).
- **Latency discipline**: engine warm-up (`speak("")`-style priming) when a
  speaking feature is armed; countdown numbers are queued as separate
  utterances ahead of time with `QUEUE_ADD` so 5-4-3-2-1 lands on the beat.
  If an utterance would start > 1.5 s late (engine hiccup), it's dropped —
  a talking clock that lies about the time is worse than one that skips.
- **Audio focus**: request `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` before
  speaking, abandon after. Output on `STREAM_MUSIC`. If media volume is 0
  when the user arms a speaking feature, show a one-time "media volume is
  muted" hint (cheap check, saves the #1 support question).
- Utterance priority: timer countdown > timer checkpoint > clock interval >
  stopwatch. Lower-priority utterances that collide are dropped, not queued
  (stale time announcements must never pile up).

## Performance & size budgets (enforced, not aspirational)

- **APK ≤ 4 MB** release (R8 full mode + `shrinkResources`; vector drawables
  only; DSEG font subset to digits+colon; no ABI splits needed — pure
  Kotlin/no native code means one universal APK). CI posts APK size per PR
  and fails on > 4 MB or a > 100 KB unexplained jump.
- **1 Hz recomposition when idle on the clock screen** — verified with a
  Macrobenchmark/JankStats check in CI; the seconds text is its own
  composable reading a `State<String>` so a tick recomposes ~one text node,
  not the screen.
- Battery: no wakelocks except the FGS while running; no polling loops; the
  app process does exactly nothing when nothing is armed.

## Coding practices

- **CODE_STYLE.md governs everything here** — learning-first naming and
  comment density, the least-code ladder, and the docs-in-same-PR rule.
- Kotlin official style, enforced by **ktlint** (via Spotless) in CI; static
  analysis with Android Lint on `fatal` for correctness categories.
- `domain/` stays Android-free (enforced by a lint/Konsist check) — this is
  what keeps the test suite fast and honest.
- Strings externalized from day one; `localeConfig` declared (Android 13
  per-app language support comes free).
- Every PR: build + unit tests + lint + screenshot diff + (merge-blocking)
  emulator smoke suite — see TESTING_AND_CI.md.
- Conventional commits; versionCode monotonic and set in one place (F-Droid
  builds off git tags).
