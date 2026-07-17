# Implementation Plan

Build order with a definition of done per milestone. Work top to bottom —
each milestone leaves the app shippable-ish (building, tested, honest).
Update the **Status** column in the same PR that changes it.

| # | Milestone | Status |
|---|---|---|
| M0 | Dev environment + project skeleton | in progress (2026-07-16) |
| M1 | Walking skeleton: ticking clock screen | in progress (2026-07-16) |
| M2 | Speech pipeline (TTS wrapper + phrasebook) | not started |
| M3 | Speaking clock: intervals + foreground service + auto-off | not started |
| M4 | Talking timer | not started |
| M5 | Stopwatch | not started |
| M6 | Settings + theming + accessibility (incl. speaking-style, quiet hours) | not started |
| M7 | Voice packs | not started |
| M7.5 | **Alarms** (list / edit / ringing + speaking-clock handoff) — adds exact-alarm + full-screen-intent permissions; see D-020. Owner veto point. | not started |
| M8 | Release prep (F-Droid + Play) | not started |

> Scope note (D-020): the design handoff expanded the app to four tools by
> adding Alarms, plus Quiet Hours and speaking-clock auto-off. Quiet Hours and
> auto-off fold into M3/M6; Alarms are their own late milestone (M7.5) because
> they widen the permission set. Screens are built from the amber/mono design
> tokens in the handoff (D-019).

CI grows *with* the code: the pr.yml basics (build, unit tests, ktlint,
manifest guard) land in M0; each later milestone adds its own tests to the
same pipeline. Don't defer CI to the end.

---

## Dev environment (Windows host, no Android Studio — see D-018)

One-time setup, roughly:

```powershell
# 1. JDK 17 (Temurin)
winget install EclipseAdoptium.Temurin.17.JDK

# 2. Android command-line tools -> C:\Android\cmdline-tools\latest
#    Download "commandlinetools-win" from https://developer.android.com/studio#command-line-tools-only
#    then unzip so that bin/ sits at C:\Android\cmdline-tools\latest\bin

# 3. SDK packages (accept licenses when prompted)
setx ANDROID_HOME C:\Android
C:\Android\cmdline-tools\latest\bin\sdkmanager.bat "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# 4. Verify
C:\Android\platform-tools\adb.exe version
```

Gradle itself comes from the checked-in wrapper (`gradlew.bat`) — no install.

**Device testing:** enable Developer options on the phone (tap Build number
7×), turn on USB debugging (or Wireless debugging, Android 11+), then
`adb install -r app\build\outputs\apk\debug\app-debug.apk` and
`adb logcat -s TalkingClock` for logs. No local emulator by design — CI
runs the emulator matrix (TESTING_AND_CI.md).

---

## M0 — Dev environment + project skeleton

Scaffold the standard Android/Gradle project: settings/build files (Kotlin
DSL, version catalog), `app/` module with the package layout from
ARCHITECTURE.md, empty `MainActivity` + Compose theme, `.editorconfig`,
ktlint via Spotless, `LICENSE` (GPL-3.0 text from gnu.org), REUSE headers.
CI: `pr.yml` with build + unit tests + ktlint + the manifest guard
(permission allowlist, no INTERNET) + APK size check; `ai-review.yml`.

**Done when:** `gradlew assembleDebug test lint` passes locally and in CI on
a PR; the debug APK installs and shows a blank themed screen on the owner's
phone; release APK size is reported in the PR.

## M1 — Walking skeleton: ticking clock screen

Bottom nav with three destinations (Timer/Stopwatch as placeholders), Clock
screen showing date + HH:MM:SS with tabular digits, ticking via the
second-boundary-aligned coroutine (ARCHITECTURE.md → Timekeeping), 12/24 h
from system setting, time-change broadcast handling.

**Done when:** clock ticks visibly at 1 Hz with ~one text-node recomposition
per tick (verify with Layout Inspector or recomposition counts in a debug
overlay); domain unit tests cover formatting incl. midnight rollover and
12/24 h; screenshot tests (Roborazzi) cover the screen in light/dark.

## M2 — Speech pipeline

