# Voice Pack Specification (v1)

User-supplied packs of **recorded human voice clips** that the app stitches
together for time/timer announcements — no AI, no synthesis, just
concatenation. System TTS remains the default; packs are an opt-in override.

## Prior art (what "the industry" does)

There is no single universal standard, but concatenative time announcement is
a well-trodden path and we borrow the best ideas from each:

- **Asterisk PBX sound prompt sets** — the closest thing to an industry
  standard: a directory of small audio files (`digits/1`, `digits/20`,
  `digits/oclock`, `digits/a-m` …) that the server concatenates to say times.
  Its key insight: record numbers as composable atoms (0–20, then tens), not
  0–59.
- **Rockbox voice files** — proves the "pack = manifest + clips, missing clip
  falls back" model works offline on tiny devices.
- **Game announcer packs (UT/TF2 community)** — loose folders of named WAV/OGG
  clips; no manifest, which is exactly what we fix.

## Container

A voice pack is a **ZIP file** with extension `.tcvoice` (plain zip inside, so
anyone can build one with any zip tool). Imported via the system file picker
(Storage Access Framework → **no storage permission**), validated, and copied
into app-private storage. Size limit 50 MB per pack.

```
mypack.tcvoice
├── pack.json          # manifest (required)
├── num/0.ogg … num/20.ogg, num/30.ogg, num/40.ogg, num/50.ogg
├── time/oclock.ogg, time/am.ogg, time/pm.ogg, time/seconds.ogg …
└── timer/minutes-remaining.ogg, timer/times-up.ogg, timer/halfway.ogg …
```

## Manifest (`pack.json`)

```json
{
  "formatVersion": 1,
  "name": "Tournament Announcer (English)",
  "language": "en",
  "author": "Jane Doe",
  "license": "CC-BY-4.0",
  "gapMs": 40,
  "clips": {
    "num.0": "num/0.ogg",
    "num.1": "num/1.ogg",
    "num.20": "num/20.ogg",
    "num.30": "num/30.ogg",
    "time.am": "time/am.ogg",
    "time.pm": "time/pm.ogg",
    "time.oclock": "time/oclock.ogg",
    "timer.minutes-remaining": "timer/minutes-remaining.ogg",
    "timer.seconds-remaining": "timer/seconds-remaining.ogg",
    "timer.times-up": "timer/times-up.ogg",
    "timer.halfway": "timer/halfway.ogg",
    "timer.started": "timer/started.ogg",
    "stopwatch.lap": "stopwatch/lap.ogg"
  }
}
```

- `license` is required and must be an SPDX identifier of an open license —
  the import screen displays it, and we refuse packs without one (keeps the
  whole ecosystem F-Droid-clean).
- `gapMs`: silence inserted between stitched clips (0–200).

## Token model

Numbers are **composable atoms**, Asterisk-style: `num.0`–`num.20`, `num.30`,
`num.40`, `num.50`. The app composes `47` → `num.40` + `num.7`. A pack MAY
also provide exact clips (`num.47`) which win over composition — packs can be
minimal (26 number clips) or luxurious (60+).

Utterances the app can build (v1 token grammar, English ordering; the
`language` field selects a per-language composition rule table shipped in the
app — composition order differs across languages, which is why the manifest
alone can't express it):

| Utterance | Composition |
|---|---|
| Time 2:23 PM | `num.2  num.23  time.pm` (with `time.oclock` for :00) |
| "5 minutes remaining" | `num.5  timer.minutes-remaining` |
| "30 seconds remaining" | `num.30  timer.seconds-remaining` |
| Countdown "5…4…3…" | `num.5`, `num.4`, … (played on the second, not stitched) |
| "Time's up" | `timer.times-up` |
| "Lap 3, 1 minute 2 seconds" | `stopwatch.lap num.3 num.1 time.minute num.2 time.seconds` |

## Audio format

- **Ogg/Vorbis or Ogg/Opus** (preferred, tiny) or WAV/PCM accepted at import
  (transcoded? No — v1 keeps files as-is; MediaPlayer/ExoPlayer-free playback
  via `SoundPool` for sub-100 ms latency, which supports ogg fine).
- Mono, 44.1/48 kHz recommended; clips should be trimmed tight (≤ 50 ms
  leading/trailing silence) — the `gapMs` setting provides the breathing room.

## Fallback rules (important for UX)

If any token needed for an utterance is missing from the pack, the **entire
utterance** falls back to system TTS (mixing one robot word into a human
sentence is jarring). The import screen shows a coverage report ("Can speak:
clock ✓, timer ✓, stopwatch ✗ — missing: stopwatch.lap") so pack authors know
exactly what to record.

## Authoring & docs

The repo will ship `docs/voice-pack-template/` with a bare `pack.json`, a
token checklist, and a README for pack authors, plus (stretch) one reference
pack recorded by us under CC0 so there's always a working example to copy.
