# ANDROID_SETUP_AND_PERMISSIONS

## Build
1. JDK 17 and Android SDK (platform 35, build-tools 35). Android Studio Ladybug or newer opens the project directly.
2. `./gradlew :core-engine:test` — engine tests (pure JVM).
3. `./gradlew :app:assembleDebug` — debug APK at `app/build/outputs/apk/debug/`.
4. `./gradlew :app:connectedDebugAndroidTest` — instrumented tests (emulator/device).
CI (`.github/workflows/ci.yml`) does all of this and uploads the debug APK as an artifact. **The first CI run is the first real build of the Android module** (see TESTING_REPORT).

## Permissions this milestone declares
| Permission | Why | User action |
|---|---|---|
| `POST_NOTIFICATIONS` (Android 13+) | Show the running countdown and "break/finished" alerts | Runtime prompt on first Start, or Settings → Reliability → Notifications |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the session process alive while the screen is off | None (install-time). Play requires a "special use" declaration; the manifest property explains the use |
| `USE_EXACT_ALARM` (API 33+) / `SCHEDULE_EXACT_ALARM` (API 31–32) | End the phase on time even in Doze | API 33+: granted at install. API 31–32: Settings → Reliability → Exact alarms → allow. Without it the app uses an inexact alarm that can fire minutes late |
| `RECEIVE_BOOT_COMPLETED` | Re-arm a running session after reboot / app update / clock or zone change | None. It runs only **after the user unlocks** the phone after boot |
| *(none for network)* | The app declares **no INTERNET permission** | — |

## Manual reliability setup (shown in Settings → Reliability)
- **Notifications**: if denied, the session still runs and ends correctly (the foreground service and alarm do not depend on it) but you will not see the countdown or alerts.
- **Exact alarms**: recommended on Android 12–13.
- **Unrestricted battery use**: recommended. Opens the system list; the app does not request the restricted `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission.
- **OEM autostart/battery managers (Xiaomi MIUI/HyperOS, Oppo/Realme ColorOS, Vivo, Samsung, Huawei, OnePlus)**: these can kill the app regardless of standard settings. If timers end late or the notification vanishes, allow Autostart / "No restrictions" for this app in the vendor's own battery or security app. Guidance by vendor is a tracked item (`dontkillmyapp.com` is a good external reference). The app cannot detect or fix this automatically.

## Behaviour you should know
- The timer is **not** an in-memory counter: the session is stored with start/phase timestamps; the notification countdown is drawn by the system; the UI re-derives time every second from stored state.
- After a **reboot** the session continues by wall-clock time (time the phone was off counts) and is flagged. It resumes once the user unlocks the device (credential-encrypted storage).
- If the user **changes the clock**, elapsed time is measured with the monotonic clock; such a session is marked "clock changed" and does not count for streaks/goals.
- Strict mode here only governs *this app's session* (no pause, deliberate end wait, limited emergency exits). It does **not** yet block other apps; that arrives with the blocker phase.

## Milestone 2 setup (manual steps the user must do)
| Feature | Where | What the user does |
|---|---|---|
| App blocking, strict enforcement | Blocking tab → "Enable app blocking" | Read the disclosure, tap "Agree and open settings", then in Settings → Accessibility → NEELAXMI Focus switch it **on**. Android shows its own warning dialog. The app cannot enable it for you |
| Usage analytics | Insights tab → "Grant usage access" (or Settings → Reliability) | Settings → Usage access → NEELAXMI Focus → allow |
| Reliable schedules | Settings → Reliability → Exact alarms; Unrestricted battery | As in Milestone 1 |
| Phone-maker restrictions | Vendor battery/autostart screens | Allow autostart / "no restrictions" for this app; otherwise the accessibility service or alarms can be stopped |

**Permissions added in M2:** `PACKAGE_USAGE_STATS` (special access), `BIND_ACCESSIBILITY_SERVICE` (declared on the service; user-enabled), `<queries>` for launchable apps and home launchers (replaces the Play-restricted `QUERY_ALL_PACKAGES`). Still **no INTERNET permission**.

**What the accessibility service reads:** the foreground package and class name on window changes; window text only on stock Settings / package-installer screens while strict protection is on (in memory, to recognise this app's own pages). It reads no keystrokes, messages or passwords and stores/sends nothing.

**Play Store note:** AccessibilityService use needs a Play policy declaration; the primary distribution remains GitHub/F-Droid.

## Release signing (CI)
Add GitHub repository secrets `KEYSTORE_BASE64` (base64 of your .keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the `release` job still builds but produces an **unsigned** APK (artifact name ends in `-unsigned`). Never commit a keystore.
