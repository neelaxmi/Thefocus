# PROJECT_ANALYSIS — Regain (ai.regainapp) reverse-engineering report

Evidence legend: **[C]** confirmed in decompiled code / manifest / assets · **[D]** reasonable deduction · **[U]** unverified (needs dynamic testing or screenshots).

## 1. Scope and method
- Source: `neelaxmi/Focus` = extracted contents of one Regain APK (4,350 files). **Preserved untouched.** No original source exists there; only compiled artifacts.
- Tools: androguard (binary manifest/resources), JADX 1.5.1 run per DEX (`classes`..`classes4`), 29,031 Java files produced (13,607 / 145 / 10,594 / 4,685 per DEX). JADX reported failures on a small number of methods (154 log errors); those methods are unreadable ("decompiled incorrectly") and any claim about them is [U].
- Not done: no emulator, no ADB, no dynamic testing, no network capture. Everything below is static analysis plus public store pages.
- Version analysed: **73.6.2089** (versionCode 2089), minSdk 24, targetSdk 36. Public stores list older builds (59.x / 62.x), so the APK is newer than most listings.
- Code is R8-obfuscated, but app packages `ai.blox100.*` keep readable feature-module, entity and Room-table names. Helper/utility classes are obfuscated (`defpackage.c`, single-letter packages).

## 2. Product identity (public sources)
- Developer: EpowerX Labs Private Limited, Bengaluru. Package `ai.regainapp`; internal namespace `ai.blox100`.
- Positioning: study-focused app blocker + focus timer for students; originally a screen-time/app-limit app, pivoted to a study timer (community reports). Paid tiers exist (see §7).
- Store claims: focus timer with music, allowlist ("Allowed Apps"), YouTube Study Mode, Reels/Shorts blocking, scheduling with emergency minutes, App Limits/streaks/insights, multiplayer focus rooms + leaderboards, cooldown when ending a session.

## 3. Architecture of the reference app [C]
- Kotlin, Jetpack Compose UI (+ Navigation 3), Hilt DI (incl. hilt-work), Room (9 databases), WorkManager, DataStore, CameraX, Credentials/Google sign-in, Play Billing, Firebase (Messaging, Analytics, Remote Config), Mixpanel + PostHog tokens in remote-config defaults, OpenTelemetry flag, in-house ad system flags.
- Feature-module package layout (`feature_*`): ai_coach, app_category, app_limit, app_usage_stats, block_shorts, block_website, c68, control_apps, creator_mode, distracting_apps, focus_timer, invite_flow, language, network_logs, notification_batching, onboarding, permission, phone_lock_unlock_counter, productive_mode, profile_stats, regain_api, regain_launcher, service_fixing, study_goal_home, subscription, user_signin, user_theme, wall_of_love, zen_mode, plus `widget`, `floating_bubble`, `services`, `broadcastReceivers`.
- Remote config: **277 keys** in `rc_defaults.xml` (timeouts, intervals, break rules, thresholds, offers/experiments). Behaviour is heavily server-tunable.
- Backend: base URL `https://api.regainapp.ai`; **101 distinct endpoints** catalogued in `evidence/regain_backend_endpoints.txt` (auth/backup, groups `fg/*`, stats/leaderboards, payments, YouTube channel meta `pm/*`, website/app category, invites, themes, usage sync, notification tags, study stamps, wall of love).

## 4. How the hard features actually work [C unless noted]

### 4.1 Foreground-app detection and blocking
- `MyMonitorService` (foreground service, `stopWithTask=false`) polls recent app events; log strings show `recentAppEvent … eventType … usageTimeStamp`, i.e. **UsageStatsManager event polling** [C for the pattern, D for exact API calls — helper classes obfuscated]. Interval and heartbeat values come from remote config (`CHECK_APP_INTERVAL_IN_MS`, `SERVICE_HEARTBEAT_CHECK_IN_MS`, …).
- Block UI = full-screen overlay activities (`OverlayActivity`, `FTOverlayActivity`, `BlockShortsOverlayActivity`, `PMTagOverlayActivity`, `PermissionStepsOverlayActivity`) using SYSTEM_ALERT_WINDOW + USE_FULL_SCREEN_INTENT.
- `service_fixing` + heartbeat tables detect when the OS (OEM battery managers) has killed the services and prompt the user [C]. OEM-specific permission walkthrough animations exist for Redmi and Samsung [C].

