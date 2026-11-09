# FEATURE_INVENTORY — what Regain does, how, and what it needs

Columns: **Where** = Regain module/class [C]; **Local** = works with no server; **Server** = needs Regain's backend; **Perms** = special Android access.
Our implementation never calls Regain's backend; "Server" rows are re-implemented on our own optional backend or locally.

| # | Feature | Where (Regain) | Local | Server | Perms | Edge cases / notes |
|---|---|---|---|---|---|---|
| 1 | Focus timer (timer / stopwatch / pomodoro) | `feature_focus_timer`, `FocusTimerSession*`, `FocusTimerConfig` | Yes | Stats sync only | Notifications, exact alarm, FGS | Timestamp-based; breaks allowed count; rounds per cycle; last-used settings remembered |
| 2 | Session tags, notes, emoji | `FTUserTag`, `tagId` | Yes | — | — | |
| 3 | Focus music | `MediaPlayerService`, `music_file_last_played` | Yes (bundled audio) | — | FGS media | Resume last track |
| 4 | Allowed-apps (allowlist) session | `FocusTimerAllowedApps`, `FocusTimerAppRestriction` | Yes | — | Usage access, overlay | Allowlist + distracting-app preselection list |
| 5 | Focus schedules (recurring) | `feature_zen_mode`, `ZenModeConfig` (days, start, duration, streak) | Yes | Optional | Exact alarm, boot | Templates, pause expiry, parent config |
| 6 | Strict mode ("locked in") | `isLockedIn`, `disableEndButtonShown`, `c68StrictMode` | Yes | Wait times via RC | Accessibility, overlay | End-session cooldown; limited breaks; emergency path |
| 7 | Uninstall/settings protection | `BlockProtectionActivity`, `checkAnOpenBlockProtectionOverlay`, `RegainDeviceAdminReceiver` | Yes | — | Accessibility, Device admin | NOT OS-level; screen covers protected Settings pages |
| 8 | Block launcher | `feature_regain_launcher`, `isLauncherBlocked` | Yes | — | Launcher role / accessibility [D] | Permission prompt thresholds |
| 9 | Block notifications during focus | `NotificationListener`, `blockNotifications` | Yes | — | Notification access | |
| 10 | App blocker / overlay | `MyMonitorService`, `OverlayActivity` | Yes | — | Usage access, overlay | Polling interval trade-off (battery vs latency) |
| 11 | App limits (daily) | `feature_app_limit`, `AppLimit` | Yes | — | Usage access, overlay | Difficulty, snooze, one-more-min, pause, streak, goal days |
| 12 | Reels/Shorts/Spotlight blocking | `MyMonitorInAppBlockService` | Yes | RC rules | Accessibility | Digest-of-counts scroll detection; per-app fragility |
| 13 | Doom-scroll thresholds | RC `DOOM_SCROLL_*` | Yes | RC | Accessibility | |
| 14 | Website blocking | `feature_block_website`, url-bar map | Yes (own list) | Category lookup | Accessibility | Browser-specific view ids; no VPN |
| 15 | YouTube Study Mode | `feature_productive_mode` | Partly | Channel meta/suggestions | Accessibility | Allowed channels/keywords/websites |
| 16 | Study browser | `CustomBrowserActivity` | Yes | — | — | |
| 17 | Usage stats & insights | `feature_app_usage_stats` | Yes | Upload (skip) | Usage access | Event→session reconstruction; hourly; before-vs-after |
| 18 | App categories | `app_category_offline.json`, `feature_app_category` | Yes | Online fill | — | |
| 19 | Phone unlock counter | `feature_phone_lock_unlock_counter` | Yes | — | — | |
| 20 | Study goal / calendar / planner | `feature_study_goal_home`, widgets | Yes | — | — | |
| 21 | Streaks, coins, rewards, study stamps | `GameReward`, `CoinLedger`, `/study_stamps` | Yes | Optional | — | Rewards only from verified sessions |
| 22 | Multiplayer focus groups | `/fg/*`, `Group*` tables | No | Yes | Network | Categories, invites, live counts, reactions, nudges, kick, ranks |
| 23 | Leaderboards | `/fg/stats/*` | No | Yes | Network | Server-side validation needed |
| 24 | Notification batching/digest | `feature_notification_batching` | Yes | Tags sync | Notification access | |
| 25 | AI Coach messages | `AICoachResponseData` | Yes (bank) | Bank refresh | — | Context-bucketed + like/dislike |
| 26 | Widgets (7 kinds) | `widget.*` | Yes | — | — | |
| 27 | Themes | `feature_user_theme` | Partly | Theme list | — | |
| 28 | Exam onboarding | `exam_search_in.json` (816 exams) | Yes | — | — | We author our own list |
| 29 | Backup/restore, sign-in | `feature_user_signin`, `/auth/backup/*` | — | Yes | Network | We offer local export + optional self-host |
| 30 | Service health ("fixing") | `feature_service_fixing`, heartbeats | Yes | — | Battery-opt exemption | OEM kill detection + guided fix |
| 31 | OEM permission guides | Lottie per Redmi/Samsung | Yes | — | — | |
| 32 | Invites/referrals/pro pass | `feature_invite_flow` | No | Yes | — | **Not carried over** (monetisation) |
| 33 | Subscriptions/paywall/ads/analytics | `feature_subscription`, Mixpanel, PostHog | — | Yes | — | **Intentionally excluded** |