`domain/speech/Phrasebook` (instant → utterance text, Digits style),
`Speaker` interface, `TtsSpeaker` with the async-init state machine
(`Initializing/Ready/NoEngine/Error`), audio focus (transient may-duck),
"tap the clock to speak" wired up, no-engine warning card with
RHVoice/eSpeak NG guidance.

**Done when:** tapping the clock speaks the time on a real device; pulling
the TTS engine (or testing on an engine-less emulator in CI) shows the
guidance card instead of crashing; Robolectric covers the state machine;
Phrasebook is exhaustively unit-tested.

## M3 — Speaking clock: intervals + foreground service

Speak-interval dropdown (Off/15s/20s/1m/5m/10m/20m/30m/1h/last-custom/
Custom…), wall-clock-boundary alignment, `AnnouncerService` (mediaPlayback
FGS) so speaking continues screen-off, POST_NOTIFICATIONS request with the
explainer + denied-path handling, notification with Stop action,
keep-screen-awake toggle.

**Done when:** a 1-minute interval keeps announcing with the screen off for
10+ minutes on a real device; the service stops itself (and the notification
disappears) when set to Off; denied-notification path verified; emulator.yml
matrix added to CI with the TTS-absent and eSpeak-installed legs.

## M4 — Talking timer

`TimerEngine` on elapsedRealtime, typed duration field with last-used
persistence, announcement schedules (Game style/Minimal/Frequent + editor),
checkpoints incl. halfway ("<time> remaining. Halfway there."), pre-queued
5-4-3-2-1 countdown, end state + chime + optional overtime, runs in the
FGS, process-death restore, per-schedule toggles.

**Done when:** a 2-minute Game-style timer announces every checkpoint with
the screen off and lands "Time's up" within ±1 s (stopwatch-in-hand test);
domain tests cover schedule math edge cases (checkpoints ≥ duration, pause/
resume, restore); UI tests cover the start→announce→end flow with a fake
Speaker.

## M5 — Stopwatch

`StopwatchEngine` on elapsedRealtime, tenths display driven by frame clock
only while running+visible, laps (lap + cumulative), optional interval/lap
announcements, copy-to-clipboard, shares the FGS, timer-wins announcement
priority.

**Done when:** stopwatch survives process death and screen-off correctly;
lap list matches manual timing; announcement-collision rule verified in a
unit test; jank check on the running stopwatch passes.

## M6 — Settings + theming + accessibility

Full settings screen per DESIGN.md §4 (DataStore-backed), themes incl.
AMOLED black, DSEG seven-segment option (subset font), night dim + burn-in
shift, haptics, hourly chime, About screen, `localeConfig`, TalkBack pass
(ticking clock is liveRegion=none), font-scale-200% pass.

**Done when:** every DESIGN.md setting exists and persists across restart;
screenshot matrix covers themes × font scales; a TalkBack walkthrough of
all three screens works (manual checklist in TESTING.md).

## M7 — Voice packs

VOICE_PACKS.md v1: SAF import, zip+manifest validation with coverage
report, `VoicePackSpeaker` (SoundPool stitching, gapMs), composable number
atoms, whole-utterance TTS fallback, voice source picker, pack-author
template folder in the repo.

**Done when:** a hand-made test pack (checked into `app/src/test/resources`)
plays a stitched time announcement on-device; a pack missing tokens falls
back cleanly per-utterance; validation rejects packs without a license.

## M8 — Release prep

Fastlane metadata (title, descriptions, changelogs, CI-generated
screenshots), icon + feature graphic (REUSE-tracked, open-licensed),
privacy policy page (GitHub Pages), signed release config, reproducible-
build check green, `fdroid build` passes in their container, versionCode
scheme wired to tags, TESTING.md manual GrapheneOS checklist, Play closed
test kicked off (12 testers × 14 days), fdroiddata MR drafted.

**Done when:** a tagged release builds a signed APK ≤ 4 MB reproducibly,
`fdroid build` succeeds locally, and the Play closed test is running.

---

## Out of scope for v1

See DESIGN.md → "Feature ideas parked for later" (widgets, alarms, Wear,
chess clock). Don't build these; don't design around them either.
