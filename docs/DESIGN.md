# Product Design

Three screens, one navigation drawer ("sandwich"/hamburger menu), one settings
screen. Material 3, dynamic color where available, dark theme by default with a
true-black (AMOLED) option.

Navigation: bottom navigation bar with three destinations — **Clock**,
**Timer**, **Stopwatch** — plus a hamburger icon in the top app bar opening a
drawer with **Settings** and **About**. (Bottom nav beats swipe-only paging for
accessibility and one-handed use; swiping between the three screens is also
supported as a bonus, not the only path.)

---

## 1. Clock screen

```
┌──────────────────────────────┐
│ ☰  Clock                     │
│                              │
│         Tue, Jul 15          │
│                              │
│        14:23:07              │   ← tap the clock = speak time once
│                              │
│  ┌────────────────────────┐  │
│  │ 🔊 Speak every: 5 min ▾ │  │   ← dropdown
│  └────────────────────────┘  │
│                              │
│  [Clock] [Timer] [Stopwatch] │
└──────────────────────────────┘
```

- **Digital clock with seconds**, huge tabular-lining digits so the layout
  doesn't shimmy as digits change. Optional seven-segment style via the DSEG
  font (OFL-licensed). 12/24 h follows the system by default, overridable in
  settings.
- **Date line** above the clock (toggleable).
- **Tap the clock to speak the time immediately.** Cheap, discoverable, and
  makes the app useful even with the interval off.
- **Speak-interval dropdown** — the core feature. Options:
  `Off · 15 s · 20 s · 1 min · 5 min · 10 min · 20 min · 30 min · 1 h ·
  <last-used custom> · Custom…`
  - "Custom…" opens a duration picker; the chosen value becomes the
    "last-used custom" slot (also editable in Settings).
  - Announcements are **aligned to wall-clock boundaries**, not to when you
    pressed the button: "every 5 min" speaks at :00, :05, :10 … and "every
    15 s" at :00/:15/:30/:45. Predictable, and it means the spoken time is
    always a round number.
  - While an interval is active the dropdown button shows a subtle pulse and
    the drawer icon gets a badge, so an active speaking clock is never a
    mystery.
- **Speaking continues with the screen off** via the announcer foreground
  service (see ARCHITECTURE.md). A persistent notification shows "Speaking
  clock: every 5 min" with a Stop action.
- **Keep screen awake** quick-toggle (moon icon in the top bar) for
  nightstand/desk use — `FLAG_KEEP_SCREEN_ON`, no permission needed.

### Spoken phrasing (clock)

Two styles, chosen in Settings:

- **Digits** (default): "2:23 PM" / "14:23". With *speak seconds* enabled:
  "14:23 and 7 seconds".
- **Natural**: "twenty-three past two" (locale-aware only for languages we
  explicitly support; falls back to Digits elsewhere).

Rule: the announcement text is generated **at utterance start** and describes
that instant; if TTS is still initializing we skip rather than speak a stale
time.

---

## 2. Talking Timer screen

```
┌──────────────────────────────┐
│ ☰  Timer                     │
│                              │
│         12:34                │   ← remaining, big
│      ▁▁▁▁▁▁▁▂▂▂▂            │   ← thin progress ring/bar
│                              │
│   [▶ Start]   [⟲ Reset]      │
│                              │
│  Presets: 1m 3m 5m 10m 15m + │   ← chips; + = new custom timer
│                              │
│  Announcements: Game style ▾ │
│                              │
│  [Clock] [Timer] [Stopwatch] │
└──────────────────────────────┘
```

- **One active timer at a time** (by design — overlapping talking timers are
  chaos). Starting a new one stops the current one after a confirm-snackbar
  with Undo.
- Controls: **Start/Pause** (same button), **Reset**, **preset chips**, and
  **“+” to create a new timer** (duration picker; saved as a reusable preset).
- Presets are editable: long-press a chip to rename/edit/delete.
- Runs in the foreground service; survives app swipe-away and screen-off. The
  notification shows remaining time with Pause/Reset actions.
- When the timer ends: spoken "Time's up", a repeating chime for up to 60 s
  (configurable), full-screen state on the Timer screen. Optional **overtime
  mode**: keep counting up past zero (shown red, "+0:37") and announce
  overtime minutes.

### Announcement schedules (the game-announcer part)

A schedule is a set of *checkpoints* (remaining-time marks that get spoken)
plus a *final countdown*. Three built-ins, plus per-schedule editing:

