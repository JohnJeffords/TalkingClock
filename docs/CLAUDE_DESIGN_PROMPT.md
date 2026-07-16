# Prompt for Claude Design (UI mockups)

Paste the block below into Claude Design to generate mockups of the app's
screens. It is self-contained on purpose — Claude Design won't have this
repo's context.

---

Design a mobile UI for **Talking Clock (OSS)**, a free, open-source Android
clock app whose whole point is speaking the time out loud. Primary audience:
people with **time blindness or ADHD** who lose track of time getting ready —
the design must make "the app is currently announcing" impossible to miss,
and arming it must take at most 2 taps.

**Platform & style:** Android phone (portrait, ~412×915). Material 3.
Dark theme is the default (also show a light variant of one screen). Big,
calm, high-contrast; generous touch targets (≥48 dp); tabular/monospaced
digits so the clock doesn't jiggle as digits change. No decorative clutter —
this is a utility with one hero element per screen. Bottom navigation bar
with three destinations: Clock, Timer, Stopwatch. Hamburger icon top-left
opening a drawer with Settings and About.

**Screen 1 — Clock (home):**
- Date line ("Tue, Jul 15"), then a huge digital clock **with seconds**
  (14:23:07) as the hero. Tapping the clock speaks the time once.
- Below it, one prominent control: a "Speak every: …" dropdown button.
  Closed state shows the active interval. Open state lists:
  Off · 15 s · 20 s · 1 min · 5 min · 10 min · 20 min · 30 min · 1 h ·
  12 min (last custom) · Custom…
- When an interval is active, the button glows/pulses subtly and a small
  status chip appears ("Speaking every 5 min"). Show both armed and off
  states.
- Moon icon in the top bar = keep-screen-awake toggle.

**Screen 2 — Talking Timer:**
- Hero: large remaining time (12:34) with a thin progress ring or bar.
- A "Duration" field showing the last-used value (e.g. 15:00) with an edit
  affordance — the user types the duration; there are no duration preset
  chips.
- An "Announcements" dropdown: Game style · Minimal · Frequent · Edit…
  (these schedules control spoken milestones like "Five minutes remaining"
  and a 5-4-3-2-1 countdown).
- Buttons: Start/Pause (primary), Reset (secondary).
- Show two states: idle (ready to start) and running (include a small
  banner/hint that announcements continue with the screen off, via a
  persistent notification).

**Screen 3 — Stopwatch:**
- Hero: counting-up time 00:04:37.2 (tenths).
- Buttons: Start/Pause (primary), Lap, Reset.
- Lap list below: lap number, lap time, cumulative time; newest on top.

**Screen 4 — Settings (one scrollable screen):**
Sections: Clock (time format, show seconds/date, clock style, speaking
style, hourly chime), Timer (announcement schedules, end-of-timer sound,
overtime), Stopwatch (precision, announce interval, speak laps), Voice
(voice source: System TTS / voice packs, rate, pitch, test button, import
voice pack), Display (theme incl. AMOLED black, keep screen awake, night
dim), Behavior (haptics, notification explainer), About. Include one inline
warning card in the Voice section: "No text-to-speech engine found — install
a free engine like RHVoice or eSpeak NG" with a button.

**Also show:** the foreground-service notification (collapsed): app icon,
"Timer · 12:34 remaining", Pause and Stop actions.

Deliver: the four screens plus the notification, dark theme, one light-theme
example, and a short note on the type scale used for the hero digits.

---

*Tip: generate one screen at a time if results get cramped, starting with the
Clock screen — it sets the visual language for the rest.*
