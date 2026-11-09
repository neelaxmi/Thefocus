package com.neelaxmi.focus.core

/** Pure, clock-injected state machine. No Android, no I/O: every function takes `now` and returns a new Session. */
object SessionMachine {

    fun startTimer(id: Long, plannedMs: Long, now: Tick, breaks: BreakRules = BreakRules(0, 0), strict: StrictRules? = null, tagId: Long? = null): Session {
        require(plannedMs > 0) { "plannedMs" }
        return base(id, Mode.TIMER, now, breaks, strict, tagId).copy(plannedMs = plannedMs)
    }

    fun startStopwatch(id: Long, now: Tick, breaks: BreakRules = BreakRules(0, 0), strict: StrictRules? = null, tagId: Long? = null): Session =
        base(id, Mode.STOPWATCH, now, breaks, strict, tagId)

    fun startPomodoro(id: Long, cfg: PomodoroConfig, now: Tick, strict: StrictRules? = null, tagId: Long? = null): Session =
        base(id, Mode.POMODORO, now, BreakRules(0, 0), strict, tagId).copy(pomodoro = cfg)

    private fun base(id: Long, mode: Mode, now: Tick, breaks: BreakRules, strict: StrictRules?, tagId: Long?) = Session(
        id = id, mode = mode, state = SessionState.RUNNING, phase = Phase.FOCUS,
        plannedMs = null, pomodoro = null, breakRules = breaks, strict = strict,
        startedAtWallMs = now.wallMs, endedAtWallMs = null, focusMs = 0, phaseElapsedMs = 0,
        round = 1, cycle = 1, breaksTaken = 0, lastTick = now, endRequestedTick = null,
        clockAdjusted = false, rebootCount = 0, tagId = tagId,
    )

    /** Length of the current phase, or null if unbounded (stopwatch focus). */
    fun phaseLengthMs(s: Session): Long? = when (s.phase) {
        Phase.FOCUS -> when (s.mode) {
            Mode.TIMER -> s.plannedMs!! - s.focusMs
            Mode.STOPWATCH -> null
            Mode.POMODORO -> s.pomodoro!!.focusMs
        }
        Phase.SHORT_BREAK -> s.pomodoro!!.shortBreakMs
        Phase.LONG_BREAK -> s.pomodoro!!.longBreakMs
        Phase.USER_BREAK -> s.breakRules.durationMs
    }

    /** Remaining time in the current phase (null for stopwatch focus). Valid right after [advance]. */
    fun remainingMs(s: Session): Long? = phaseLengthMs(s)?.let { (it - s.phaseElapsedMs).coerceAtLeast(0) }

    /**
     * Catches the session up to [now]. Safe to call any number of times, from any process, after any gap
     * (including a reboot): it processes as many phase transitions as the elapsed time implies.
     */
    fun advance(s: Session, now: Tick): Step.Ok {
        if (s.state != SessionState.RUNNING) return Step.Ok(s)
        val logs = mutableListOf<SessionLog>()
        val d = Ticks.delta(s.lastTick, now)
        if (d.clockAdjusted) logs += SessionLog(now.wallMs, LogKind.CLOCK_ADJUSTED)
        if (d.rebooted) logs += SessionLog(now.wallMs, LogKind.REBOOT_DETECTED)

        var cur = s.copy(
            phaseElapsedMs = s.phaseElapsedMs + d.ms,
            lastTick = now,
            clockAdjusted = s.clockAdjusted || d.clockAdjusted,
            rebootCount = s.rebootCount + if (d.rebooted) 1 else 0,
        )

        val guard = (s.pomodoro?.let { it.cycles * it.roundsPerCycle * 2 + 4 } ?: 8)
        var i = 0
        while (cur.state == SessionState.RUNNING && i++ < guard) {
            val len = phaseLengthMs(cur) ?: break
            if (cur.phaseElapsedMs < len) break
            val overflow = cur.phaseElapsedMs - len
            val endedAt = now.wallMs - overflow
            val (next, l) = completePhase(cur, len, overflow, endedAt)
            logs += l
            cur = next
        }
        return Step.Ok(cur, logs)
    }

    private fun completePhase(s: Session, len: Long, overflow: Long, endedAt: Long): Pair<Session, List<SessionLog>> = when (s.phase) {
        Phase.FOCUS -> when (s.mode) {
            Mode.TIMER -> s.copy(
                state = SessionState.COMPLETED, focusMs = s.plannedMs!!, phaseElapsedMs = 0, endedAtWallMs = endedAt,
            ) to listOf(SessionLog(endedAt, LogKind.COMPLETED, Phase.FOCUS))
            Mode.POMODORO -> {
                val cfg = s.pomodoro!!
                val focusDone = s.focusMs + len
                if (s.round == cfg.roundsPerCycle && s.cycle == cfg.cycles) {
                    s.copy(state = SessionState.COMPLETED, focusMs = focusDone, phaseElapsedMs = 0, endedAtWallMs = endedAt) to
                        listOf(SessionLog(endedAt, LogKind.COMPLETED, Phase.FOCUS))
                } else {
                    val next = if (s.round == cfg.roundsPerCycle) Phase.LONG_BREAK else Phase.SHORT_BREAK
                    s.copy(phase = next, focusMs = focusDone, phaseElapsedMs = overflow) to
                        listOf(SessionLog(endedAt, LogKind.PHASE_CHANGED, next))
                }
            }
            Mode.STOPWATCH -> error("stopwatch focus is unbounded")
        }
        Phase.SHORT_BREAK -> s.copy(phase = Phase.FOCUS, round = s.round + 1, phaseElapsedMs = overflow) to
            listOf(SessionLog(endedAt, LogKind.PHASE_CHANGED, Phase.FOCUS))
        Phase.LONG_BREAK -> s.copy(phase = Phase.FOCUS, round = 1, cycle = s.cycle + 1, phaseElapsedMs = overflow) to
            listOf(SessionLog(endedAt, LogKind.PHASE_CHANGED, Phase.FOCUS))
        Phase.USER_BREAK -> s.copy(phase = Phase.FOCUS, phaseElapsedMs = overflow) to
            listOf(SessionLog(endedAt, LogKind.BREAK_ENDED, Phase.FOCUS))
    }

