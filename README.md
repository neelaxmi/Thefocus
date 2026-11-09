# NEELAXMI : THE FOCUS

Independent, local-first Android focus and digital-wellbeing app for students.

> **Repository upload status:** This is a partial foundation push. The complete Android app source, tests, resources, documentation, scripts, and Gradle wrapper are still to be added from the supplied ZIP. Do not treat this commit as a buildable release.

## Project layout planned

- `app/` — Android app: Kotlin, Jetpack Compose, Room persistence, foreground timer service, alarms, notifications, screens and resources.
- `core-engine/` — pure Kotlin focus-session engine, policy rules, usage sessionizer, dashboard and streak calculations.
- `docs/` — architecture, feature inventory, parity matrix, roadmap, known limitations, testing and permission notes.
- `scripts/` — offline verification and core test scripts.
- `.github/workflows/` — CI build and verification.

## Current verification notes

The supplied milestone report states that 64 core-engine checks passed. Android Gradle build, Compose/Room code generation, emulator and real-device behaviour had not yet been verified in the source environment. See the full `docs/TESTING_REPORT.md` in the ZIP before relying on the app.

## Build (after the complete project tree is uploaded)

```bash
./gradlew :core-engine:test :app:assembleDebug
```

## Independence and privacy

This is an independent implementation. It is not affiliated with Regain or EpowerX Labs and does not include their proprietary source code or assets. The intended app is local-first, with no analytics or ads.
