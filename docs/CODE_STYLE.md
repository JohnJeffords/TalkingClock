# Code Style & Contribution Rules

Binding for every contributor — human or AI agent. Two forces shape this
document, and they point the same direction more often than you'd think:

1. **Least code (the "ponytail" discipline).** The best code is code never
   written. Fewer lines, fewer files, fewer dependencies — reuse the platform
   before building anything.
2. **Learning-first readability.** The project owner is learning software
   development on this codebase. A relatively new coder must be able to browse
   the GitHub repo and follow what's happening from names and comments alone.

The synthesis: **minimal code, maximal explanation.** Comments cost nothing at
runtime and nothing in APK size; code costs forever. Write the fewest lines
that solve the problem, then explain them generously.

## The ladder — climb it before writing ANY code; stop at the first rung that solves it

1. **Does this need to exist at all?** Is it a real problem or a speculative
   one? Check DESIGN.md's non-goals — features get parked there on purpose.
2. **Does this codebase already do it?** Search first. `TickSource`,
   `Phrasebook`, `Speaker`, the engines, `SettingsRepository` — a new spoken
   feature is usually a new phrase + a checkpoint, not a new subsystem.
3. **Does Android / AndroidX ship it?** `TextToSpeech`, `SoundPool`,
   DataStore, `DateUtils`/`java.time` formatting, Compose Material components.
   Don't hand-roll what the platform provides (and don't add a library for
   what a platform class already does).
4. **Can it be data instead of code?** Announcement schedules are data.
   Phrase tables are data. Voice-pack composition rules are data. Data is
   testable, diffable, and beginner-readable.
5. **Can it be a small addition to an existing file?** Prefer a 5-line
   addition in the right place over a new class.
6. **Only now, write the minimal working code** — in the package layout from
   ARCHITECTURE.md, smallest sensible surface area.

**New dependencies are guilty until proven innocent**: each one needs a
license check (F-Droid), a size check (4 MB budget), and a "why can't the
platform do this?" sentence in the PR description.

## Naming

- Self-documenting, spelled-out names everywhere. `remainingMilliseconds`,
  not `remMs`; `secondsUntilNextAnnouncement`, not `t`. Single-letter names
  are allowed only as conventional loop indices in tiny loops — and even
  then, prefer `for (lapIndex in laps.indices)`.
- Booleans read as questions: `isRunning`, `hasTtsEngine`, `shouldSpeakSeconds`.
- Functions are verbs that say what happens: `alignDelayToNextSecondBoundary()`,
  not `calc()`.
- Standard Kotlin conventions otherwise (official style guide, enforced by
  ktlint): `PascalCase` types, `camelCase` members, `SCREAMING_SNAKE_CASE`
  only for true constants.

## Comments — deliberately more than industry-normal

Normal advice is "good names make most comments unnecessary." This project
**intentionally overrides that**: we keep the good names AND write the
comments, because the audience includes someone learning what a ViewModel or
a coroutine *is*.

- **Every file** opens with a short header: what this file is responsible
  for, and how it fits the picture ("This ViewModel holds the Clock screen's
  state. The UI (ClockScreen.kt) just draws whatever state says.").
- **Every non-obvious block** gets a *why* comment, and blocks that are
  obvious to a senior dev but not a beginner get a *what/how* comment too:
  what is a StateFlow, why `viewModelScope`, why `elapsedRealtime()` and not
  `currentTimeMillis()` (link to ARCHITECTURE.md's timekeeping rules).
- **Teach at the weird spots.** Anywhere the code does something for a
  platform-quirk reason (TTS init being async, FGS notification rules,
  Compose recomposition), the comment explains the quirk — these are the
  exact places a newcomer gets lost.
- Public functions/classes get KDoc (`/** … */`) with a one-line summary and
  param notes where the name alone isn't enough.
- Comments explain the code as it is — never narrate history ("changed this
  because the review said…"). History lives in git and DECISIONS.md.
- If a comment and the code disagree, that's a bug; fix both in one commit.

## Structure & practices (the boring standards, on purpose)

- Standard Android/Gradle project layout, standard Kotlin style, standard
  Compose patterns (state hoisting, unidirectional data flow) — a newcomer
  should be able to google any pattern they see here and find a thousand
  explanations of it. Novelty budget: zero.
- Small, single-responsibility files (~150 lines as a guideline, not a hard
  cap — never split awkwardly just to hit a number).
- `domain/` stays pure Kotlin (no Android imports) so it's fully unit-testable.
- Every behavior change ships with a test; every bug fix ships with the test
  that would have caught it.
- Conventional commit messages (`feat:`, `fix:`, `docs:`, `test:` …) with a
  body that says *why* — commit messages are part of the learning material.

## Documentation-for-continuity rule

Assume the next contributor — AI agent with a fresh context, or a human who
just cloned the repo — has **zero chat history**. Therefore:

- Decisions with a "why" go in `docs/DECISIONS.md` the day they're made.
- Anything a contributor must know to work on the code goes in `docs/`, not
  in a conversation. If it was worth explaining in chat, it's worth a doc.
- READMEs and docs are updated **in the same PR** as the change they
  describe; a PR that makes a doc stale is incomplete.
- Each screen/subsystem's design lives in DESIGN.md / ARCHITECTURE.md;
  code comments link to those docs rather than re-explaining them.

## Guardrails — never "lazy" these away

- Correct timekeeping (the monotonic-clock rules in ARCHITECTURE.md).
- The permission set and no-INTERNET rule (CI enforces; don't fight it).
- Accessibility (DESIGN.md § Accessibility) — a talking clock that TalkBack
  can't use is broken, not minimal.
- Tests, error handling, and explicitly requested behavior. Least code means
  not building the *unasked*, never skipping the asked.

---
The "ladder" section is adapted from
[DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail), with
its "minimal comments" instinct deliberately inverted for this project's
learning-first goal.
