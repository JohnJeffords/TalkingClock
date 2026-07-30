# Talking Clock (OSS)

A free, open-source **talking clock, talking timer, and stopwatch** for Android.
Speaks the time aloud at intervals you choose, announces timer milestones
game-style ("Five minutes remaining… five, four, three, two, one — time's up!"),
and does it all with a tiny APK, no trackers, no proprietary code, and almost no
permissions.

> **Status: work in progress.** The basic app is created. The main thing I'm
> focused on right now is UX testing.

## Who it's for

Built first for people with **time blindness, ADHD, or other memory issues** —
anyone who loses track of time while getting ready and ends up running late.
The core idea: you shouldn't have to *remember to look at a clock*; the clock
should keep quietly telling you, at a rhythm you choose, so time stays ambient
instead of invisible. Every feature gets judged against that person's morning.

## Goals

1. **Designed for F-Droid** publishable on F-Droid with no anti-features such as:
   closed-source binaries, analytics, network access. No permissions will be needed
   except an optional notification prompt for when the talking clock is running.
2. **Works on de-Googled Android** — first-class support for GrapheneOS and
   CalyxOS, including graceful handling of the "no TTS engine installed" case.
3. **Tiny file size**
4. **All assets open** — icon, fonts, and any sounds under OFL / CC0 / GPL,
   tracked with the [REUSE](https://reuse.software/) spec.
5. **Code and documentation should be readable by a beginner** — see [docs/CODE_STYLE.md](docs/CODE_STYLE.md).

## The three screens

| Screen | One-liner |
|---|---|
| **Clock** | Big digital clock with seconds; pick a speak interval (15 s … 1 h, or custom) and it announces the time aloud. |
| **Alarm** | Still needs a description (WIP). |
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

- **Neither GrapheneOS or CalyxOS ship with a TTS engine.**
  Google TTS is proprietary and absent there.
  The app detects a missing/broken engine and walks the user
  through installing a FOSS one (RHVoice, eSpeak NG — both on F-Droid).
  We deliberately do **not** bundle a TTS engine into the app (see D-011 in
  the decision log).
- **Timers must never use the clock,** as the clock time jumps (NTP, time
  zones, manual changes). Timer & stopwatch run on `elapsedRealtime()`.
- **A ticking clock can be a battery/framerate trap.** One state update per
  second, aligned to the second boundary — never a per-frame loop while idle.

## Name & package id

Official name: **Talking Clock (OSS)**.
Repository: <https://github.com/JohnJeffords/TalkingClock>.
Package id: `io.github.johnjeffords.talkingclock` (the `io.github.<username>`
convention lets F-Droid verify ownership trivially).

## License
Todo: Fix this section:
GPL-3.0-or-later (add the `LICENSE` file from
<https://www.gnu.org/licenses/gpl-3.0.txt>).
