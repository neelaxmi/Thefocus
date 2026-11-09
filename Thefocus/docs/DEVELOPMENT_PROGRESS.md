# DEVELOPMENT_PROGRESS

## Milestone 2 (2026-11-06) — see MILESTONE_2_REPORT.md
- **Done and verified (logic):** schedule engine/coordinator, policy assembler/holder, config lock, analytics rollup/reports/score, achievements, settings guard, restart, Room v2 + migration (SQLite model).
- **Written, compiled against android.jar, not run:** usage source, app catalogue, accessibility service + overlay, schedule/rollup alarms, notifiers, receivers.
- **Written, never compiled:** Compose UI (Blocking, Insights, dashboard cards, Focus/Settings changes), Gradle/CI release job.
- **Engine checks:** 119 (64 from M1 preserved). **Selftest:** 12/12.
- **Next:** (1) run CI and send me the first failing log; (2) fix first-build errors for M1+M2; (3) run the 5 instrumented test classes; (4) device test of accessibility blocking, schedules and usage; (5) then Milestone 3 (Reels/Shorts, website blocking, AI coach, rooms).


_Last updated: 2026-11-05 — Milestone 1: Android foundation + working timer (verification scripts hardened, revision 2)_

## Milestone 1 (this delivery)
| Item | Status |
|---|---|
| Gradle multi-module project (`:app`, `:core-engine`), version catalog, wrapper, CI | Written. **Not yet built by Gradle** |
| Engine integrated (`SessionController` orchestrator, dashboard calculator, formatting) | **Done, 64 checks passing** (original 38 kept) |
| Room DB + persistent sessions + audit logs | Written; SQL + mappers verified; Room codegen unverified |
| Foreground service, exact alarm, boot/time/zone restore, notifications | Written; **compiled against android-35.jar**; not run on a device |
| Timer / Stopwatch / Pomodoro, breaks, Strict (own-session) rules | Engine verified; UI unverified |
| Dashboard, Focus, History, Settings (Compose) | Written; **UI never compiled or run** |
| Permission guidance + OEM notes | Written (`ANDROID_SETUP_AND_PERMISSIONS.md`) |
| Android tests | JVM mapper tests run; 3 instrumented test classes written, **not run** |

## Revision 2 (verification fixes only; no Milestone 2 work started)
- Fixed `test-core.sh` (excluded the JUnit wrapper via shared `scripts/lib.sh`).
- Fixed `verify_offline.sh` so compile failures can no longer be swallowed; added a minimum-check guard and `selftest_verify.sh` (9/9 passing).
- Re-ran every offline check: engine 64/64, platform layer compiled (30 classes), data layer type-checked + 6 mapper tests, DAO SQL 5 queries, manifest checks. See TESTING_REPORT.md "Corrections".

## Blockers / risks
1. **No Gradle/Android SDK/Maven in the build sandbox** → first CI run is the first real compile of the Android module. Expect possible fixes in the Compose/Room/Gradle layer.
2. No GitHub write access (owner pushes the ZIP contents).
3. No physical-device testing: exact-alarm delivery, FGS restrictions and OEM kill behaviour are unproven.

## Next (Milestone 2: blocking core)
1. Get CI green (fix any first-build errors).
2. UsageStats source → `UsageSessionizer` → real stats screen.
3. Foreground-app monitor + `PolicyEngine` wiring + blocking overlay + allowlist/blocklist UI.
4. Accessibility service (with prominent disclosure screen) for window-change detection and settings-page protection.
5. Daily limits and schedules (alarm-backed, idempotent occurrences).

## Open product decisions
- Licence: Apache-2.0 proposed (not yet added as a LICENSE file; owner to confirm).
- Localisation: Hindi strings planned (primary language of the owner/audience).
