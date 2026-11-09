# NEELAXMI : THE FOCUS

Independent, open-source, local-first focus and digital-wellbeing app for students (Android first).
Not affiliated with Regain/EpowerX Labs. Original code and branding; functionality studied from public behaviour and static analysis only.

## Status — Milestone 2 (2026-11-06)
Milestone 2 (app blocking via accessibility, strict enforcement, schedules, usage analytics, achievements, upgraded dashboard) is **implemented and logic-verified** (119 engine checks, Room v2 migration model, framework compile of the platform layer). **Nothing has been built by Gradle or run on a device.** Start with `docs/MILESTONE_2_REPORT.md` and `docs/TESTING_REPORT.md`.

## Status — Milestone 1 (2026-11-05)
Android foundation with a working timer **in code**. Read `docs/TESTING_REPORT.md` before trusting anything: the engine is
verified (64 checks at the time; 119 now), the Android platform layer compiles against the real framework, but **the Gradle build, the Compose UI
and the on-device behaviour have not been run yet** (the build sandbox had no Android SDK). CI will be the first real build.

## Layout
- `core-engine/` — pure Kotlin: session state machine, strict rules, policy engine, usage sessionizer, streaks, dashboard, controller.
- `app/` — Android: Room, foreground service, alarms, notifications, Compose UI.
- `docs/` — analysis, parity matrix, architecture, roadmap, limitations, testing report, permission guide.
- `scripts/` — `test-core.sh` (engine checks), `verify_offline.sh` (all offline checks), `selftest_verify.sh` (proves those scripts can fail), `check_dao_sql.py`, shared `lib.sh`.

## Build and test
See `docs/ANDROID_SETUP_AND_PERMISSIONS.md`. Short version: `./gradlew :core-engine:test :app:assembleDebug`.
Without Gradle: `KOTLINC=... ANDROID_JAR=... bash scripts/verify_offline.sh` (engine only: `KOTLINC=... bash scripts/test-core.sh`).

## First push (important)
If you unpack the ZIP on Windows the executable bit on `gradlew` and `scripts/*.sh` is lost. After `git add`, run:
`git update-index --chmod=+x gradlew scripts/*.sh` (CI also fixes `gradlew` itself).

## Privacy
No INTERNET permission, no analytics, no ads, no account. All data stays on the device.
