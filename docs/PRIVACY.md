# Privacy Policy — Talking Clock (OSS)

**Talking Clock collects no data. None.**

The app has **no internet permission** — the operating system itself
prevents it from sending anything anywhere. There are no ads, no analytics,
no crash reporting, no accounts, and no third-party SDKs.

Everything the app stores (your settings, alarms, timer state, imported
voice packs) lives in the app's private storage on your device and never
leaves it. Uninstalling the app deletes all of it.

The one outward-facing thing the app can do is open your browser (for
example, to show a text-to-speech engine's F-Droid page, or this project's
source code). That is your browser making the visit, not this app.

This policy is enforced in code, not just promised: the project's
continuous-integration checks fail any change that requests the INTERNET
permission. You can verify everything yourself — the complete source is at
<https://github.com/JohnJeffords/TalkingClock>.

*Contact: open an issue at
<https://github.com/JohnJeffords/TalkingClock/issues>.*
