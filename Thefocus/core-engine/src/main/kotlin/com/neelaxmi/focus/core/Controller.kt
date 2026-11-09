package com.neelaxmi.focus.core

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

/** Persistence port. Implemented with Room on Android and in memory in tests. All calls are blocking. */
interface SessionStore {
    /** The RUNNING or PAUSED session, if any. */
    fun active(): Session?
    /** Upserts the session and appends the logs, atomically if the backend allows. */
    fun save(session: Session, logs: List<SessionLog>)
    fun allocateId(): Long
    fun finishedSince(sinceWallMs: Long): List<Session>
    fun countLogs(kind: LogKind, sinceWallMs: Long): Int
}

fun interface TickSource { fun now(): Tick }

/** Schedules ONE wake-up after [delayMs] of monotonic time (replaces any previous one). */
interface WakeScheduler {
    fun scheduleIn(delayMs: Long)
    fun cancel()
}

/** Everything the user can see or hear. Android implements it with a foreground service + notifications. */
interface SessionPresenter {
    /** A session is active (running or paused): show/refresh the persistent UI (starts the foreground service). */
    fun ongoing(session: Session)
    /** No active session any more: remove the persistent UI (stops the foreground service). */
    fun cleared()
    /** A phase boundary was crossed while time advanced (focus->break, break->focus, completion). */
    fun event(kind: LogKind, session: Session)
}

data class StartConfig(
    val mode: Mode,
    val plannedMs: Long? = null,
    val pomodoro: PomodoroConfig? = null,
    val breaks: BreakRules = BreakRules(0, 0),
    /** Non-null = strict mode with this end-cooldown. */
    val strictEndCooldownMs: Long? = null,
    val emergencyExitsPerDay: Int = 0,
    val tagId: Long? = null,
)

sealed interface Outcome {
    data class Applied(val session: Session) : Outcome
    data class Rejected(val reason: Reason) : Outcome
    data class Invalid(val message: String) : Outcome
    data object AlreadyActive : Outcome
    data object NoActive : Outcome
}

/**
 * The single orchestrator for focus sessions. NOT thread-safe by itself: callers must serialise access
 * (the Android app funnels every call through one single-thread executor).
 *
 * Invariant: the persisted [Session] is the only source of truth. Every entry point first catches the
 * session up to "now" ([sync]) so that after process death, reboot, clock changes or a late alarm the
 * state is rebuilt purely from stored timestamps.
 */
