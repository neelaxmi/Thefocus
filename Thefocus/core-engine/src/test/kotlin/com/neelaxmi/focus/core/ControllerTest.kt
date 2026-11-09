package com.neelaxmi.focus.core

import com.neelaxmi.focus.core.Harness.eq
import com.neelaxmi.focus.core.Harness.no
import com.neelaxmi.focus.core.Harness.test
import com.neelaxmi.focus.core.Harness.yes
import java.time.ZoneId

private const val MIN = 60_000L
private const val T0 = 1_800_000_000_000L

/** "Disk": survives a simulated process death because the next controller is built over the same store. */
class MemStore : SessionStore {
    val sessions = HashMap<Long, Session>()
    val logs = mutableListOf<Pair<Long, SessionLog>>()
    override fun active() = sessions.values.filter { !it.isFinished }.maxByOrNull { it.id }
    override fun save(session: Session, logs: List<SessionLog>) { sessions[session.id] = session; logs.forEach { this.logs += session.id to it } }
    override fun allocateId() = (sessions.keys.maxOrNull() ?: 0L) + 1
    override fun finishedSince(sinceWallMs: Long) = sessions.values.filter { it.isFinished && it.startedAtWallMs >= sinceWallMs }
    override fun countLogs(kind: LogKind, sinceWallMs: Long) = logs.count { it.second.kind == kind && it.second.atWallMs >= sinceWallMs }
}

class FakeTicks(var wall: Long = T0, var mono: Long = 10_000 * MIN) : TickSource {
    override fun now() = Tick(wall, mono)
    fun advance(ms: Long) { wall += ms; mono += ms }
    /** Device was off for [offMs]: wall clock moved, monotonic clock restarted near zero. */
    fun reboot(offMs: Long) { wall += offMs; mono = 3_000 }
}

class FakeWake : WakeScheduler {
    var delay: Long? = null; var cancels = 0
    override fun scheduleIn(delayMs: Long) { delay = delayMs }
    override fun cancel() { delay = null; cancels++ }
}

class FakePresenter : SessionPresenter {
    var ongoing: Session? = null; var cleared = 0
    val events = mutableListOf<LogKind>()
    override fun ongoing(session: Session) { ongoing = session }
    override fun cleared() { cleared++; ongoing = null }
    override fun event(kind: LogKind, session: Session) { events += kind }
}

private class Rig(val store: MemStore = MemStore(), val ticks: FakeTicks = FakeTicks()) {
    val wake = FakeWake(); val pres = FakePresenter()
    val c = SessionController(store, ticks, wake, pres) { ZoneId.of("UTC") }
    /** Simulates the OS killing the process: all in-memory objects are gone, only the store and clocks remain. */
    fun processDeath() = Rig(store, ticks)
}
private fun applied(o: Outcome): Session = (o as? Outcome.Applied ?: throw AssertionError("expected Applied, got $o")).session

