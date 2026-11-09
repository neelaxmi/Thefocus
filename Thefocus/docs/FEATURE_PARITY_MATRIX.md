# FEATURE_PARITY_MATRIX

Status vocabulary: **Planned** · **In progress** · **Implemented** · **Tested** · **Blocked** · **Excluded (by design)**.
Status as of Milestone 1 (2026-11-05). "Engine implemented" = verified on the JVM only. Nothing is device-tested; see TESTING_REPORT.md for exactly what was run.

| Feature | Reference app | Our implementation | Technology | Status | Testing |
|---|---|---|---|---|---|
| Timer / stopwatch / pomodoro, timestamp-persistent | Yes | Same model: absolute start, accumulated pause, state machine | Kotlin, Room, WorkManager, AlarmManager | **Engine implemented** (`SessionMachine`); persistence/service/alarm Planned (P0) | 17 JVM tests pass (clock tamper, reboot, catch-up, pomodoro, breaks); no device test yet |
| Active session survives kill/reboot | Yes | Foreground service + boot receiver + DB truth | FGS, BOOT_COMPLETED | Planned (P0) | Instrumented |
| Foreground app detection | UsageEvents polling | UsageEvents + accessibility window events | UsageStatsManager | Planned (P0) | Device test |
| App blocker + overlay | Yes | Blocklist/allowlist policy engine + overlay | SYSTEM_ALERT_WINDOW | Planned (P0) | Device test |
| App limits (daily) | Yes | Same, local | Room | Decision logic implemented in `PolicyEngine`; storage/UI Planned (P1) | JVM tests pass |
| Strict mode (locked-in, cooldown, breaks) | Yes | Same rules, local, tamper-evident session log | State machine | **Rules implemented in engine**; enforcement UI/overlay Planned (P0) | JVM tests pass (no pause, cooldown, clock-jump-proof, emergency log); no device test |
| Settings/uninstall protection | Accessibility cover screen | Same technique; documented limits | AccessibilityService | Planned (P1) | Device test |
| Device admin force-lock | Yes | Optional | DevicePolicyManager | Planned (P2) | Device test |
| Reels/Shorts/Spotlight blocking | Digest-of-counts + view ids | Independent node-tree rules, data-driven rule files, user-updatable | AccessibilityService | Planned (P1) | Device test per app version |
| Website blocking | URL-bar read | Same technique + own category lists; **optional** local VPN-DNS filter as a second method | Accessibility; VpnService (P3) | Planned (P1/P3) | Device test per browser |
| YouTube Study Mode | Channels/keywords | Local allowlists + suggested starter list we author | Accessibility + Room | Planned (P2) | Device test |
| Study browser | Yes | WebView with allow/blocklists | WebView | Planned (P2) | Instrumented |
| Notification blocking/batching | Yes | NotificationListener digest | NLS, Room | Planned (P2) | Device test |
| Block launcher | Yes | Home-screen replacement guard | Accessibility/Role | Planned (P3) | Device test |
| Usage analytics | Yes + upload | Local only, no upload | UsageStatsManager, Room | `UsageSessionizer` implemented; Android data source + screens Planned (P0/P1) | JVM tests pass; accuracy vs system Digital Wellbeing not yet measured |
| Schedules (recurring) | Yes | Same, idempotent occurrences | AlarmManager, WorkManager | Planned (P1) | Unit + device |
| Streaks / coins / badges | Yes | Verified-session-only | Room | Streak calculation implemented; coins/badges Planned (P2) | JVM tests pass |
| Focus music | Yes | Original/CC0 audio | Media3 | Planned (P2) | Device |
| Widgets | 7 | Glance widgets | Glance | Planned (P3) | Device |
| AI coach | Message bank + server refresh | Original bank + optional LLM provider layer (Ollama/user key) | Room, provider interface | Planned (P2) | Unit |
| Exam prep + study planner | Onboarding list | Original planner using exam date, hours, weak subjects | Kotlin | Planned (P2) | Unit |
| Multiplayer rooms + leaderboards | Regain backend | Our own backend, server-validated | Supabase/self-host Postgres + Realtime | Planned (P3) — needs backend | Integration |
| Backup/restore | Cloud | Local export/import (P1); optional self-host sync (P3) | JSON, SAF | Planned | Unit |
| Themes (dark/AMOLED/custom) | Yes | Original theme engine | Compose Material3 | Planned (P2) | UI tests |
| Desktop/web dashboard | No | Later phase | Tauri/React | Planned (P4) | — |
| Subscriptions, paywalls, ads, referrals, Mixpanel/PostHog | Yes | **Excluded by design** | — | Excluded | — |

## Milestone 2 status (2026-11-06)
| Feature | Reference app | Our implementation | Technology | Status | Testing |
|---|---|---|---|---|---|
| App blocking (focus sessions) | Accessibility + overlay | Same technique, original code; honest status; always-allowed set | AccessibilityService, `PolicyEngine` | Engine **verified**; service/overlay **compiled, not run** | JVM tests; device test pending |
| Scheduled blocking + auto-start | Yes (schedules) | Day mask, overnight, DST-safe, idempotent runs, reminders | AlarmManager RTC, Room | Engine **verified**; alarms **compiled** | JVM tests; device pending |
| Strict mode vs other apps | Overlay + settings-page cover | Overlay + settings guard + config lock; documented limits | AccessibilityService | Logic **verified**; runtime **unproven** | JVM tests |
| App groups / app management | Lists | 5 built-in + custom groups, search, categories, icons | Room, PackageManager | Logic/SQL **verified**; UI **written** | SQLite + JVM |
| Usage analytics | UsageStats + upload | Local only; rollups persisted; no usage access = no numbers | UsageStatsManager, Room | Logic **verified**; source **compiled** | JVM tests; device pending |
| Productivity score | Yes (proprietary) | Documented formula; null without data | Engine | **Verified** | JVM tests |
| Achievements / streaks | Yes | 9 achievements from stored data, unlock-once | Engine, Room | Engine **verified**; UI **written** | JVM tests |
| Restart session | n/a | Controller `restart()` | Engine | **Verified** | JVM tests |
| Reels/Shorts, website blocking, AI coach, rooms | Yes | Not started | — | **Planned (M3)** | — |
