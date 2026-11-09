# MILESTONE 2 PLAN — Intelligence, Deep Focus & Digital Control

## 0. Starting point (inspected, not assumed)
| Requirement | Already in M1 (engine-verified) | New in M2 |
|---|---|---|
| Focus engine (timer/stopwatch/pomodoro, persistence, recovery) | Yes: `SessionMachine`, `SessionController`, Room v1, service, alarm, boot restore | **Restart** action; schedule-started sessions |
| Strict mode | Rules for own session only | **Enforcement against other apps** (AccessibilityService + overlay), settings-page guard |
| App blocker | `PolicyEngine` (pure) | Groups, categorisation, assembler, installed-app catalogue, enforcement service, App Management UI |
| Analytics | `UsageSessionizer` (pure) | Real `UsageStatsManager` source, daily rollups persisted in Room, reports, productivity score |
| Scheduler | none | Schedule engine + coordinator + alarms + boot re-arm + notifications, idempotent runs |
| Achievements | `Streaks` | Achievement engine + persistence + notifications |
| Dashboard | M1 dashboard | + most-distracting app, strict status, upcoming schedules, quick Pomodoro |

**Risk carried in:** M1's Gradle/Compose/Room layers have never been compiled (no CI result is available to me). M2 follows the same verification strategy, and everything is labelled accordingly in `TESTING_REPORT.md`.

## 1. Verification strategy (unchanged, enforced by scripts)
1. Pure logic → `core-engine`, unit-tested here (`scripts/test-core.sh`, must keep ≥ 64 checks, will grow).
2. Android framework code → compiled against real `android-35.jar` (`verify_offline.sh`).
3. Room → type-checked against stubs; **every query executed on SQLite**; **v1→v2 migration executed on SQLite and compared with a fresh v2 schema**.
4. Compose UI → written, syntax-checked only. Labelled **UNVERIFIED** until CI builds it.

## 2. Product decisions (documented because they define behaviour)
- **Enforcement path:** blocking requires the **Accessibility service**. Without it nothing is blocked and the UI says so. Usage access is needed only for analytics. We do **not** use `SYSTEM_ALERT_WINDOW`: the block screen is an `TYPE_ACCESSIBILITY_OVERLAY` window owned by the accessibility service.
- **Groups:** a group's `enabled` flag means "applies during focus sessions". Schedules choose groups explicitly (works even if the group is not enabled for sessions).
- **Schedule windows** may cross midnight; an occurrence belongs to the day it starts. Auto-start runs **once per occurrence** (persisted `schedule_runs`).
- **Distracting app** = member of an enabled block group, or category SOCIAL / ENTERTAINMENT / GAMES.
- **Productivity score (0–100)** `= 100 × (0.6 × min(1, focus/goal) + 0.4 × (1 − min(1, distractingMin/120)))`; **not computed** (shown as "needs usage access") when usage data is unavailable.
- **Distraction-free day:** ≥ 1 verified session that day AND ≤ 10 min in distracting apps, with usage data present for that day.
- **Weekly Champion:** daily goal met on ≥ 5 of the 7 days of a calendar week (Mon–Sun).
- **Streak/achievement counting:** only finished sessions with an untouched clock (same rule as the dashboard).
- **Usage retention:** Android keeps raw usage events only for a few days, so daily rollups are persisted in Room; history older than the retention window *before the app was installed/opened* cannot be recovered. Shown in the UI.

## 3. Stages
A. Engine: categories, groups, policy assembler, schedule engine + coordinator, analytics, achievements, settings guard, enforcement status, controller restart → tests.
B. Room v2 + migration + DAOs + stores → SQL/migration checks.
C. Platform: usage source, app catalogue, accessibility service + overlay, policy holder, schedule alarms/receivers, rollup, notifications → compile vs android.jar.
D. UI: App Management, Blocking/Schedules, Insights (usage/history/achievements), dashboard upgrade, permission onboarding with accessibility disclosure.
E. CI release config, docs, tests report, ZIP.

## 4. Explicitly NOT in Milestone 2 (stay honest)
Reels/Shorts section detection, website blocking, YouTube study mode, AI coach, multiplayer, widgets, break-reminder-interval for plain timers, WorkManager-based rollup (an AlarmManager-based rollup is used instead).