class SessionController(
    private val store: SessionStore,
    private val ticks: TickSource,
    private val wake: WakeScheduler,
    private val presenter: SessionPresenter,
    /** Evaluated on every use so a time-zone change while the process is alive is respected. */
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val listeners = CopyOnWriteArrayList<(Session?) -> Unit>()
    fun addListener(l: (Session?) -> Unit) { listeners += l }
    fun removeListener(l: (Session?) -> Unit) { listeners -= l }

    /** Catch up with the clock, persist, reschedule, present. Call on app start, boot, wake-up, time change. */
    fun sync(): Session? = catchUp(emit = true)

    private fun catchUp(emit: Boolean): Session? {
        val cur = store.active() ?: run {
            wake.cancel(); presenter.cleared(); if (emit) emit(null); return null
        }
        val now = ticks.now()
        val step = SessionMachine.advance(cur, now)
        store.save(step.session, step.logs)
        val s = step.session
        // one alert for the last boundary crossed, even if several were crossed during a long gap
        val boundary = step.logs.lastOrNull { it.kind == LogKind.PHASE_CHANGED || it.kind == LogKind.BREAK_ENDED || it.kind == LogKind.COMPLETED }
        afterChange(s, emit)
        boundary?.let { presenter.event(it.kind, s) }
        return if (s.isFinished) null else s
    }

    fun start(cfg: StartConfig): Outcome {
        if (catchUp(emit = false) != null) return Outcome.AlreadyActive
        val now = ticks.now()
        val strict = cfg.strictEndCooldownMs?.let { cooldown ->
            val used = store.countLogs(LogKind.EMERGENCY_EXIT, dayStartWallMs(now.wallMs))
            StrictRules(cooldown, (cfg.emergencyExitsPerDay - used).coerceAtLeast(0))
        }
        val id = store.allocateId()
        val s = try {
            when (cfg.mode) {
                Mode.TIMER -> SessionMachine.startTimer(id, cfg.plannedMs ?: return Outcome.Invalid("plannedMs required"), now, cfg.breaks, strict, cfg.tagId)
                Mode.STOPWATCH -> SessionMachine.startStopwatch(id, now, cfg.breaks, strict, cfg.tagId)
                Mode.POMODORO -> SessionMachine.startPomodoro(id, cfg.pomodoro ?: return Outcome.Invalid("pomodoro config required"), now, strict, cfg.tagId)
            }
        } catch (e: IllegalArgumentException) {
            return Outcome.Invalid(e.message ?: "invalid configuration")
        }
        store.save(s, listOf(SessionLog(now.wallMs, LogKind.STARTED, Phase.FOCUS)))
        afterChange(s)
        return Outcome.Applied(s)
    }

    fun pause() = op { s, now -> SessionMachine.pause(s, now) }
    fun resume() = op { s, now -> SessionMachine.resume(s, now) }
    fun startBreak() = op { s, now -> SessionMachine.startBreak(s, now) }
    fun endBreakEarly() = op { s, now -> SessionMachine.endBreakEarly(s, now) }
    fun requestEnd() = op { s, now -> SessionMachine.requestEnd(s, now) }
    fun cancelEndRequest() = op { s, _ -> Step.Ok(SessionMachine.cancelEndRequest(s)) }
    fun stop(emergency: Boolean = false) = op { s, now -> SessionMachine.stop(s, now, emergency) }

    /**
     * Ends the running session (saved like [stop]) and starts a fresh one with the same configuration.
     * Refused for strict sessions: restarting would be a way around the end-cooldown.
     */
    fun restart(): Outcome {
        val cur = catchUp(emit = false) ?: run { emit(null); return Outcome.NoActive }
        if (cur.strict != null) { emit(cur); return Outcome.Rejected(Reason.STRICT_LOCKED) }
        val cfg = StartConfig(cur.mode, cur.plannedMs, cur.pomodoro, cur.breakRules, null, 0, cur.tagId)
        val stopped = stop()
        if (stopped !is Outcome.Applied) return stopped
        return start(cfg)
    }

    /** Emergency exits still available today for a new strict session. */
    fun emergencyExitsUsedToday(): Int = store.countLogs(LogKind.EMERGENCY_EXIT, dayStartWallMs(ticks.now().wallMs))

    private fun op(f: (Session, Tick) -> Step): Outcome {
        val cur = catchUp(emit = false) ?: run { emit(null); return Outcome.NoActive }
        val now = ticks.now()
        return when (val st = f(cur, now)) {
            is Step.Rejected -> { emit(cur); Outcome.Rejected(st.reason) }
            is Step.Ok -> { store.save(st.session, st.logs); afterChange(st.session); Outcome.Applied(st.session) }
        }
    }

    private fun afterChange(s: Session, emit: Boolean = true) {
        if (s.isFinished) {
            wake.cancel(); presenter.cleared(); if (emit) emit(null)
            return
        }
        val remaining = if (s.state == SessionState.RUNNING) SessionMachine.remainingMs(s) else null
        if (remaining != null) wake.scheduleIn(remaining.coerceAtLeast(1)) else wake.cancel()
        presenter.ongoing(s)
        if (emit) emit(s)
    }

    private fun emit(s: Session?) { listeners.forEach { it(s) } }

    private fun dayStartWallMs(wallMs: Long): Long =
        zone().let { z -> Instant.ofEpochMilli(wallMs).atZone(z).toLocalDate().atStartOfDay(z).toInstant().toEpochMilli() }
}