### 4.2 Reels / Shorts / Spotlight blocking
- Implemented in `MyMonitorInAppBlockService` (AccessibilityService, `canRetrieveWindowContent=true`, all event types, 500 ms timeout) [C].
- Walks the accessibility node tree (hard cap **2,000 nodes**), builds node wrappers with bounds/midpoints [C].
- Detects "a reel scrolled" by taking a digest of numeric labels (like counts) visible on screen and comparing consecutive snapshots with a tolerance of ±2; a changed digest increments `reelsScrolledCount` [C]. Also has "first reel" detection and a "doom scroll" threshold/wait set via remote config [C].
- Targets by package: YouTube, Instagram (+Lite), Facebook (+Lite), Snapchat [C]. Uses specific view ids for YouTube player/miniplayer and Instagram comment field [C]. The service contains a `performGlobalAction(1)` (GLOBAL_ACTION_BACK) call and block-overlay windows [C]; which one fires for which trigger is [D].
- Fragility [D]: depends on third-party view ids/structures that change per app release; Regain mitigates with remote config and a debug "DUMP_YT_NODES" capture tool into a `yt_corpus` directory [C].

### 4.3 Website blocking
- **No VpnService in the manifest** [C]. Websites are read by the accessibility service from each browser's URL-bar view via a per-browser view-id map (Chrome, Brave, Opera, Firefox, Edge, DuckDuckGo, Samsung Internet, others) [C]; the URL field can be cleared via the browser's clear button [C].
- Domain category lookups call `/wb/get_website_category` and `/wb/get_website_search_recommendations` [C].

### 4.4 Strict Mode / "uninstall protection" (the honest picture)
- Device-admin receiver declares only the `force-lock` policy [C]. `onDisableRequested` returns a warning message only for Pro users [C].
- "Uninstall protection" is **not OS-level**. The accessibility service checks whether the user opened a *protected settings page* (matched via a text/id map) while protection is on, and if so shows a block-protection screen (`showBlockProtectionScreen`) [C]. Protection flag sources: config `c68UninstallProtection` or a session with `isUninstallBlocked` [C].
- Strict-related persisted fields: `isLockedIn`, `disableEndButtonShown`, `isLauncherBlocked`, `isUninstallBlocked`, `blockNotifications`, `c68StrictMode`, `c68AlarmEnabled`, `c68BlockLauncher`, `strictModeExpiryTime` (per app limit) [C].
- Ending a session has a server-tunable wait (`ZEN_MODE_END_SESSION_WAIT_TIME_IN_SECS`, `FT_END_SESSION_WAIT_TIME`) and limited breaks (`ZEN_MODE_MAXIMUM_BREAKS_ALLOWED_COUNT`) [C].
- Implication: a determined user can still uninstall via ADB, safe mode, a different settings route, or by revoking accessibility from a path the map misses. Our docs must say so (see KNOWN_LIMITATIONS).

