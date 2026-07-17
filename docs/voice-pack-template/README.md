# Making a voice pack

A voice pack replaces the robot voice with YOUR recordings. The app stitches
your clips together to say times and timer announcements — no AI, no cloud,
just concatenation (the same idea telephone systems have used for decades).

## Quick start

1. Record the clips listed in `pack.json` (a phone voice-memo app is fine).
   Keep each clip tight: ≤ 50 ms of silence at the ends — the app adds its
   own gap (`gapMs`) between clips.
2. Save them as **Ogg or WAV**, mono, 44.1/48 kHz, using the folder layout
   in `pack.json` (`num/7.ogg`, `time/oclock.ogg`, …).
3. Edit `pack.json`: your pack's name, your name, and a **license** — this
   field is required, and it must be an open license (CC0-1.0 is the
   simplest; CC-BY-4.0 if you want credit).
4. Zip the whole folder (pack.json at the ROOT of the zip, not inside a
   subfolder) and rename it `mypack.tcvoice`.
5. In the app: Settings → Voice & speech → Import voice pack…

The import screen shows a coverage report ("Can speak: clock ✓ timer ✓
stopwatch ✗") so you know exactly what's missing. Anything your pack can't
say falls back to the system voice — whole sentences at a time, never a
robot word spliced into your voice.

## The token checklist

| Tokens | What to say | Needed for |
|---|---|---|
| `num.0` … `num.20`, `num.30`, `num.40`, `num.50` | the bare numbers ("seven", "twenty") | everything |
| `time.oclock` | "o'clock" | clock |
| `time.am` / `time.pm` | "A M" / "P M" | clock |
| `time.minute` / `time.minutes` | "minute" / "minutes" | stopwatch laps |
| `time.second` / `time.seconds` | "second" / "seconds" | stopwatch laps |
| `timer.minutes-remaining` | "minutes remaining" | timer |
| `timer.seconds-remaining` | "seconds remaining" | timer |
| `timer.times-up` | "Time's up!" (have fun with this one) | timer |
| `timer.halfway` | "Halfway there!" | timer (optional) |
| `stopwatch.lap` | "Lap" | stopwatch |

**Optional extras:** exact number clips (`num.23`) always win over stitched
ones (`num.20` + `num.3`) — record the numbers you care about sounding
natural (like your favorite timer durations) and let composition handle
the rest.

How the app says "2:23 PM": `num.2` + `num.20` + `num.3` + `time.pm`.
How it says "Five minutes remaining": `num.5` + `timer.minutes-remaining`.

## Rules the importer enforces

- `pack.json` must parse, declare `formatVersion: 1`, and name a license.
- Every clip the manifest lists must exist in the zip.
- Paths must stay inside the pack (no `..`, no absolute paths).
- 50 MB maximum per pack.
