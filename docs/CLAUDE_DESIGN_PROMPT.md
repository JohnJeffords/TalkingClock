# Prompt for Claude Design (UI mockups)

Paste the block below into a **fresh** Claude Design session. It covers every
screen and state in the app (v2 of this prompt — supersedes the 4-screen
version). It deliberately leaves visual details to the design tool's judgment
and only pins down what the product requires.

When mockups are done, export them into `docs/mockups/` (one image per frame,
named after the screen) so implementing agents can reference them.

---

Design the complete UI for "Talking Clock (OSS)" — a free, open-source Android
clock app whose whole point is SPEAKING the time out loud. Primary audience:
people with time blindness or ADHD who lose track of time while getting ready.
Two design laws follow from that: (1) arming the speaking clock takes at most
2 taps from launch, and (2) it must be impossible to not-notice that
announcements are on (or that they've stopped).

Platform & style: Android phone, portrait (~412×915), Material 3. Dark theme
is the default; big calm hero element per screen, high contrast, ≥48dp touch
targets, tabular/monospaced digits so time displays don't jiggle as digits
change. This is a utility for daily use, not a showcase — familiar beats
clever, but it should still feel warm and cared-for, not clinical. Bottom
navigation: Clock · Timer · Stopwatch. Hamburger (top-left) opens a drawer
with Settings and About. You have good instincts — where I haven't specified
something, make the choice a thoughtful Android designer would.

Screens and states to design (each its own frame):

1. CLOCK (home) — date line, huge digital clock WITH seconds (tap = speak
   once), and one prominent "Speak every: …" dropdown control. Show it in
   both states: OFF, and ARMED at "5 min" (armed must be unmissable — glow,
   chip, whatever works).
2. Clock speak-interval dropdown OPEN: Off · 15 s · 20 s · 1 min · 5 min ·
   10 min · 20 min · 30 min · 1 h · 12 min (last custom) · Custom…
3. NIGHTSTAND MODE — the clock screen dimmed for a dark bedroom: red-tinted,
   low contrast, nothing but the time.
4. TALKING TIMER, idle — big remaining-time display with thin progress ring,
   a typed "Duration" field pre-filled with the last-used value (no preset
   duration chips), an "Announcements" dropdown (Game style / Minimal /
   Frequent / Edit…), Start + Reset.
5. TIMER, running — include a subtle cue that announcements continue with the
   screen off via a notification.
6. TIMER, time's up — loud, obvious end state; and a small variant showing
   overtime counting up past zero (+0:37, red).
7. ANNOUNCEMENT SCHEDULE EDITOR — checkpoints as toggleable rows (30m, 20m,
   10m, 5m, 3m, 2m, 1m, 30s, 10s), plus toggles for: start announcement,
   "halfway there", final 5-4-3-2-1 countdown, "Time's up".
8. STOPWATCH, running — counting up with tenths, Start/Pause · Lap · Reset,
   lap list (lap time + cumulative, newest on top).
9. SETTINGS — one scrollable screen, sections: Clock (time format, show
   seconds/date, clock style incl. seven-segment, speaking style, hourly
   chime) · Timer · Stopwatch · Voice · Display (theme incl. AMOLED black,
   keep screen awake, night dim) · Behavior · About.
10. VOICE section detail — voice source (System TTS / voice packs), rate,
    pitch, test button, "Import voice pack…", AND the warning card for when
    no TTS engine is installed ("install a free engine like RHVoice or
    eSpeak NG" + button). Also a small "voice pack coverage report" card:
    "Can speak: clock ✓ timer ✓ stopwatch ✗ — missing: stopwatch.lap".
11. NOTIFICATION-PERMISSION EXPLAINER — the friendly one-time sheet shown
    before requesting POST_NOTIFICATIONS: why the app shows a notification
    (only while something is running), and the honest line that the timer
    still works without it.
12. FOREGROUND NOTIFICATION — collapsed: "Timer · 12:34 remaining" with
    Pause/Stop actions; plus the speaking-clock variant ("Speaking clock ·
    every 5 min").
13. NAV DRAWER open, and the ABOUT screen (version, GPL-3.0, source link,
    "no internet permission — nothing leaves your phone" privacy statement).
14. THEME VARIANTS — the Clock screen once in light theme and once in AMOLED
    black, to establish the theme system.

Finish with a short spec note: the type scale for hero digits, the accent
color, and the armed-state treatment, so a developer can reproduce them.

---

*Tip: if a single session gets cramped, do frames 1–8 (the core screens)
first, then feed the established style back for frames 9–14.*
