# Talking Clock (working title)

A free, open-source **talking clock, talking timer, and stopwatch** for Android.
Speaks the time aloud at intervals you choose, announces timer milestones
game-style ("Five minutes remaining… five, four, three, two, one — time's up!"),
and does it all with a tiny APK, no trackers, no proprietary code, and almost no
permissions.

> **Status: design phase.** Nothing is implemented yet. This repo currently
> contains the design documents that will drive implementation.

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

## The three screens

| Screen | One-liner |
|---|---|
| **Clock** | Big digital clock with seconds; pick a speak interval (15 s … 1 h, or custom) and it announces the time aloud. |
| **Talking Timer** | Countdown with game-announcer-style spoken milestones; one active timer at a time. |
| **Stopwatch** | Count-up with laps; optional spoken interval and lap announcements. |

Full UX in [docs/DESIGN.md](docs/DESIGN.md).

## Documentation map

| Doc | Contents |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | Product design: screens, settings, announcement phrasing, feature list |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Tech stack, code structure, timekeeping rules, performance & size budgets |
| [docs/VOICE_PACKS.md](docs/VOICE_PACKS.md) | Spec for user-supplied recorded-voice packs (stitched clips, no AI) |
| [docs/TESTING_AND_CI.md](docs/TESTING_AND_CI.md) | Automated per-PR testing across Android versions, de-Googled testing, F-Droid compliance checks |
| [docs/PUBLISHING.md](docs/PUBLISHING.md) | Play Store + F-Droid submission requirements and checklists |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decision log (what was decided, when, and why) |

## Key pitfalls we're designing around

- **GrapheneOS/CalyxOS ship no TTS engine.** Google TTS is proprietary and
  absent there. The app detects a missing/broken engine and walks the user
  through installing a FOSS one (RHVoice, eSpeak NG — both on F-Droid).
- **Timers must never use the wall clock.** Wall-clock time jumps (NTP, time
  zones, manual changes). Timer & stopwatch run on `elapsedRealtime()`.
- **A ticking clock can be a battery/framerate trap.** One state update per
  second, aligned to the second boundary — never a per-frame loop while idle.
- **Play Console personal accounts (created after Nov 2023) must run a closed
  test with 12 testers for 14 days** before production release. Plan for it.
- **This folder lives in OneDrive.** Fine for design docs; before
  implementation the repo should move (or be cloned) outside OneDrive —
  Gradle build churn + OneDrive sync = file-lock pain and wasted upload.

## Name candidates

"Talking Clock" is the working title. Candidates to decide before publishing
(check F-Droid/Play for collisions first): **TimeTeller**, **ChronoVox**,
**SpokenTime**, **TickTeller**. Package id will be
`io.github.<github-username>.talkingclock` (fill in when the GitHub repo is
created — this convention lets F-Droid verify ownership trivially).

## License

GPL-3.0-or-later (add the `LICENSE` file from
<https://www.gnu.org/licenses/gpl-3.0.txt> when implementation starts).
