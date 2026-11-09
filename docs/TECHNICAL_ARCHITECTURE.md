# TECHNICAL_ARCHITECTURE — NEELAXMI : THE FOCUS

## 1. Principles
Local-first (the app is fully functional offline with no account) · persisted absolute timestamps, never in-memory counters · one policy engine decides "block or allow", many detectors feed it · no third-party analytics/ads/billing · optional self-hostable backend only for multiplayer and sync · every feature has a real execution path or is marked Planned.

## 2. Stack decision
| Layer | Choice | Why |
|---|---|---|
| Language/UI | Kotlin + Jetpack Compose (Material 3) | Same platform APIs as reference needs; best Accessibility/Usage/Overlay access |
| DI | Hilt | Matches Workers/Services well |
| Storage | Room (single DB, migrations tracked) + DataStore for prefs | Local-first, testable |
| Background | Foreground service (monitor), AlarmManager exact alarms (session end/schedules), WorkManager (housekeeping, export) | Each for what it is reliable at |
| Media | Media3 | Background audio |
| Network (optional) | Ktor client + kotlinx.serialization | Light, no codegen |
| Backend (optional, P3) | Supabase (Postgres + Realtime + RLS) or self-hosted equivalent via Docker | Open-source, self-hostable. Hosted free tier has limits (to be checked at launch time) |
| AI (optional) | `LlmProvider` interface: Ollama/OpenAI-compatible endpoint, user-supplied key, or none | No mandatory paid API; rule-based coach works offline |
| Desktop/web (P4) | Tauri + React reading the same sync API | Android-specific blocking is NOT portable |
| minSdk/targetSdk | 26 / 35 (raise target as Play requires) | Reference uses 24 / 36; 26 gives reliable notification channels + simpler foreground-service rules |
| License | Apache-2.0 (proposed, to confirm with owner) | OSI-approved, patent grant |

## 3. Module layout (Gradle multi-module)
```
:app                     entry, navigation, DI wiring, manifest
:core:model              pure Kotlin data types (JVM) — no Android deps
:core:engine             PURE-KOTLIN logic, unit-testable on JVM:
                         SessionStateMachine, PolicyEngine, UsageSessionizer,
                         StreakCalculator, ScheduleExpander, StudyPlanner
:core:database           Room entities/DAOs/migrations
:core:platform           Android adapters: UsageEvents source, Accessibility bridge,
                         Overlay controller, Alarm scheduler, Notification factory
:feature:timer  :feature:blocking  :feature:strict  :feature:limits
:feature:schedule  :feature:stats  :feature:study(yt/browser/web)  :feature:coach
:feature:rooms (P3)  :feature:settings  :feature:onboarding
:service:monitor         foreground monitor + accessibility service
:backend/                optional server (P3) + docker-compose
```
Rationale: the hard logic (timers, strict rules, policy decisions, usage sessionising) lives in `:core:engine` as pure Kotlin so it can be tested on a laptop without a device.

## 4. Key designs

### 4.1 Session state machine (persistent timers)
State = `Idle | Running | Paused | OnBreak | Completed | Abandoned`. Persisted row: `id, mode, plannedDurationMs, startedAtEpoch, accumulatedFocusMs, lastResumeEpoch, state, breakEndsAtEpoch, breaksAllowed, breaksTaken, rounds, strictLevel, tagId, note`. Elapsed = `accumulated + (now - lastResume)` computed on read; wall-clock jumps are detected using both `System.currentTimeMillis()` and `SystemClock.elapsedRealtime()` anchors (reboot invalidates elapsedRealtime, so on boot we fall back to wall-clock and flag the session "time-adjusted"). Session end is an exact alarm; the foreground service only renders UI/notification. A session is "verified" for rewards only if it ran uninterrupted per these timestamps and was not time-adjusted beyond a tolerance.

### 4.2 Policy engine
Inputs: `ForegroundApp(pkg, ts)`, `InAppContext(pkg, section: REELS|SHORTS|SPOTLIGHT|NONE)`, `WebContext(browser, domain)`, active session, schedules, app limits, allowlists. Output: `Allow | Block(reason, canSnooze, canEmergency)`. Pure function + clock injection ⇒ fully unit-tested. Detectors never decide; they only report facts.

### 4.3 Detection
- Foreground app: AccessibilityService `TYPE_WINDOW_STATE_CHANGED` as primary (low latency, event-driven) with UsageEvents polling fallback when accessibility is off (battery-conscious interval, adaptive when screen off = paused).
- Reels/Shorts: rule files (`assets/rules/*.json`) describing per-app, per-version node signals (view-id, content-description, class, bounds heuristics). Our own detector design: (1) screen-section signature from view ids/descriptions, (2) full-screen vertical pager heuristic from bounds, (3) scroll counting via change of the visible-content fingerprint. Rules are user-updatable (import/export) because app UIs change. Never read text fields, keystrokes, or messages; node walk capped and skipped for password/editable nodes.
- Website: browser URL-bar node by id map (data-driven), domain extraction only; second optional method = local VPN DNS filter (P3; battery/privacy cost explained in UI).
- Strict protection: accessibility detects Settings pages for our app (app info, accessibility, device admin, uninstall dialog) and shows cover screen while a strict session is active.

### 4.4 Persistence
Room tables (planned): `session`, `session_event` (pause/resume/break/early-exit log), `preset`, `tag`, `schedule`, `schedule_occurrence`, `app_rule`, `app_limit`, `limit_usage_day`, `blocked_domain`, `allow_channel`, `usage_event`, `usage_session`, `hourly_usage`, `streak_day`, `achievement`, `coach_message`, `coach_feedback`, `room_cache` (P3). Exportable JSON.

### 4.5 Strict mode honesty
We implement: locked session UI, hidden/delayed end button, configurable cooldown + limited breaks + emergency exit that is logged, settings-page cover, optional device admin lock. We do **not** claim uninstall prevention; see KNOWN_LIMITATIONS. Optional hardening path for power users: Device Owner / Android Enterprise provisioning (documented, advanced).

### 4.6 Multiplayer backend (P3)
Tables: `profiles, rooms, room_members, focus_sessions_reported, weekly_rank`. Clients submit session start/end **events**; server computes duration from its own receive times and clamps to plausible bounds; leaderboards derive from server-computed values only; RLS + per-user rate limits. Anonymous/pseudonymous auth; no phone numbers.

### 4.7 Privacy/security
No analytics SDKs, no ad ID, no remote config. Accessibility data processed in-memory, only the matched rule result is acted on or stored (counts, not content). Network is opt-in and off by default. API keys stored in Android Keystore-backed encrypted storage; none in repo. Permission disclosure screens before each special-access request.

## 5. Build/verification plan
Pure-JVM tests for `:core:engine` (JUnit) run in CI; instrumented tests for services; GitHub Actions builds debug APK; release signing via user-provided keystore (secrets in CI, not repo).