| Schedule | Checkpoints | Final countdown |
|---|---|---|
| **Game style** (default, UT/TF2-inspired) | 30 m, 20 m, 10 m, 5 m, 3 m, 2 m, 1 m, 30 s, 20 s, 10 s (only those below the timer's duration) | 5-4-3-2-1 |
| **Minimal** | halfway, 1 m | 3-2-1 |
| **Frequent** | every minute, then 30 s, 10 s | 10 → 1 |

Spoken as "Five minutes remaining", "Thirty seconds remaining", then bare
numbers for the countdown, then "Time's up". A start announcement ("Timer
started: fifteen minutes") is a toggle. A **"halfway there"** checkpoint is
available in the editor because it's fun.

Checkpoints fire on the announcer service's second-tick, so they're accurate
to well under a second — good enough for speech, which itself takes ~1 s to
begin. For the 5-4-3-2-1 countdown we pre-synthesize/queue the numbers so the
cadence stays tight (see ARCHITECTURE.md → TTS latency).

Voice: system TTS by default; a **voice pack** (recorded human clips, stitched
— see VOICE_PACKS.md) can be selected per the whole app, which is where the
TF2-announcer fantasy really lands.

---

## 3. Stopwatch screen

Deliberately the simplest screen.

```
┌──────────────────────────────┐
│ ☰  Stopwatch                 │
│                              │
│       00:04:37.2             │
│                              │
│   [▶ Start]  [⚑ Lap]  [⟲]    │
│                              │
│   Lap 3   01:02.1   04:37.2  │
│   Lap 2   01:44.8   03:35.1  │
│   Lap 1   01:50.3   01:50.3  │
│                              │
│  [Clock] [Timer] [Stopwatch] │
└──────────────────────────────┘
```

- Counts up from zero; displays `HH:MM:SS.t` (tenths — hundredths are noise at
  a glance and cost framerate; a setting can switch precision to hundredths,
  which then only render while paused… kidding: they render live, but the
  *state* still updates per display frame only while running and visible).
- **Start/Pause**, **Lap**, **Reset** (Reset only when paused, to prevent
  fat-finger loss; long-press to force).
- Lap list shows lap time + cumulative time, newest on top. Copy-to-clipboard
  on long-press of the list (no permission needed).
- **Spoken options** (both off by default):
  - *Announce every N* — 30 s / 1 min / 5 min ("Five minutes").
  - *Speak lap on Lap press* — "Lap three: one minute two seconds".
- Runs in the same foreground service when backgrounded, so it keeps counting
  and (optionally) speaking with the screen off.
- Timer and stopwatch can run at the same time (they share the service, and a
  stopwatch is passive enough that this doesn't create announcement chaos —
  if both want to speak at once, timer wins, stopwatch drops that line).

---

## 4. Settings (via hamburger drawer)

Grouped, searchable-by-eyeball, one screen with sections:

**Clock**
- Time format: System / 12 h / 24 h
- Show seconds (on) · Show date (on)
- Clock style: Default / Seven-segment (DSEG)
- Speaking style: Digits / Natural · Speak seconds when speaking time (off)
- Custom speak interval (edits the "last-used custom" dropdown slot)
- Hourly chime: Off / Chime / Speak the hour (a classic; aligned to the hour)

**Timer**
- Manage presets · Manage announcement schedules (edit checkpoints)
- Start announcement (on) · End-of-timer sound: Speech only / Chime / Both
- Overtime mode (off)

**Stopwatch**
- Precision: Tenths / Hundredths
- Announce every: Off / 30 s / 1 min / 5 min · Speak laps (off)

**Voice**
- Voice source: System TTS / <installed voice packs>
- TTS engine picker (deep-links to system TTS settings) · Voice/locale
- Speech rate · Pitch · **Test button** ("It is 2:23 PM")
- Audio output: Media stream (default) · Duck other audio while speaking (on)
- Import voice pack… (opens system file picker — no storage permission)
- ⚠ Inline warning card when no TTS engine is detected, with instructions +
  F-Droid links for RHVoice / eSpeak NG (rendered as text/QR — remember, the
  app itself has no network access)

**Display**
- Theme: System / Light / Dark / Black (AMOLED)
- Keep screen awake: Never / While speaking clock is on / Always on this screen
- Night dim + burn-in shift: in awake mode, dim to a red-tinted low-contrast
  clock and drift the layout a few pixels each minute

**Behavior**
- Haptic feedback (on)
- Notification explainer: a card that explains *why* the app shows a
  persistent notification (only while something is running) and re-requests
  `POST_NOTIFICATIONS` if it was denied — with a "the timer still works
  without it, you just can't see it from outside the app" honesty line.

**About**
- Version, license (GPL-3.0-or-later), source-code link, "no network, no
  analytics, no data collected" privacy statement, asset/font credits.

---

## Feature ideas parked for later (non-goals for v1)

- Home-screen widget (a per-second widget is a battery/API minefield; a
  minute-precision one is possible later via `TextClock`)
- Alarms (a whole different app + exact-alarm permission territory — keeping
  this app alarm-free is what keeps its permission story clean)
- Wear OS companion
- Per-app language picker UI (Android 13 gives this for free via
  `localeConfig`; we ship the manifest bit, just no custom UI)
- Chess-clock mode (two-player tap-to-pass — fun, but its own screen)

## Accessibility (not optional)

A talking clock's audience overlaps heavily with screen-reader users:

- Full TalkBack support: every control labeled, the clock exposes its value as
  text, live regions used sparingly (the ticking clock must NOT announce every
  second on TalkBack — it's marked `liveRegion = none`; users tap to hear it).
- Minimum 48 dp touch targets, WCAG AA contrast in all themes.
- Font scaling respected up to 200 % without clipped layouts (test at 200 %).
- All interval/preset pickers operable without drag gestures.
