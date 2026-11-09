package com.neelaxmi.focus.core

enum class Mode { TIMER, STOPWATCH, POMODORO }
enum class Phase { FOCUS, SHORT_BREAK, LONG_BREAK, USER_BREAK }
enum class SessionState { RUNNING, PAUSED, COMPLETED, ABANDONED }

data class PomodoroConfig(
    val focusMs: Long,
    val shortBreakMs: Long,
    val longBreakMs: Long,
    val roundsPerCycle: Int,
    val cycles: Int,
) {
    init {
        require(focusMs > 0 && shortBreakMs >= 0 && longBreakMs >= 0) { "durations" }
        require(roundsPerCycle >= 1 && cycles >= 1) { "rounds/cycles" }
    }
}

/** Optional user-taken breaks for TIMER / STOPWATCH sessions. */
data class BreakRules(val allowed: Int, val durationMs: Long)

/**
 * Strict ("locked in") rules. Ending requires either waiting [endCooldownMs] after requesting the end,
 * or an emergency exit (only if [emergencyExitsLeft] > 0; the caller records the consumption).
 * Pausing is not possible in strict mode.
 */
data class StrictRules(val endCooldownMs: Long, val emergencyExitsLeft: Int)

/**
 * Entire persisted state of a focus session. Everything needed to rebuild the timer after process death or reboot
 * is in here; no in-memory counter is authoritative.
 */
data class Session(
    val id: Long,
    val mode: Mode,
    val state: SessionState,
    val phase: Phase,
    val plannedMs: Long?,                 // TIMER only
    val pomodoro: PomodoroConfig?,        // POMODORO only
    val breakRules: BreakRules,
    val strict: StrictRules?,
    val startedAtWallMs: Long,
    val endedAtWallMs: Long?,
    val focusMs: Long,                    // focus time already flushed (excludes the running FOCUS phase)
    val phaseElapsedMs: Long,             // elapsed inside the current phase as of [lastTick]
    val round: Int,                       // pomodoro, 1-based
    val cycle: Int,                       // pomodoro, 1-based
    val breaksTaken: Int,
    val lastTick: Tick,                   // reference point for the running phase
    val endRequestedTick: Tick?,          // strict: when the user asked to end
    val clockAdjusted: Boolean,
    val rebootCount: Int,
    val tagId: Long? = null,
) {
    /** Total focus time as of the last tick. Call SessionMachine.advance(now) first for a live value. */
    val focusTotalMs: Long get() = focusMs + if (phase == Phase.FOCUS) phaseElapsedMs else 0L

    val isFinished: Boolean get() = state == SessionState.COMPLETED || state == SessionState.ABANDONED

    /**
     * A session earns rewards/streak credit only if it ran to completion (or a stopwatch was stopped normally)
     * and the clock was not tampered with.
     */
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
