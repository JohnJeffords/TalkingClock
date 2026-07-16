# Decision Log

Short ADR-style records. Newest first. "Owner" = project owner (John),
"CC" = Claude Code.

## 2026-07-15 — D-008: Voice pack format is our own zip+manifest spec
No true industry standard exists for concatenative time announcements; the
closest are Asterisk prompt sets (composable number atoms) and Rockbox voice
files (manifest + fallback). We define `.tcvoice` = zip + `pack.json`,
borrowing both ideas. Whole-utterance fallback to TTS when tokens are
missing. Details: VOICE_PACKS.md. (CC proposal, pending owner review.)

## 2026-07-15 — D-007: GrapheneOS/CalyxOS covered by condition-equivalent CI, not OS images
Neither OS ships CI-consumable emulator images. We automate the *conditions*
they create (no GMS, no TTS engine, FOSS-TTS-only, notification-permission
denial, no native code) on AOSP no-Google-APIs images, plus a manual
pre-release device checklist. (CC proposal, pending owner review.)

## 2026-07-15 — D-006: Permission set is exactly FOREGROUND_SERVICE(+MEDIA_PLAYBACK) + optional POST_NOTIFICATIONS
Owner: background operation "DEFINITELY needs to stay on", with a visible
notification and an in-app explanation of why. CI's manifest guard fails the
build if the permission set ever grows (and if INTERNET ever appears).

## 2026-07-15 — D-005: minSdk 26, targetSdk = latest Play requirement
Owner asked what Play requires: Play constrains only targetSdk (within one
year of latest release; 35 now). minSdk is our choice → 26 (Android 8.0,
~98 % of devices, small CI matrix). Revisit if a user actually asks for
older. (CC recommendation, adopted.)

## 2026-07-15 — D-004: License GPL-3.0-or-later
Owner choice. Copyleft prevents closed-source ad-laden clones; F-Droid's
house favorite.

## 2026-07-15 — D-003: Own repo, outside the Roblox project
Owner choice. Lives at `Documents/TalkingClock` for the design phase; move
(or clone) outside OneDrive before implementation because Gradle + OneDrive
sync fight over file locks.

## 2026-07-15 — D-002: Kotlin + Jetpack Compose, single module, manual DI
Modern default, fully FOSS, testable; app is too small to justify Hilt or
multi-module. Size risk (Compose ≈ +1.5–2 MB after R8) is inside the 4 MB
budget. (CC recommendation.)

## 2026-07-15 — D-001: No alarms, no widgets, no Wear in v1
Keeps the permission story (no SCHEDULE_EXACT_ALARM), the size budget, and
the scope honest. Parked in DESIGN.md non-goals.

## Open questions for the owner

- **App name** — pick before Play/F-Droid submission (README has candidates);
  also decides `applicationId` (`io.github.<username>.talkingclock`).
- **Timer preset durations** — owner is still thinking about good defaults
  (game-style). Current straw man: 1/3/5/10/15/20/30 min chips.
- **Natural-language phrasing ("five past two")** — v1 English-only, or cut
  from v1 and ship Digits style only?
- **Reference voice pack** — do we record/ship one (CC0), and whose voice?
- **Where to host** — GitHub (easiest CI) vs Codeberg (FOSS-purist points,
  F-Droid-adjacent culture). CI design assumes GitHub Actions; Woodpecker/
  Codeberg CI port is possible but extra work.
