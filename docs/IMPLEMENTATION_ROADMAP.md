# IMPLEMENTATION_ROADMAP

Order follows the owner's priority: functionality first (blocking, strict mode, persistent timers, usage analytics), UI second.

| Phase | Content | Exit criteria |
|---|---|---|
| 0 | Docs, repo scaffold, CI, license, Gradle skeleton | Repo builds a debug APK in CI |
| 1 (P0) | `:core:engine` — SessionStateMachine, PolicyEngine, UsageSessionizer + JVM tests | All unit tests green incl. pause/resume/boot/clock-jump cases |
| 2 (P0) | Room DB, session service, exact-alarm end, notification, boot restore; minimal Compose UI to drive it | Session survives app kill + reboot on a real device |
| 3 (P0) | UsageStats source, usage sessionizer wiring, real stats screen | Numbers match system Digital Wellbeing within tolerance (documented) |
| 4 (P0) | App blocker: monitor, overlay, allowlist/blocklist, permissions onboarding | Blocked app is covered within target latency on test devices |
| 5 (P0/P1) | Strict mode: lock UI, cooldown, breaks, emergency exit log, settings-page cover, device admin | Strict scenarios pass on device; limits documented |
| 6 (P1) | App limits, schedules, streaks, notifications | Recurrence without duplicates (unit tested) |
| 7 (P1) | Reels/Shorts rule engine, website blocking (accessibility) | Per-app/browser device test matrix filled in TESTING_REPORT |
| 8 (P2) | YouTube/Study browser, notification digest, music, themes, coach (rule-based), planner/exam mode | Each feature has real path + test |
| 9 (P2) | Optional LLM provider layer (Ollama/user key) | Works with a local endpoint; absent = feature hidden |
| 10 (P3) | Backend + rooms + leaderboards, widgets, VPN-DNS filter, launcher block, local-export sync | Server validation tests |
| 11 (P4) | Desktop/web dashboard, polish, release builds, F-Droid/GitHub release | Signed APK actually produced |

Rule: a phase is closed only when `TESTING_REPORT.md` records what was tested and how.