### 4.5 Persistent timers [C]
- Session entity stores absolute timestamps and durations, not a counter: `startTime`, `durationInMillis`, `breakDurationInMillis`, `breakExpiryTime`, `breaksAllowedCount`, `breaksTakenCount`, `roundsPerCycle`, `longBreakDurationInMillis`, `tagId`, `updatedAt`. Config remembers last-used timer/stopwatch/pomodoro settings and last music.
- Receivers exist for `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED` and alarm-restore (`C68AlarmRestoreReceiver`) [C]; that they re-arm the active session/alarms is [D] (handler bodies not read). Exact alarms (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`) + a dedicated alarm service/receivers for scheduled sessions [C].

### 4.6 Usage analytics
- Room `AppUsageDatabase`: `AppUsage`, `EventData`, `HourlyAppUsage`, `installed_apps_meta`, `UserAppCategory`, `RegainAppCategory`, `app_usage_today`, `BeforeRegainUsage`, `UserTotalUsage` [C]. Config keys show a session-reconstruction algorithm over raw events (min idle/active durations, skip threshold, previous/next-N events) [C]. Usage is periodically uploaded (`/usage/submit`, `/usage/get_sync_time`) [C] — we will not replicate uploads.
- Offline resource `app_category_offline.json` maps apps to categories [C]; online `/app_category/get_app_category` supplements it.

### 4.7 YouTube Study Mode ("productive mode")
- Tables: tag data, events, app usage, allowed keywords, channel metadata, whitelisted keywords, channel events, website metadata, allowed websites [C]. Allowed channels/keywords/websites; suggested channels from `/get_productive_channels`; channel metadata via backend (`/pm/*`) [C]. Channel name/handle is read from the YouTube UI by accessibility (`lastProcessedChannelName`, channel-categorise jobs) [C]. Backend categorises channels as productive [D].
- Remote-config limits exist for free users (`STUDY_CHANNELS_LIMIT_ENABLED_V1`, `BLOCK_WEBSITES_LIMIT_ENABLED_V1`) [C]; a custom in-app browser exists (`CustomBrowserActivity`, "ultra browser study") [C/D].

### 4.8 Other confirmed subsystems
- **Multiplayer**: groups with categories, invites/deep links, suggested groups, live focusing counts, weekly leaderboards/ranks, reactions, "wake-up" nudges for inactive members, kick/leave, pinned groups [C]. All server-side (`/fg/*`).
- **Notification batching**: NotificationListener + 5 Room tables (meta, user tags, channel meta, history, current tag) [C]; digest/inbox UI and per-app grouping rules [D].
- **Custom launcher block** (`feature_regain_launcher`, `isLauncherBlocked`) [C].
- **App limits**: per-app daily limits, difficulty levels, snooze, "one more minute", pause, streaks/goals, strict-mode expiry, overlay + game-style rewards (`GameReward`, `CoinLedger`) [C].
- **Widgets**: usage (2 sizes), focus, study goal, "rega goal", study calendar, phone-unlock count, pro-pass (2 sizes) [C].
- **AI Coach**: see §5. **Onboarding**: 816-exam search list (`exam_search_in.json`), avatar art set (`peeps`) [C].
- **Backup/sign-in**: Google sign-in; backup/restore/migrate endpoints; `RestoreBackupService` [C].
- **Monetisation**: Play Billing + server payment endpoints, offer experiments, pro pass, free trial, in-house ads flags [C]. Offering identifiers resemble a third-party billing SDK; unverified [U].

## 5. AI Coach asset (`assets/AICoach.db`) [C]
- Room DB, table `AICoachResponseData`, **732 rows**; PK = (gpt_model, app_id, app_type, usage_minutes_bucket, time_of_day_bucket, ai_response). Columns also hold category, length, approved flag, shown/liked/disliked counters (global and per-user), `action_json`, timestamps.
- Distribution: categories casual 368, small_action_reminders 100, encouragement 100, action 63, facts 51, joke 50; time buckets night/morning/evening; usage buckets low/high; 710 generic rows, 22 social-media rows; response length 29–127 chars (avg ≈64); 688 approved.
- Conclusion: the shipped "AI coach" is a **pre-generated, curated message bank selected by context buckets with feedback counters**, refreshed from a server (`AI_COACH_PROMPT_DB_UPDATE_*`, prompt text in remote config). **No on-device model.** The `gpt_model` column suggests server rows were LLM-generated [D]. We will not copy the text (their content); we will author an original bank and add an optional LLM layer.

## 6. Permissions and what each is used for [C]
PACKAGE_USAGE_STATS (usage/foreground), QUERY_ALL_PACKAGES (app list), SYSTEM_ALERT_WINDOW + USE_FULL_SCREEN_INTENT (overlays), BIND_ACCESSIBILITY_SERVICE (in-app/website blocking, protection), BIND_NOTIFICATION_LISTENER (batching / FT notification block), BIND_DEVICE_ADMIN force-lock, POST_NOTIFICATIONS, SCHEDULE/USE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE (+MEDIA_PLAYBACK, +SPECIAL_USE), REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, WAKE_LOCK, VIBRATE, CAMERA (creator mode/QR/profile [D]), READ_BASIC_PHONE_STATE, INTERNET/ACCESS_NETWORK_STATE, billing/ads-id/ad-services/install-referrer (monetisation + attribution; **we omit all of these**).
Google Play note: AccessibilityService, QUERY_ALL_PACKAGES, and SYSTEM_ALERT_WINDOW require Play policy declarations; our primary distribution will be GitHub/F-Droid-style APKs.

## 7. Free vs paid [C/D]
Pro/"Ultra" gating is controlled by remote-config version-code cutoffs: block launcher, productive mode & blocks, focus schedule, premium assets, website/channel limits, ultra browser study, ultra focus. Device-admin disable warning is Pro-only. Our product ships all of these ungated.

## 8. Privacy observations about the reference
It uploads usage data, uses Mixpanel/PostHog/Firebase Analytics, ad IDs, and in-house ads. NEELAXMI will be local-first with no third-party analytics, no ad SDKs, and opt-in sync only.

## 9. Unverified / needs dynamic evidence
Exact UsageStats call pattern; exact accessibility rule data for Reels/Shorts per app (partly remote-driven); billing SDK identity; screen-by-screen UI flows and copy; paywall boundaries; behaviour of server-driven rules. Screenshots/screen recordings of the real app would close most of these.
