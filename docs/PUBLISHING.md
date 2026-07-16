# Publishing: F-Droid + Google Play

Two stores, one repo. F-Droid is the priority and shapes the whole project
(dependency policy, REUSE, reproducibility); Play adds a few bureaucratic
requirements of its own.

## F-Droid

**How inclusion works:** F-Droid builds the app themselves, from source, from
a public git repo, at a specific tag. You don't upload an APK — you submit a
merge request to [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) with a
build recipe (metadata YAML), and their pipeline + human review check it.

Requirements we must meet (all already baked into the design):
- OSI/FSF-approved license → **GPL-3.0-or-later**, `LICENSE` in repo root.
- Buildable from source with FOSS toolchain only; no prebuilt jars/aars in
  the tree; no proprietary dependencies (their `scanner` checks — we run the
  same scanner in CI, so this can't regress silently).
- No anti-features, or they get badged: we have **none** (no ads, no
  tracking, no non-free network services — no network at all, no non-free
  assets thanks to REUSE).
- Versioning discipline: releases = signed git tags; `versionCode`/
  `versionName` in the tag's tree must match the recipe. F-Droid
  auto-detects updates from tags (`UpdateCheckMode: Tags`).
- **Fastlane metadata in-repo** — F-Droid reads store listing from
  `fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt`
  (≤ 80 chars), `full_description.txt`, `changelogs/<versionCode>.txt`,
  `images/icon.png`, `images/phoneScreenshots/*.png`. Screenshots get
  generated from the emulator in CI so they're always current and honest.
- **Reproducible builds (recommended, we'll aim for it):** if `fdroid build`
  produces a byte-identical APK to ours, F-Droid publishes with *our*
  signature, so users can cross-install between F-Droid and Play/GitHub
  builds. Requires pinned SDK/build-tools, no signing-time variance, stable
  timestamps — the nightly diffoscope job in CI keeps us there.

**Submission checklist (when v1.0 is ready):**
1. Public repo (GitHub or Codeberg) with LICENSE, fastlane metadata, tagged
   release.
2. Fork `fdroiddata`, add `metadata/<applicationId>.yml` (categories:
   `Time`; license; source/issue URLs; build block: `gradle: [yes]`,
   subdir `app`).
3. Test locally with `fdroid build -v -l <appid>` in their docker image
   (we already run this container in CI, so this step is a formality).
4. Open the MR; respond to review. First inclusion typically takes a few
   weeks of queue — plan for that, it's normal.

## Google Play

- **One-time $25 developer registration.**
- **Personal accounts created after Nov 13 2023 must run a closed test with
  at least 12 testers opted-in for 14 continuous days** before they can apply
  for production access. This is the biggest schedule item — recruit testers
  (friends, F-Droid forum, r/fossdroid) early.
- **Target API level policy:** new apps/updates must target an API level
  within one year of the latest Android release (API 35 as of mid-2026;
  bump annually — CI's targetSdk is the single source of truth). *There is
  no minSdk requirement* — minSdk 26 is purely our choice.
- **AAB required** for Play (F-Droid uses the APK; we build both from the
  same tag). Play App Signing is mandatory for new apps — Google holds the
  release key for the Play channel; our own key still signs the
  F-Droid/GitHub APKs (and reproducible builds keep those consistent).
  Document that the two channels' signatures differ (users can't
  cross-update Play↔F-Droid unless we achieve reproducible publishing with
  our key on F-Droid — another reason to chase reproducibility).
- **Data safety form:** gloriously short for us — no data collected, no data
  shared, no internet. Still must be filled in accurately.
- **Privacy policy URL required** even for no-data apps: a one-paragraph
  page in the repo (GitHub Pages) stating no collection, no network.
- Content rating questionnaire (IARC) — trivial, "Everyone".
- Store listing assets: icon 512 px, feature graphic 1024×500, ≥ 2
  screenshots — all derived from our own open assets (same REUSE rules).

## Release flow (both stores, one motion)

```
tag vX.Y.Z (signed)
  → CI release.yml: builds APK (our key) + AAB (Play upload key),
    runs full matrix + compliance, attaches APK to GitHub Release
  → Play: upload AAB to closed/production track, changelog from fastlane
  → F-Droid: picks up the tag automatically (UpdateCheckMode: Tags), builds,
    publishes ~a few days later — nothing to do after initial inclusion
```

Version scheme: `versionName = X.Y.Z`, `versionCode = X*10000 + Y*100 + Z`
(monotonic, derivable, no per-store suffixes).
