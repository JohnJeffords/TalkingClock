# Talking Clock (OSS)

A free, open-source **talking clock, talking timer, and stopwatch** for Android.
Speaks the time aloud at intervals you choose, announces timer milestones
game-style ("Five minutes remaining… five, four, three, two, one — time's up!"),
and does it all with a tiny APK, no trackers, no proprietary code, and almost no
permissions.

> **Status: design phase.** Nothing is implemented yet. This repo currently
> contains the design documents that will drive implementation.

## Who it's for

Built first for people with **time blindness, ADHD, or other memory issues** —
anyone who loses track of time while getting ready and ends up running late.
The core idea: you shouldn't have to *remember to look at a clock*; the clock
should keep quietly telling you, at a rhythm you choose, so time stays ambient
instead of invisible. Every feature gets judged against that person's morning.

## Learning project

The project owner is using this app to learn software development. That is a
**design constraint, not a footnote**: the codebase must be readable by a
relatively new coder browsing GitHub — self-documenting names, generous
comments (including places experienced devs wouldn't bother commenting),
boringly standard structure, and documentation complete enough that a new AI
agent or human contributor can pick up mid-project with zero chat history.
See [docs/CODE_STYLE.md](docs/CODE_STYLE.md) — binding for all contributors,
human or AI.

## Goals

1. **FOSS to the bone** — publishable on F-Droid with zero anti-features:
   no closed-source binaries, no Google Play Services, no analytics, no
   network access at all. License: **GPL-3.0-or-later**.
2. **Tiny and smooth** — release APK budget **≤ 4 MB**, steady 60 fps,
   near-zero battery cost when idle.
3. **Minimal permissions** — no dangerous permissions except an *optional*
   `POST_NOTIFICATIONS` prompt (Android 13+) so the timer's foreground-service
   notification is visible. No internet, no storage, no location, nothing else.
4. **Works on de-Googled Android** — first-class support for GrapheneOS and
   CalyxOS, including graceful handling of the "no TTS engine installed" case.
5. **All assets open** — icon, fonts, and any sounds under OFL / CC0 / GPL,
   tracked with the [REUSE](https://reuse.software/) spec.
6. **Readable by a beginner** — see "Learning project" above.

## The three screens

| Screen | One-liner |
|---|---|
| **Clock** | Big digital clock with seconds; pick a speak interval (15 s … 1 h, or custom) and it announces the time aloud. |
| **Talking Timer** | Type a duration (last one is remembered), pick an announcement schedule, and get game-announcer-style spoken milestones; one active timer at a time. |
| **Stopwatch** | Count-up with laps; optional spoken interval and lap announcements. |

Full UX in [docs/DESIGN.md](docs/DESIGN.md).

## Documentation map

| Doc | Contents |
|---|---|
| [CLAUDE.md](CLAUDE.md) | Entry point for AI agents: read order, hard rules, verification commands |
| [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) | Build order (M0–M8) with per-milestone definition of done + dev-env setup |
| [docs/DESIGN.md](docs/DESIGN.md) | Product design: screens, settings, announcement phrasing, feature list |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Tech stack, code structure, timekeeping rules, performance & size budgets |
| [docs/CODE_STYLE.md](docs/CODE_STYLE.md) | Coding rules: learning-first commenting, naming, and the "least code" ladder |
| [docs/VOICE_PACKS.md](docs/VOICE_PACKS.md) | Spec for user-supplied recorded-voice packs (stitched clips, no AI) |
| [docs/TESTING_AND_CI.md](docs/TESTING_AND_CI.md) | Automated per-PR testing across Android versions, de-Googled testing, F-Droid compliance, AI code review |
| [docs/PUBLISHING.md](docs/PUBLISHING.md) | Play Store + F-Droid submission requirements and checklists |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decision log (what was decided, when, and why) |
| [docs/CLAUDE_DESIGN_PROMPT.md](docs/CLAUDE_DESIGN_PROMPT.md) | Ready-to-paste prompt for mocking up the UI in Claude Design |

## Key pitfalls we're designing around

- **GrapheneOS/CalyxOS ship no TTS engine.** Google TTS is proprietary and
  absent there. The app detects a missing/broken engine and walks the user
  through installing a FOSS one (RHVoice, eSpeak NG — both on F-Droid).
  We deliberately do **not** bundle a TTS engine into the app (see D-011 in
  the decision log).
- **Timers must never use the wall clock.** Wall-clock time jumps (NTP, time
  zones, manual changes). Timer & stopwatch run on `elapsedRealtime()`.
- **A ticking clock can be a battery/framerate trap.** One state update per
  second, aligned to the second boundary — never a per-frame loop while idle.
- **Play Console personal accounts (created after Nov 2023) must run a closed
  test with 12 testers for 14 days** before production release. Plan for it.

## Name & package id

Official name: **Talking Clock (OSS)** (decided 2026-07-15).
Repository: <https://github.com/JohnJeffords/TalkingClock>.
Package id: `io.github.johnjeffords.talkingclock` (the `io.github.<username>`
convention lets F-Droid verify ownership trivially).

## License

GPL-3.0-or-later (add the `LICENSE` file from
<https://www.gnu.org/licenses/gpl-3.0.txt> when implementation starts).
