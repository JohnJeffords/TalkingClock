# Agent instructions — Talking Clock (OSS)

You are working on a FOSS Android talking clock/timer/stopwatch app. The
project owner is **learning software development on this codebase** — that
shapes everything you write here.

## Read these before writing any code

1. [docs/CODE_STYLE.md](docs/CODE_STYLE.md) — **binding.** Least-code ladder,
   beginner-readable naming, comment density deliberately above industry norm.
2. [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) — build order,
   current status, per-milestone definition of done. **Start here to find the
   next task.**
3. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — tech stack, package layout,
   the timekeeping rules (monotonic vs wall clock — correctness-critical).
4. [docs/DESIGN.md](docs/DESIGN.md) — the product spec for all screens.
5. [docs/DECISIONS.md](docs/DECISIONS.md) — why things are the way they are.
   New decisions go here **in the same PR** that implements them.

Specs consulted when relevant: [docs/VOICE_PACKS.md](docs/VOICE_PACKS.md),
[docs/TESTING_AND_CI.md](docs/TESTING_AND_CI.md),
[docs/PUBLISHING.md](docs/PUBLISHING.md).

## Hard rules (CI enforces most of these)

- **Permissions:** exactly `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`. **Never add
  `INTERNET`** or anything else.
- **Dependencies:** AndroidX + Kotlin/coroutines only. No Google Play
  Services, Firebase, analytics, or crash reporting. Every new dependency
  needs a license check and a size justification in the PR.
- **Timer/stopwatch use `SystemClock.elapsedRealtime()`, never wall clock.**
  Clock display uses wall clock. See ARCHITECTURE.md → Timekeeping rules.
- **Release APK ≤ 4 MB.** Check before adding anything heavy.
- **Comments and names are for a beginner reading on GitHub.** If an
  experienced dev "wouldn't bother" commenting it, comment it anyway
  (per CODE_STYLE.md).
- **Docs update in the same PR** as the change they describe. A PR that
  makes DESIGN/ARCHITECTURE/DECISIONS stale is incomplete.
- Every behavior change ships with a test; bug fixes ship with the test
  that would have caught them.
- Conventional commits (`feat:`, `fix:`, `docs:`, `test:`); explain *why*
  in the body.

## Project facts

- App name: **Talking Clock (OSS)** · applicationId
  `io.github.johnjeffords.talkingclock` · GPL-3.0-or-later
- minSdk 26, targetSdk = latest stable (35 as of 2026-07; track Play policy)
- Kotlin + Jetpack Compose (Material 3), single module, manual DI, Gradle
- Repo: https://github.com/JohnJeffords/TalkingClock
- Target user: people with time blindness / ADHD — announcements are the
  point, ≤ 2 taps to arm, never silently stop announcing.

## Verifying your work

- Unit + screenshot tests: `.\gradlew.bat testDebugUnitTest` (Windows host;
  `./gradlew testDebugUnitTest` in CI). Regenerate screenshot goldens after an
  intentional UI change with `-Proborazzi.test.record=true`.
- Android Lint: `.\gradlew.bat lintDebug`
- (Spotless/ktlint formatting is planned but not wired up yet — don't run
  `spotlessCheck` until it exists.)
- On-device: build `assembleDebug`, install via `adb install -r`, and
  exercise the changed flow on the owner's phone (see IMPLEMENTATION_PLAN.md
  → Dev environment). There is no local emulator by design; CI runs the
  emulator matrix.
