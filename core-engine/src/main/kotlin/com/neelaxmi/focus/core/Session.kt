package com.neelaxmi.focus.core

enum class Mode { TIMER, STOPWATCH, POMODORO }
enum class Phase { FOCUS, SHORT_BREAK, LONG_BREAK, USER_BREAK }
enum class SessionState { RUNNING, PAUSED, COMPLETED, ABANDONED }

data class PomodoroConfig(
    val focusMs: Long, val shortBreakMs: Long, val longBreakMs: Long,
    val roundsPerCycle: Int, val cycles: Int,
) {
    init {
        require(focusMs > 0 && shortBreakMs >= 0 && longBreakMs >= 0) { "durations" }
        require(roundsPerCycle >= 1 && cycles >= 1) { "rounds/cycles" }
    }
}
data class BreakRules(val allowed: Int, val durationMs: Long)
data class StrictRules(val endCooldownMs: Long, val emergencyExitsLeft: Int)

data class Session(
    val id: Long, val mode: Mode, val state: SessionState, val phase: Phase,
    val plannedMs: Long?, val pomodoro: PomodoroConfig?, val breakRules: BreakRules,
    val strict: StrictRules?, val startedAtWallMs: Long, val endedAtWallMs: Long?,
    val focusMs: Long, val phaseElapsedMs: Long, val round: Int, val cycle: Int,
    val breaksTaken: Int, val lastTick: Tick, val endRequestedTick: Tick?,
    val clockAdjusted: Boolean, val rebootCount: Int, val tagId: Long? = null,
) {
    val focusTotalMs: Long get() = focusMs + if (phase == Phase.FOCUS) phaseElapsedMs else 0L
    val isFinished: Boolean get() = state == SessionState.COMPLETED || state == SessionState.ABANDONED
    val isVerifiedCompletion: Boolean get() = state == SessionState.COMPLETED && !clockAdjusted
}
enum class LogKind {
    STARTED, PAUSED, RESUMED, BREAK_STARTED, BREAK_ENDED, PHASE_CHANGED, COMPLETED,
    END_REQUESTED, EARLY_EXIT, EMERGENCY_EXIT, ABANDONED, CLOCK_ADJUSTED, REBOOT_DETECTED,
}
data class SessionLog(val atWallMs: Long, val kind: LogKind, val phase: Phase? = null)
enum class Reason {
    NOT_RUNNING, ALREADY_ENDED, STRICT_LOCKED, NO_BREAKS_LEFT, WRONG_MODE, COOLDOWN_NOT_ELAPSED, NO_EMERGENCY_EXITS,
}
sealed interface Step {
    data class Ok(val session: Session, val logs: List<SessionLog> = emptyList()) : Step
    data class Rejected(val reason: Reason) : Step
}
