# MILESTONE 2 REPORT — Intelligence, Deep Focus & Digital Control

_Date: 2026-11-06. Read `TESTING_REPORT.md` for exactly what was executed. Legend: **VERIFIED** = executed and passing in the build sandbox · **COMPILED** = compiled against the real Android framework, never executed · **TYPE-CHECKED** = checked against stubs · **WRITTEN** = exists, never compiled or run (Compose UI, Gradle, Room codegen)._

## 0. Headline
Milestone 2 is **implemented in code and verified at the logic level**. It has **not been built by Gradle, not run on a device and not run in GitHub Actions** (the sandbox has no Android SDK/Maven and I cannot see your CI). Nothing below is claimed to "work on a phone" yet. Milestone 1 was likewise never CI-built, so the first CI run covers both milestones and may need first-build fixes.

## 1. Implementation summary
| Area | What exists | Proof level |
|---|---|---|
| Focus engine | Timer, stopwatch, Pomodoro (short/long breaks, rounds, cycles, auto transitions), user breaks, pause/resume/stop, **restart (new)**, timestamp-based state, process-death/reboot recovery | Engine **VERIFIED**; service/alarm/boot **COMPILED** |
| Strict mode | Own-session rules (no pause, end-cooldown, emergency exits per day) + **enforcement on other apps** via AccessibilityService block overlay + **settings-page guard** + **config lock** (groups/schedules cannot be edited while strict is active) | Rules/guard/lock **VERIFIED**; service/overlay **COMPILED** |
| App blocker | Categoriser, 5 built-in groups seeded from installed apps, custom groups, search/category/multi-select picker with real icons, enable/disable per group, `PolicyAssembler` | Logic **VERIFIED**; Room SQL **VERIFIED**; UI **WRITTEN** |
| Analytics | `UsageStatsManager` source, daily rollups persisted in Room, per-app/daily/weekly/monthly reports, trend, unlock count, focus-vs-distraction, productivity score (**null without usage access**) | Logic **VERIFIED**; source **COMPILED**; UI **WRITTEN** |
| Scheduler | Day-mask + time windows (overnight, DST-safe), per-schedule groups, strict flag, auto-start (once per occurrence), reminders, wall-clock alarm, boot/time/zone re-arm, `schedule_runs` idempotency | Engine **VERIFIED**; alarms/receivers **COMPILED**; UI **WRITTEN** |
| Achievements | 9 achievements from stored sessions + persisted usage rollups, progress, permanent unlocks, one-time notification | Engine **VERIFIED**; notifier **COMPILED**; UI **WRITTEN** |
| Live session | Foreground service + chronometer notification, Pause/Resume/Skip-break actions (hidden in strict), completion/phase alerts, "Today: X" line | **COMPILED**, not run |
| Dashboard | M1 design kept; added quick-start Pomodoro, strict/blocking status, most-distracting app, upcoming sessions, productivity score; glass-style cards, animated ring/bars, interactive tap-charts, loading/empty/error states | **WRITTEN** |
| Data | Room v2: 8 new tables, `MIGRATION_1_2`, DAOs, stores; no destructive fallback | SQL + migration model **VERIFIED**; real Room validation = CI `MigrationTest` |
| CI/release | debug APK artifact, unit tests, lint, **release job** (unsigned unless secrets), emulator tests | **WRITTEN**, never run |

## 2. New files (31)
- `app/src/androidTest/kotlin/com/neelaxmi/focus/ConfigAndUsageStoreTest.kt`
- `app/src/androidTest/kotlin/com/neelaxmi/focus/MigrationTest.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/data/ConfigDao.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/data/EntitiesM2.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/data/Migrations.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/data/RoomConfigStore.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/data/RoomUsageStore.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/data/UsageDao.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/AppCatalog.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/BlockOverlay.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/FocusAccessibilityService.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/ScheduleAlarms.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/UsageEventsSource.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/AppIcons.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/InsightsViewModel.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/ManageViewModel.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/screens/InsightsScreen.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/screens/ManageScreen.kt`
- `app/src/main/res/xml/accessibility_service_config.xml`
- `core-engine/src/main/kotlin/com/neelaxmi/focus/core/Achievements.kt`
- `core-engine/src/main/kotlin/com/neelaxmi/focus/core/Analytics.kt`
- `core-engine/src/main/kotlin/com/neelaxmi/focus/core/Apps.kt`
- `core-engine/src/main/kotlin/com/neelaxmi/focus/core/Blocking.kt`
- `core-engine/src/main/kotlin/com/neelaxmi/focus/core/PolicyHolder.kt`
- `core-engine/src/main/kotlin/com/neelaxmi/focus/core/Schedule.kt`
- `core-engine/src/main/kotlin/com/neelaxmi/focus/core/Services.kt`
- `core-engine/src/test/kotlin/com/neelaxmi/focus/core/Milestone2Test.kt`
- `core-engine/src/test/kotlin/com/neelaxmi/focus/core/ServicesTest.kt`
- `docs/MILESTONE_2_PLAN.md`
- `docs/MILESTONE_2_REPORT.md`
- `scripts/check_room_v2.py`

