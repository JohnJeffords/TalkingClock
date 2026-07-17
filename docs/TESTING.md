# Manual pre-release checklist

Automated CI covers builds, unit/screenshot tests, and emulator smoke runs
on de-Googled images (docs/TESTING_AND_CI.md). This checklist covers what
only a human with a real phone can verify — run it before each release tag,
ideally once on a stock phone and once on GrapheneOS or CalyxOS.
About 15 minutes.

## Speech basics
- [ ] Fresh install launches to the ticking clock; seconds advance smoothly.
- [ ] Tap the time → it is spoken aloud, matching the displayed time.
- [ ] No TTS engine installed (GrapheneOS out of box): the amber guidance
      card shows; nothing crashes; the F-Droid link opens in the browser.
- [ ] Install RHVoice or eSpeak NG → card disappears (or after app restart);
      speech works.
- [ ] With music playing: announcements duck the music, music returns to
      full volume after.

## Speaking clock
- [ ] Arm "every 1 min": chip + amber control + countdown appear together.
- [ ] Announcements land on round minutes (:00), not at odd offsets.
- [ ] Screen off for 10+ minutes: announcements continue on schedule.
- [ ] Notification shows the interval + next time; tapping it stops
      everything, and every armed cue clears.
- [ ] Auto-off pill counts down; at zero everything visibly stops.

## Timer
- [ ] 2-minute Game-style run, screen off: "One minute remaining", 30 s,
      20 s, 10 s, five-four-three-two-one, "Time's up" — the final "one"
      lands within ~1 s of the display hitting 0:01 (stopwatch in hand).
- [ ] Pause silences announcements; resume picks up correctly.
- [ ] Swipe the app away mid-run; reopen → timer restored PAUSED at its
      last known remaining time.
- [ ] Notification Pause/Stop buttons work from the lockscreen.

## Alarms
- [ ] Alarm 2 minutes out, phone locked: full-screen ringing UI pops over
      the lockscreen, tone + spoken time repeat.
- [ ] Snooze: quiet, rings again in 9 minutes.
- [ ] Alarm with speaking-clock handoff: dismissing starts the speaking
      clock at the chosen interval (chip visible on the Clock tab).
- [ ] Reboot the phone with an alarm set: it still fires.

## Settings & misc
- [ ] Theme switch (incl. AMOLED) restyles instantly, everywhere.
- [ ] Quiet hours ON with a window covering now: the armed speaking clock
      stays visibly armed but silent; a running timer still speaks (unless
      the exception is off).
- [ ] Import the voice-pack template zip (docs/voice-pack-template) with
      dummy clips: coverage report renders; a broken zip shows a readable
      error and installs nothing.
- [ ] TalkBack: every control on the Clock screen is labeled; the ticking
      clock does NOT announce every second; tapping the time speaks it.
- [ ] Font scale 200 %: no clipped or overlapping text on any screen.

## Battery / OEM (once per new device model)
- [ ] Xiaomi/Samsung/etc.: with battery saver on, does the 1-min speaking
      clock still announce with the screen off for 10 min? If announcements
      defer, document the OEM in the README's known-issues section (and
      consider the wakelock decision in AnnouncerService's docs).
