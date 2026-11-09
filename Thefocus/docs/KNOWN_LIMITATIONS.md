# KNOWN_LIMITATIONS (design-time, will be updated with test results)

1. **No app can be made impossible to uninstall on stock Android.** Regain itself only covers its own Settings pages and (Pro) warns on device-admin removal. A user can still use ADB, safe mode, or Settings paths not matched. Stronger options need Device Owner provisioning (factory-reset-level setup).
2. **Reels/Shorts blocking is inherently fragile.** It depends on other apps' internal view structures; updates can break detection. Mitigation: data-driven, user-updatable rules and a per-version test matrix. We will never advertise a section as supported unless tested.
3. **Website blocking via accessibility only works in supported browsers**, not in-app browsers or arbitrary apps. VPN-DNS filtering covers more but costs battery, conflicts with other VPNs, and can't see URL paths on HTTPS.
4. **Accessibility/Usage/Overlay/Notification access require manual user grants**; OEMs (Xiaomi, Samsung, Oppo, Vivo…) may kill background services. We include a guided fix flow but cannot guarantee uptime.
5. **Google Play** restricts AccessibilityService and QUERY_ALL_PACKAGES use; primary distribution is GitHub/F-Droid until a Play declaration is prepared.
6. **Multiplayer needs a server.** Self-hostable, but someone must run it; hosted free tiers have quotas.
7. **No iOS/desktop blocking parity**: desktop gets timer/stats/schedule/rooms only.
8. **Analysis limits**: static analysis only; server-driven rules and paywall behaviour of Regain were not observed dynamically.
9. **This sandbox cannot build Android apps** (no Android SDK / Google Maven access), so builds must run in CI or the owner's machine.

## Added in Milestone 1
10. **Timer after reboot starts only after the user unlocks the phone** (database is credential-encrypted; direct-boot is not used).
11. **Time while the phone is off counts toward the session** (wall clock is the only evidence after a reboot). It is flagged, not discarded.
12. **Exact end-of-phase timing needs the exact-alarm permission on Android 12–13**; otherwise an inexact alarm may be minutes late (the foreground service backs it up only while it is alive).
13. **Notification permission denied** (Android 13+) hides the countdown/alerts but does not stop the session.
14. **OEM battery managers can kill the app** despite foreground service + alarms; no API reveals this. Documented in-app.
15. **Strict mode currently governs only this app's own session**; it cannot yet stop the user opening other apps (blocker phase).
16. **A session that crosses midnight is attributed to the day it started** (dashboard simplification, documented in-app).
17. **Room schema export and migration tests are not enabled yet** (required before the first public release).
18. **`USE_EXACT_ALARM` is policy-restricted on Google Play** to alarm/timer-type apps; sideload/F-Droid distribution is unaffected.

## Added in Milestone 2
19. **Blocking works only through the Accessibility service**, enabled by the user. There is no fallback: without it nothing is blocked and the UI says so. (No `SYSTEM_ALERT_WINDOW` path was added: background activity launches are restricted on modern Android.)
20. **The block screen covers the app but does not stop it**: audio/video from a blocked app can keep playing underneath until the user taps "Go to home screen".
21. **Strict mode is a deliberate speed bump, not a lock.** Cannot be prevented: force-stop, revoking permissions or the accessibility grant, uninstalling (from the launcher, a PC/ADB, or in safe mode), factory reset, or using a vendor settings screen the guard does not recognise (the guard matches stock Settings/package-installer class names only). Within the app, group/schedule editing is locked while strict is active.
22. **Foreground detection is event-driven** (window-state changes). Split-screen, picture-in-picture and floating windows are not guaranteed. An app already on screen when a policy starts is re-checked on the policy change, not continuously.
23. **Usage retention:** Android keeps raw usage events for only a few days. Rollups are saved so reports survive, but history from before the app first ran cannot be recovered. The rollup assumes ≥ 5 days of OS retention; a device that keeps less could store one partial day as complete.
24. **Usage accuracy:** totals may differ slightly from system Digital Wellbeing (activities without pause events, multi-window). Unlock counts use `KEYGUARD_HIDDEN` (Android 9+); none are available on Android 8.x.
25. **Rollup cadence:** an inexact 6-hour alarm plus every app open/boot; Doze can delay it.
26. **Categories are heuristic:** public package-name lists plus the SDK's category hint. Mis-classification is possible; user groups override, and analytics "distracting" = SOCIAL/ENTERTAINMENT/GAMES categories ∪ enabled groups.
27. **Schedules:** exact timing needs the exact-alarm permission; an auto-started timer lasts for the rest of the window (max 12 h) and one start is made per occurrence even if the user ends it early; a strict schedule keeps blocking until its window ends (it has no session-style emergency exit).
28. **Achievement definitions are product decisions** (documented in MILESTONE_2_PLAN): "Distraction-free day" needs usage access; "Weekly champion" = daily goal on 5 of 7 days of a Mon–Sun week.
29. **Not implemented in Milestone 2:** break reminders at fixed intervals for plain timers (Pomodoro breaks are implemented), widgets, Reels/Shorts and website blocking, YouTube/browser study modes, AI coach, multiplayer, Hindi localisation.
30. **Persistence threading:** Room DAOs are blocking and run on one single-thread executor (serialises all state changes); coroutines/Flow are used only in the UI layer. Deliberate; differs from "suspend DAOs".
31. **"Glassmorphism" is translucent layered cards with a hairline border** (no real backdrop blur, which is not available on all supported API levels).
32. **Release APK is not minified** and is unsigned unless signing secrets are configured.