## 3. Modified files (29)
- `.github/workflows/ci.yml`
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/neelaxmi/focus/FocusApp.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/MainActivity.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/data/FocusDatabase.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/NotificationPresenter.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/PermissionHelper.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/Receivers.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/ServiceLocator.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/platform/Settings.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/Components.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/FocusViewModel.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/screens/DashboardScreen.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/screens/FocusScreen.kt`
- `app/src/main/kotlin/com/neelaxmi/focus/ui/screens/SettingsScreen.kt`
- `app/src/main/res/values/strings.xml`
- `core-engine/src/main/kotlin/com/neelaxmi/focus/core/Controller.kt`
- `core-engine/src/main/kotlin/com/neelaxmi/focus/core/Usage.kt`
- `core-engine/src/test/kotlin/com/neelaxmi/focus/core/Main.kt`
- `docs/ANDROID_SETUP_AND_PERMISSIONS.md`
- `docs/DEVELOPMENT_PROGRESS.md`
- `docs/FEATURE_PARITY_MATRIX.md`
- `docs/KNOWN_LIMITATIONS.md`
- `docs/TESTING_REPORT.md`
- `scripts/lib.sh`
- `scripts/selftest_verify.sh`
- `scripts/verify_offline.sh`

(No file from Milestone 1 was deleted; all original tests still run.)

## 4. Functional status (honest)
**Fully verified logic (executed, passing):** session state machine and controller (incl. restart), strict rules, policy assembly, schedule engine/coordinator (idempotent, overnight, DST, missed windows, boot-late start), usage rollup + reports + productivity score, achievements + unlock-once service, settings-guard classification, enforcement-status honesty, config lock, DAO SQL, Room v2 migration equivalence.
**Not yet functional until proven on a device/CI:** everything that touches Android at runtime (service, alarms, accessibility overlay, usage reads, notifications) and all Compose screens.

## 5. Features that need Android permissions / user setup
| Feature | Requires | Without it |
|---|---|---|
| Any app blocking, strict enforcement, settings guard | **Accessibility service** enabled by the user (with in-app disclosure) | Nothing is blocked; the UI says "NOT blocking" |
| Screen-time analytics, productivity score, "most distracting", distraction-free-day achievement | **Usage access** (special access) | No usage numbers at all; explanatory card |
| Exact end-of-phase and schedule alarms | `USE_EXACT_ALARM` (API 33+) / user-granted `SCHEDULE_EXACT_ALARM` (31–32) | Inexact alarms (may be minutes late) |
| Live countdown, alerts, reminders | `POST_NOTIFICATIONS` (API 33+) | Sessions still run; nothing is shown |
| App list | Narrow `<queries>` (no QUERY_ALL_PACKAGES) | — |

## 6. Features limited by Android OS restrictions
See `KNOWN_LIMITATIONS.md` items 19–32. Key points: force-stop, uninstall (also from a PC or in safe mode), revoking permissions, ADB and vendor settings pages cannot be prevented; blocking is event-driven (window changes), so split-screen/PiP/floating windows are not guaranteed; the block screen does not stop a blocked app's background audio; the OS keeps only a few days of raw usage events (history before install cannot be recovered); OEM battery managers can kill the app.

## 7. Test results
See `TESTING_REPORT.md`. Summary: engine **119/119** (64 original + 55 new), mapper tests 6/6, DAO + Room v2 checks passing (20 queries + migration), verification-script self-test **12/12**. Android tests written (not run): `RoomSessionStoreTest`, `ProcessDeathRestoreTest`, `FocusServiceSmokeTest`, **`MigrationTest`**, **`ConfigAndUsageStoreTest`**.

## 8. GitHub Actions result
**Not available to me.** I have no access to your repository or CI. The workflow (`engine`, `android`, `release`, `instrumented`) is written and YAML-valid but has never run. Please run it and send me the first failing log.

## 9. APK location
**No APK was generated here.** After a green run: Actions → the run → Artifacts → `neelaxmi-focus-debug-apk` (installable) and `neelaxmi-focus-release-apk-unsigned|signed`. The release APK is unsigned unless you add secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## 10. Remaining known bugs / risks
1. **First-build risk:** Gradle/KSP/Room/Compose code has never compiled; expect fixes.
2. Deviations from the brief: persistence uses blocking DAOs on one io thread (not suspend/coroutines); "glassmorphism" is translucent layered cards (no backdrop blur); break reminders at intervals for plain timers are **not** implemented; Reels/Shorts, website blocking, YouTube mode, AI coach, multiplayer remain out of scope (Milestone 3).
3. Rollup assumes the OS retains ≥ 5 days of events; on devices that retain less, one partial day could be stored as complete.
4. Overlay is dismissed only by a different, non-system foreground app; a system-UI/keyboard window never dismisses it (by design) but exotic window types are unverified.
5. Package-name category lists can misclassify apps; users can fix via groups.
6. `Restart` ends the old session as an early exit (counts minutes, not a "completed" session).
7. No Hindi localisation yet.
8. Release APK is not minified (shrinking untested).
