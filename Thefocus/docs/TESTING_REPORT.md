# TESTING_REPORT — Milestones 1 + 2

_Date: 2026-11-05 (revised after verification-script fixes). Legend:_ **VERIFIED** = executed and passing in the build sandbox · **COMPILED** = compiled against the real Android framework but not executed · **TYPE-CHECKED** = checked against stubs only · **WRITTEN, NOT RUN** = exists, has never been executed · **UNVERIFIED** = never compiled or run anywhere yet.

## Milestone 2 results (2026-11-06)
| Area | How | Result |
|---|---|---|
| Engine suite | `scripts/test-core.sh` (kotlinc 2.1.20 + JDK 21) | **VERIFIED — 119/119** (64 from Milestone 1 untouched + 55 new). The run fails if fewer than 119 execute |
| Schedule engine | masks, start-inclusive/end-exclusive, overnight windows, DST spring-forward (1 real hour), next boundary, upcoming | **VERIFIED** |
| Schedule coordinator | auto-start with remaining window length; duplicate wake-ups idempotent; user already in a session consumes the occurrence; strict cooldown; missed window; boot mid-window; reminder once | **VERIFIED** |
| Policy assembler / holder | enabled-groups-only for sessions; schedule groups regardless of enabled flag; paused/break/ended release; staleness at schedule boundary | **VERIFIED** |
| Config lock | strict session / strict schedule locks group+schedule edits; non-strict and ended windows do not | **VERIFIED** (enforced in `ManageViewModel`: **WRITTEN**) |
| Enforcement honesty | never reports enforcing without the accessibility service (5-state table) | **VERIFIED** |
| Settings guard | protects only this app's info/accessibility/device-admin/uninstall pages, not other apps' | **VERIFIED** |
| Usage rollup | per-day/per-app totals, ignored packages, unlock counts, partial retention day **not** stored, no events → nothing written, no access → nothing read/written | **VERIFIED** |
| Reports / score | totals, averages over days WITH data, trend, missing day = "no data" not zero, productivity score formula and null-without-data | **VERIFIED** |
| Achievements | all 9, counting rules (tampered/early-ended sessions), weekly champion across calendar weeks, distraction-free needs real usage data, notify-once, permanence | **VERIFIED** |
| Controller restart | keeps config, old session saved, refused in strict | **VERIFIED** |
| Mutation checks | broke idempotency, partial-day rule, score-without-data, enforcement honesty in a scratch copy | **VERIFIED** — 5 failures as required |
| Room v2 | `scripts/check_room_v2.py`: migrated v1 DB (with real session rows) == fresh v2 schema for 10 tables; 20 new queries executed; composite-key idempotency; v1 data preserved | **VERIFIED on SQLite** (models Room's DDL; real Room validation = `MigrationTest` in CI) |
| Platform layer | 52 classes compiled against real `android-35.jar` (usage source, app catalogue, accessibility service, overlay, alarms, notifiers, receivers) | **COMPILED** — never executed |
| Application shell | `FocusApp` + platform + data (38 data classes) type-checked together; Room = stubs | **TYPE-CHECKED** (89 classes) |
| Resources | 312 string uses defined; quotes/apostrophes/format specifiers valid; manifest components/resources/permissions consistent | **VERIFIED** (static) |
| Verification tooling | `selftest_verify.sh`: baseline + 11 injected defects (compile errors in 3 layers, regressed rule, dropped tests, broken SQL, lying compiler x2, migration mismatch, unregistered entity, bad string resource) | **VERIFIED — 12/12** |

### Milestone 2: NOT run (treat as unproven)
| Area | Status |
|---|---|
| Gradle sync/build, KSP/Room code generation, lint, release build | **UNVERIFIED** (no Android SDK / Maven in the sandbox; CI result not available to me) |
| All Compose UI added in M2 (Blocking, Insights, dashboard cards, Focus restart/strict warnings, Settings) | **WRITTEN, never compiled**; only a syntax-level pass (0 syntax errors, balanced delimiters) |
| `androidTest`: `MigrationTest`, `ConfigAndUsageStoreTest` (+ M1's three) | **WRITTEN, NOT RUN** |
| AccessibilityService behaviour on real devices: event delivery, overlay window, foreground check on policy change, Settings guard | **UNTESTED** |
| `UsageStatsManager` reads, unlock counting (`KEYGUARD_HIDDEN`), agreement with system Digital Wellbeing | **UNTESTED / UNMEASURED** |
| Wall-clock schedule alarms, reboot re-arm, Doze behaviour, OEM kills | **UNTESTED** |
| Battery/CPU cost of the accessibility service and rollups | **UNMEASURED** |
| Accessibility (TalkBack), tablets, multiple Android versions | **UNTESTED** |

## Corrections to the previous delivery (verification scripts)
Two defects in the verification tooling were found by independent review and have been fixed and re-verified:
1. **`scripts/test-core.sh` failed** for anyone running it: it compiled `EngineSuiteTest.kt` (the Gradle/JUnit wrapper), which needs JUnit. The script had last been run *before* that file existed and was not re-run. It now excludes the wrapper through one shared definition (`scripts/lib.sh`) and runs all engine checks.
2. **`scripts/verify_offline.sh` could not fail on a compile error.** The platform step used `kotlinc ... | grep -v '^warning' && exit 1 || true` under `set -o pipefail`; when the compiler failed, the pipeline status was non-zero, the `exit 1` branch was skipped and `|| true` swallowed the failure (reproduced: a deliberately broken file still returned exit 0). The earlier "compiled" statement was true only because the same code had also been compiled by hand; the script itself did not prove it.

Fix: every compile now goes through `compile()` in `scripts/lib.sh`, which uses the compiler's real exit status, prints the compiler output on failure, aborts the script, and only reports "compiled OK" if `kotlinc` exited 0 **and** produced class files. The engine suite additionally must run at least 64 checks. `scripts/selftest_verify.sh` proves the scripts can fail (see below).

## What was actually run
| Area | How | Result |
|---|---|---|
| Engine suite (state machine, strict rules, policy, usage, streaks, controller, dashboard, formatting) | `scripts/test-core.sh` and `verify_offline.sh` step 1 (kotlinc 2.1.20 + JDK 21); re-run after the script fixes | **VERIFIED — now 119 checks** (the original 38 and the Milestone 1 total of 64 are unchanged) |
| Mutation check on strict-mode/clock logic | Deliberately broke the code in a scratch copy | **VERIFIED** — tests failed as they should (4 failures) |
| Process death → restore | `ControllerTest` "PROCESS DEATH": a second controller over the same store rebuilds the session and re-arms the wake-up | **VERIFIED** (fake store) |
| Reboot → restore | `ControllerTest` "REBOOT": monotonic clock reset, wall clock used, `rebootCount` set | **VERIFIED** (simulated clocks) |
| Late/missed alarm | `ControllerTest`: alarm delivered 5 h late still ends at the true end time | **VERIFIED** |
| Clock tampering | Wall clock moved +3 h: session not shortened, completion not counted for streaks/rewards | **VERIFIED** |
| Strict mode | No pause; cooldown cannot be skipped by clock jump; state survives process death; emergency exits counted per day from persisted logs | **VERIFIED** (engine level) |
| Platform layer (FocusService, AlarmWakeScheduler, receivers, NotificationPresenter, PermissionHelper) | Compiled against real `android-35.jar` by the hardened `verify_offline.sh` step 3 (kotlinc exit 0, 30 class files) | **COMPILED** (now enforced by the script) — never executed |
| Room entities/DAO/mappers | Type-checked against annotation **stubs** (20 class files; not real Room); mapper round-trip tests executed with a JUnit shim | **TYPE-CHECKED + mapper tests VERIFIED (6)** |
| DAO SQL | The 5 real `@Query` strings executed against SQLite with Room-equivalent schema | **VERIFIED** (not Room's own compile-time check) |
| Manifest | Components exist; every `@resource` resolves; actions match real `Intent` constants; no INTERNET permission | **VERIFIED** (static) |
| Strings | 134 `R.string` uses all defined | **VERIFIED** (static) |
| Verification tooling itself | `scripts/selftest_verify.sh`: unmodified tree must pass; 8 injected defects must each fail with the right message (platform/engine/data compile errors, a regressed strict-mode rule, dropped tests, a broken DAO column, a compiler that exits 1 printing only a warning-looking line, a compiler that exits 0 producing no classes) | **VERIFIED — 9/9** |

## How to reproduce (no Gradle needed)
```
KOTLINC=/path/to/kotlinc/bin/kotlinc bash scripts/test-core.sh                      # engine checks only
KOTLINC=... ANDROID_JAR=/path/to/android.jar bash scripts/verify_offline.sh          # all 6 offline steps
KOTLINC=... ANDROID_JAR=... bash scripts/selftest_verify.sh                          # proves the scripts can fail
```
Note: the data-layer step uses Room *annotation stubs* and a JUnit *shim*; it does not exercise real Room or real JUnit. `EngineSuiteTest.kt` (the Gradle/JUnit entry point) is compiled only by Gradle in CI.

## What has NOT been run (be skeptical of these until CI is green)
| Area | Status |
|---|---|
| Gradle sync / `assembleDebug` / KSP + Room code generation / lint | **UNVERIFIED** — Maven/Google repos are unreachable from the sandbox. Versions chosen are old and stable, but a first-build error is plausible. |
| All Jetpack Compose UI (dashboard, focus, history, settings, MainActivity, ViewModel) | **UNVERIFIED** — only syntax-sanity-checked. |
| `androidTest` (RoomSessionStoreTest, ProcessDeathRestoreTest, FocusServiceSmokeTest) | **WRITTEN, NOT RUN** — run by the `instrumented` CI job. |
| Real device/OEM behaviour: Doze, exact-alarm delivery, FGS start restrictions, notification chronometer, boot receiver | **UNTESTED** |
| Battery, memory, CPU, timer drift over hours | **UNMEASURED** |
| Multiple screen sizes / tablets / Android versions | **UNTESTED** |
| Accessibility / TalkBack | **UNTESTED** |

## Feature status after this milestone
| Feature | Status |
|---|---|
| Timer / stopwatch / pomodoro logic | **Implemented, engine-verified**; end-to-end on device **not yet verified** |
| Persistent session storage (Room) | **Implemented**, SQL + mapping verified; Room runtime unverified |
| Foreground service + exact alarm + boot restore | **Implemented, compiled**; **not run on a device** |
| Strict mode (rules) | **Implemented in engine, verified**. Enforcement of *other apps* (blocking) is **not implemented yet** (Phase 2) |
| Dashboard | **Implemented (UI unverified)**; numbers come from the verified calculator |
| History, settings, permission guidance | **Implemented (UI unverified)** |
| App blocking, overlays, usage analytics screens, schedules, limits | **Not started** (policy/usage logic exists and is tested; no Android wiring) |
| Reels/Shorts, website blocking, AI coach, rooms | **Not started** |
