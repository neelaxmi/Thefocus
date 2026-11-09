# Android setup and permissions

Use JDK 17 and Android Studio with Android SDK platform 35. Open the repository root as a Gradle project and allow Gradle sync.

The app declares notification, foreground-service, boot-receiver and exact-alarm permissions. Android versions and OEMs may handle these differently. Request runtime notification permission where applicable and guide the user to system settings for exact alarms or battery optimization when required.

The manifest intentionally has no INTERNET permission in this foundation build. Verify the permission declarations and foreground-service type against current Play policy before distribution.
