# Technical architecture

## Layers
- `core-engine/`: Android-independent Kotlin state machine, clock handling, policies, usage sessionization, streaks and dashboard calculations.
- `app/data/`: Room entities, DAO, database and session-store adapter.
- `app/platform/`: Android alarms, foreground service, notification presenter, permission helpers, receivers and settings.
- `app/ui/`: Jetpack Compose screens, view model, timer ticker and theme.

## Session lifecycle
The persisted Session is the source of truth. Every controller entry point catches the stored session up against a paired wall/monotonic clock reading, persists transitions, re-arms the next wake-up and updates the notification. The service itself does not own timer state.