    fun pause(s: Session, now: Tick): Step {
        if (s.isFinished) return Step.Rejected(Reason.ALREADY_ENDED)
        if (s.state != SessionState.RUNNING) return Step.Rejected(Reason.NOT_RUNNING)
        if (s.strict != null) return Step.Rejected(Reason.STRICT_LOCKED)
        val a = advance(s, now)
        if (a.session.isFinished) return a
        return Step.Ok(a.session.copy(state = SessionState.PAUSED), a.logs + SessionLog(now.wallMs, LogKind.PAUSED, a.session.phase))
    }

    fun resume(s: Session, now: Tick): Step {
        if (s.isFinished) return Step.Rejected(Reason.ALREADY_ENDED)
        if (s.state != SessionState.PAUSED) return Step.Rejected(Reason.NOT_RUNNING)
        // Paused time is intentionally not counted: restart the reference tick.
        return Step.Ok(s.copy(state = SessionState.RUNNING, lastTick = now), listOf(SessionLog(now.wallMs, LogKind.RESUMED, s.phase)))
    }

    fun startBreak(s: Session, now: Tick): Step {
        if (s.isFinished) return Step.Rejected(Reason.ALREADY_ENDED)
        if (s.mode == Mode.POMODORO) return Step.Rejected(Reason.WRONG_MODE)
        if (s.state != SessionState.RUNNING || s.phase != Phase.FOCUS) return Step.Rejected(Reason.NOT_RUNNING)
        if (s.breaksTaken >= s.breakRules.allowed) return Step.Rejected(Reason.NO_BREAKS_LEFT)
        val a = advance(s, now)
        if (a.session.isFinished) return a
        val c = a.session
        return Step.Ok(
            c.copy(phase = Phase.USER_BREAK, focusMs = c.focusMs + c.phaseElapsedMs, phaseElapsedMs = 0, breaksTaken = c.breaksTaken + 1),
            a.logs + SessionLog(now.wallMs, LogKind.BREAK_STARTED, Phase.USER_BREAK),
        )
    }

    fun endBreakEarly(s: Session, now: Tick): Step {
        if (s.phase != Phase.USER_BREAK || s.state != SessionState.RUNNING) return Step.Rejected(Reason.NOT_RUNNING)
        val a = advance(s, now)
        if (a.session.phase != Phase.USER_BREAK) return a
        return Step.Ok(a.session.copy(phase = Phase.FOCUS, phaseElapsedMs = 0), a.logs + SessionLog(now.wallMs, LogKind.BREAK_ENDED, Phase.FOCUS))
    }

    /** Strict mode: the user asks to end. Starts the cooldown; ending is only possible after it elapses. */
    fun requestEnd(s: Session, now: Tick): Step {
        if (s.isFinished) return Step.Rejected(Reason.ALREADY_ENDED)
        if (s.endRequestedTick != null) return Step.Ok(s)
        return Step.Ok(s.copy(endRequestedTick = now), listOf(SessionLog(now.wallMs, LogKind.END_REQUESTED)))
    }

    fun cancelEndRequest(s: Session): Session = s.copy(endRequestedTick = null)

    /**
     * Ends the session.
     * - STOPWATCH (normal): COMPLETED. TIMER/POMODORO ended early: ABANDONED (focus time so far is kept).
     * - Strict sessions need a matured end request, or [emergency] with exits left.
     */
    fun stop(s: Session, now: Tick, emergency: Boolean = false): Step {
        if (s.isFinished) return Step.Rejected(Reason.ALREADY_ENDED)
        val a = advance(s, now)
        if (a.session.isFinished) return a
        val c = a.session
        val logs = a.logs.toMutableList()

        if (c.strict != null) {
            if (emergency) {
                if (c.strict.emergencyExitsLeft <= 0) return Step.Rejected(Reason.NO_EMERGENCY_EXITS)
                logs += SessionLog(now.wallMs, LogKind.EMERGENCY_EXIT, c.phase)
            } else {
                val req = c.endRequestedTick ?: return Step.Rejected(Reason.STRICT_LOCKED)
                val waited = Ticks.delta(req, now)
                if (waited.ms < c.strict.endCooldownMs) return Step.Rejected(Reason.COOLDOWN_NOT_ELAPSED)
            }
        }

        val flushed = c.focusTotalMs
        val finalState = if (c.mode == Mode.STOPWATCH) SessionState.COMPLETED else SessionState.ABANDONED
        logs += if (finalState == SessionState.COMPLETED) SessionLog(now.wallMs, LogKind.COMPLETED, c.phase)
                else SessionLog(now.wallMs, LogKind.EARLY_EXIT, c.phase)
        return Step.Ok(c.copy(state = finalState, focusMs = flushed, phaseElapsedMs = 0, endedAtWallMs = now.wallMs), logs)
    }
}