fun controllerTests() {
    val timer25 = StartConfig(Mode.TIMER, plannedMs = 25 * MIN)

    test("controller: start persists, schedules wake for the remaining time, shows ongoing") {
        val r = Rig(); val s = applied(r.c.start(timer25))
        eq(s, r.store.active()); eq(25 * MIN, r.wake.delay); eq(s, r.pres.ongoing)
        yes(r.store.logs.any { it.second.kind == LogKind.STARTED })
    }
    test("controller: starting while a session is active is refused") {
        val r = Rig(); r.c.start(timer25); eq(Outcome.AlreadyActive, r.c.start(timer25))
    }
    test("controller: invalid config is rejected, nothing persisted") {
        val r = Rig()
        yes(r.c.start(StartConfig(Mode.TIMER)) is Outcome.Invalid); yes(r.c.start(StartConfig(Mode.TIMER, plannedMs = 0)) is Outcome.Invalid)
        yes(r.c.start(StartConfig(Mode.POMODORO)) is Outcome.Invalid); eq(null, r.store.active())
    }
    test("PROCESS DEATH: new process rebuilds the running timer from storage and re-arms the wake-up") {
        val r = Rig(); r.c.start(timer25); r.ticks.advance(10 * MIN)
        val r2 = r.processDeath()
        val s = r2.c.sync()!!
        eq(10 * MIN, s.focusTotalMs); eq(15 * MIN, r2.wake.delay); eq(s, r2.pres.ongoing)
    }
    test("wake-up at the end completes the session, alerts once, clears the notification") {
        val r = Rig(); r.c.start(timer25); r.ticks.advance(25 * MIN)
        eq(null, r.c.sync())
        eq(listOf(LogKind.COMPLETED), r.pres.events); eq(null, r.pres.ongoing); eq(null, r.wake.delay)
        val done = r.store.sessions.values.single(); eq(SessionState.COMPLETED, done.state); yes(done.isVerifiedCompletion)
    }
    test("alarm delivered hours late (Doze / dead process): session still ends at its true end time") {
        val r = Rig(); r.c.start(timer25); r.ticks.advance(5 * 60 * MIN)
        r.processDeath().c.sync()
        val done = r.store.sessions.values.single(); eq(T0 + 25 * MIN, done.endedAtWallMs); eq(25 * MIN, done.focusMs)
    }
    test("REBOOT: monotonic clock resets, session resumes from wall-clock time and is flagged") {
        val r = Rig(); r.c.start(timer25); r.ticks.advance(5 * MIN)
        r.ticks.reboot(offMs = 8 * MIN)                                  // off for 8 min
        val s = r.processDeath().c.sync()!!
        eq(1, s.rebootCount); eq(13 * MIN, s.focusTotalMs); no(s.clockAdjusted)
    }
    test("reboot after the planned end: completes on first sync") {
        val r = Rig(); r.c.start(timer25); r.ticks.reboot(offMs = 2 * 60 * MIN)
        val r2 = r.processDeath(); eq(null, r2.c.sync()); eq(listOf(LogKind.COMPLETED), r2.pres.events)
    }
    test("clock set forward by the user: session not shortened, completion not verified") {
        val r = Rig(); r.c.start(timer25)
        r.ticks.wall += 3 * 60 * MIN; r.ticks.mono += 2 * MIN
        val s = r.c.sync()!!; eq(2 * MIN, s.focusTotalMs); yes(s.clockAdjusted)
        r.ticks.advance(23 * MIN); r.c.sync()
        no(r.store.sessions.values.single().isVerifiedCompletion)
    }
    test("pause cancels the wake-up; resume re-arms it with the remaining time") {
        val r = Rig(); r.c.start(timer25); r.ticks.advance(10 * MIN)
        applied(r.c.pause()); eq(null, r.wake.delay); eq(SessionState.PAUSED, r.store.active()!!.state)
        r.ticks.advance(60 * MIN)                                          // long pause
        r.processDeath().c.sync(); applied(r.c.resume()); eq(15 * MIN, r.wake.delay)
    }
    test("pomodoro catch-up across several phases yields ONE alert and the right phase") {
        val cfg = PomodoroConfig(25 * MIN, 5 * MIN, 15 * MIN, 4, 2)
        val r = Rig(); r.c.start(StartConfig(Mode.POMODORO, pomodoro = cfg)); r.ticks.advance(70 * MIN)
        val s = r.c.sync()!!
        eq(Phase.FOCUS, s.phase); eq(3, s.round); eq(1, r.pres.events.size)   // 25+5+25+5 = 60, +10 into round 3
        eq(15 * MIN, r.wake.delay)
    }
    test("pomodoro break phase alerts as PHASE_CHANGED and schedules the break length") {
        val r = Rig(); r.c.start(StartConfig(Mode.POMODORO, pomodoro = PomodoroConfig(25 * MIN, 5 * MIN, 15 * MIN, 4, 1)))
        r.ticks.advance(25 * MIN); val s = r.c.sync()!!
        eq(Phase.SHORT_BREAK, s.phase); eq(5 * MIN, r.wake.delay); eq(listOf(LogKind.PHASE_CHANGED), r.pres.events)
    }
    test("stopwatch: no wake-up scheduled, stop saves the measured focus time") {
        val r = Rig(); r.c.start(StartConfig(Mode.STOPWATCH)); eq(null, r.wake.delay)
        r.ticks.advance(42 * MIN); applied(r.c.stop())
        eq(42 * MIN, r.store.sessions.values.single().focusMs); eq(null, r.pres.ongoing)
    }
    test("early stop of a timer: ends, clears, no completion alert, focus kept") {
        val r = Rig(); r.c.start(timer25); r.ticks.advance(7 * MIN); applied(r.c.stop())
        val d = r.store.sessions.values.single(); eq(SessionState.ABANDONED, d.state); eq(7 * MIN, d.focusMs); eq(emptyList<LogKind>(), r.pres.events)
    }
    test("operations without an active session report NoActive") { eq(Outcome.NoActive, Rig().c.pause()) }
    test("STRICT: pause refused; stop refused until cooldown after request; rejection leaves session intact") {
        val r = Rig(); r.c.start(StartConfig(Mode.TIMER, plannedMs = 60 * MIN, strictEndCooldownMs = 3 * MIN))
        eq(Outcome.Rejected(Reason.STRICT_LOCKED), r.c.pause()); eq(Outcome.Rejected(Reason.STRICT_LOCKED), r.c.stop())
        applied(r.c.requestEnd()); r.ticks.advance(1 * MIN); eq(Outcome.Rejected(Reason.COOLDOWN_NOT_ELAPSED), r.c.stop())
        r.ticks.advance(2 * MIN); eq(SessionState.ABANDONED, applied(r.c.stop()).state)
    }
    test("STRICT: strict state survives process death (cannot be shed by killing the app)") {
        val r = Rig(); r.c.start(StartConfig(Mode.TIMER, plannedMs = 60 * MIN, strictEndCooldownMs = 3 * MIN)); r.ticks.advance(5 * MIN)
        val r2 = r.processDeath(); eq(Outcome.Rejected(Reason.STRICT_LOCKED), r2.c.stop()); yes(r2.store.active()!!.strict != null)
    }
    test("STRICT: emergency exits per day are counted from persisted logs") {
        val cfg = StartConfig(Mode.TIMER, plannedMs = 60 * MIN, strictEndCooldownMs = 10 * MIN, emergencyExitsPerDay = 1)
        val r = Rig(); r.c.start(cfg); r.ticks.advance(1 * MIN); applied(r.c.stop(emergency = true))
        eq(1, r.c.emergencyExitsUsedToday())
        r.c.start(cfg); r.ticks.advance(1 * MIN)
        eq(Outcome.Rejected(Reason.NO_EMERGENCY_EXITS), r.processDeath().c.stop(emergency = true))
    }
    test("listeners get live updates and null when finished") {
        val r = Rig(); val seen = mutableListOf<SessionState?>(); r.c.addListener { seen += it?.state }
        r.c.start(timer25); r.c.pause(); r.c.resume(); r.ticks.advance(30 * MIN); r.c.sync()
        eq(listOf(SessionState.RUNNING, SessionState.PAUSED, SessionState.RUNNING, null), seen)
    }
    test("user break: wake-up is scheduled for the break end, then focus resumes") {
        val r = Rig(); r.c.start(StartConfig(Mode.TIMER, plannedMs = 60 * MIN, breaks = BreakRules(1, 10 * MIN)))
        r.ticks.advance(20 * MIN); applied(r.c.startBreak()); eq(10 * MIN, r.wake.delay)
        r.ticks.advance(10 * MIN); val s = r.c.sync()!!; eq(Phase.FOCUS, s.phase); eq(40 * MIN, r.wake.delay)
    }
}

fun dashboardTests() {
    val zone = ZoneId.of("UTC")
    val today = java.time.LocalDate.of(2026, 11, 4)
    fun sess(id: Long, daysAgo: Long, focusMin: Long, state: SessionState = SessionState.COMPLETED, adjusted: Boolean = false): Session {
        val start = today.minusDays(daysAgo).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        return SessionMachine.startTimer(id, 60 * MIN, Tick(start, 0)).copy(
            state = state, focusMs = focusMin * MIN, endedAtWallMs = start + focusMin * MIN, clockAdjusted = adjusted)
    }
    test("dashboard: today's minutes, goal progress, remaining, completed count") {
        val d = DashboardCalculator.compute(listOf(sess(1, 0, 30), sess(2, 0, 20, SessionState.ABANDONED)), 100, today, zone)
        eq(50, d.todayFocusMin); eq(0.5f, d.goalProgress); eq(50, d.remainingMin); eq(1, d.completedToday); eq(Motivation.MORE_THAN_HALF, d.motivation)
    }
    test("dashboard: tampered or unfinished sessions never count") {
        val running = SessionMachine.startTimer(9, 25 * MIN, Tick(today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), 0))
        val d = DashboardCalculator.compute(listOf(sess(1, 0, 90, adjusted = true), running), 60, today, zone)
        eq(0, d.todayFocusMin); eq(Motivation.START_TODAY, d.motivation)
    }
    test("dashboard: streak, week bars (7, oldest first), best day, weekly change") {
        val ss = listOf(sess(1, 0, 60), sess(2, 1, 60), sess(3, 2, 120), sess(4, 8, 60), sess(5, 9, 60))
        val d = DashboardCalculator.compute(ss, 60, today, zone)
        eq(3, d.streak.current); eq(7, d.week.size); eq(today, d.week.last().date); eq(today.minusDays(6), d.week.first().date)
        eq(240, d.weekTotalMin); eq(today.minusDays(2), d.bestDay!!.date); eq(100, d.weekChangePct); eq(Motivation.STREAK_ALIVE, d.motivation)
    }
    test("dashboard: no history gives zeros, no division errors, null change") {
        val d = DashboardCalculator.compute(emptyList(), 60, today, zone)
        eq(0, d.todayFocusMin); eq(null, d.weekChangePct); eq(0, d.avgSessionMin); eq(null, d.bestDay); eq(StreakResult(0, 0), d.streak)
    }
}
