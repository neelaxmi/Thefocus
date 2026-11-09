package com.neelaxmi.focus.core

import com.neelaxmi.focus.core.Harness.eq
import com.neelaxmi.focus.core.Harness.no
import com.neelaxmi.focus.core.Harness.test
import com.neelaxmi.focus.core.Harness.yes

private const val MIN = 60_000L
private const val T0 = 1_800_000_000_000L
private fun at(minutes: Long, boot: Long = 0) = Tick(T0 + minutes * MIN, boot + minutes * MIN)
private fun ok(s: Step): Step.Ok = s as? Step.Ok ?: throw AssertionError("expected Ok, got $s")
private fun rej(s: Step): Reason = (s as? Step.Rejected ?: throw AssertionError("expected Rejected, got $s")).reason

fun sessionMachineTests() {
    test("Ticks: normal, clock change, reboot") {
        eq(10 * MIN, Ticks.delta(at(0), at(10)).ms)
        val tampered = Ticks.delta(Tick(T0, 0), Tick(T0 + 120 * MIN, 5 * MIN))
        yes(tampered.clockAdjusted); eq(5 * MIN, tampered.ms, "uses monotonic")
        val reboot = Ticks.delta(Tick(T0, 900 * MIN), Tick(T0 + 30 * MIN, 1 * MIN))
        yes(reboot.rebooted); no(reboot.clockAdjusted); eq(30 * MIN, reboot.ms)
        val backwards = Ticks.delta(Tick(T0, 900 * MIN), Tick(T0 - 10 * MIN, 1 * MIN))
        eq(0L, backwards.ms); yes(backwards.clockAdjusted)
    }

    test("timer completes at exactly planned time and is verified") {
        val s = SessionMachine.startTimer(1, 25 * MIN, at(0))
        val r = ok(SessionMachine.advance(s, at(25))).session
        eq(SessionState.COMPLETED, r.state); eq(25 * MIN, r.focusMs); yes(r.isVerifiedCompletion)
        eq(T0 + 25 * MIN, r.endedAtWallMs)
    }

    test("late catch-up (process death for hours) still ends at the true end time") {
        val s = SessionMachine.startTimer(1, 25 * MIN, at(0))
        val r = ok(SessionMachine.advance(s, at(600))).session
        eq(SessionState.COMPLETED, r.state); eq(T0 + 25 * MIN, r.endedAtWallMs); eq(25 * MIN, r.focusTotalMs)
    }

    test("advance is idempotent") {
        val s = SessionMachine.startTimer(1, 25 * MIN, at(0))
        val a = ok(SessionMachine.advance(s, at(10))).session
        val b = ok(SessionMachine.advance(a, at(10))).session
        eq(a, b)
    }

    test("pause time is not counted") {
        val s = SessionMachine.startTimer(1, 25 * MIN, at(0))
        val p = ok(SessionMachine.pause(s, at(10))).session
        eq(SessionState.PAUSED, p.state); eq(10 * MIN, p.focusTotalMs)
        val r = ok(SessionMachine.resume(p, at(70))).session          // 1h pause
        val mid = ok(SessionMachine.advance(r, at(80))).session
        eq(20 * MIN, mid.focusTotalMs); eq(5 * MIN, SessionMachine.remainingMs(mid))
        val done = ok(SessionMachine.advance(mid, at(85))).session
        eq(SessionState.COMPLETED, done.state)
    }

    test("clock tampering: forward wall jump does not shorten the session, and removes verification") {
        val s = SessionMachine.startTimer(1, 25 * MIN, at(0))
        val cheat = Tick(T0 + 3 * 60 * MIN, 5 * MIN)               // wall +3h, real +5min
        val r = ok(SessionMachine.advance(s, cheat)).session
        eq(SessionState.RUNNING, r.state); eq(5 * MIN, r.focusTotalMs); yes(r.clockAdjusted)
        val end = ok(SessionMachine.advance(r, Tick(cheat.wallMs + 20 * MIN, 25 * MIN))).session
        eq(SessionState.COMPLETED, end.state); no(end.isVerifiedCompletion, "tampered session earns no reward")
    }

    test("reboot mid-session: wall clock is used and session continues") {
        val s = SessionMachine.startTimer(1, 25 * MIN, Tick(T0, 5000 * MIN))
        val r = ok(SessionMachine.advance(s, Tick(T0 + 10 * MIN, 2 * MIN))).session
        eq(1, r.rebootCount); eq(10 * MIN, r.focusTotalMs); eq(SessionState.RUNNING, r.state)
    }

    test("pomodoro: phases derived purely from elapsed time") {
        val cfg = PomodoroConfig(25 * MIN, 5 * MIN, 15 * MIN, roundsPerCycle = 4, cycles = 2)
        val s = SessionMachine.startPomodoro(1, cfg, at(0))
        val a = ok(SessionMachine.advance(s, at(26))).session
        eq(Phase.SHORT_BREAK, a.phase); eq(1 * MIN, a.phaseElapsedMs); eq(25 * MIN, a.focusMs)
        val b = ok(SessionMachine.advance(s, at(40))).session           // 25 + 5 + 10
        eq(Phase.FOCUS, b.phase); eq(2, b.round); eq(10 * MIN, b.phaseElapsedMs)
    }

    test("pomodoro: long break after last round of a cycle") {
        val cfg = PomodoroConfig(25 * MIN, 5 * MIN, 15 * MIN, 4, 2)
        val s = SessionMachine.startPomodoro(1, cfg, at(0))
        val t = 25 + 5 + 25 + 5 + 25 + 5 + 25 + 3                        // end of round 4 + 3 min
        val r = ok(SessionMachine.advance(s, at(t.toLong()))).session
        eq(Phase.LONG_BREAK, r.phase); eq(3 * MIN, r.phaseElapsedMs)
        val n = ok(SessionMachine.advance(s, at(t + 12L))).session        // long break (15) ends
        eq(Phase.FOCUS, n.phase); eq(2, n.cycle); eq(1, n.round)
    }

    test("pomodoro: full run completes with exact total focus; huge gap is fine") {
        val cfg = PomodoroConfig(25 * MIN, 5 * MIN, 15 * MIN, 4, 2)
        val s = SessionMachine.startPomodoro(1, cfg, at(0))
        val r = ok(SessionMachine.advance(s, at(100_000))).session
        eq(SessionState.COMPLETED, r.state); eq(8 * 25 * MIN, r.focusMs)
        val totalMin = 8 * 25 + 6 * 5 + 15                               // no trailing break after the final round
        eq(T0 + totalMin * MIN, r.endedAtWallMs)
    }

    test("user breaks: limited count, focus resumes, total focus exact") {
        val s = SessionMachine.startTimer(1, 60 * MIN, at(0), breaks = BreakRules(1, 10 * MIN))
        val b = ok(SessionMachine.startBreak(s, at(20))).session
        eq(Phase.USER_BREAK, b.phase); eq(20 * MIN, b.focusMs)
        val back = ok(SessionMachine.advance(b, at(30))).session
        eq(Phase.FOCUS, back.phase); eq(0L, back.phaseElapsedMs)
        eq(Reason.NO_BREAKS_LEFT, rej(SessionMachine.startBreak(back, at(31))))
        val done = ok(SessionMachine.advance(back, at(70))).session
        eq(SessionState.COMPLETED, done.state); eq(60 * MIN, done.focusMs)
    }

    test("pomodoro rejects user breaks") {
        val s = SessionMachine.startPomodoro(1, PomodoroConfig(25 * MIN, 5 * MIN, 15 * MIN, 4, 1), at(0))
        eq(Reason.WRONG_MODE, rej(SessionMachine.startBreak(s, at(1))))
    }

    test("stopwatch: stop = COMPLETED with measured focus; survives long background") {
        val s = SessionMachine.startStopwatch(1, at(0))
        val r = ok(SessionMachine.stop(s, at(95))).session
        eq(SessionState.COMPLETED, r.state); eq(95 * MIN, r.focusMs)
    }

    test("timer stopped early = ABANDONED but focus time kept and logged as EARLY_EXIT") {
        val s = SessionMachine.startTimer(1, 25 * MIN, at(0))
        val st = ok(SessionMachine.stop(s, at(7)))
        eq(SessionState.ABANDONED, st.session.state); eq(7 * MIN, st.session.focusMs)
        yes(st.logs.any { it.kind == LogKind.EARLY_EXIT }); no(st.session.isVerifiedCompletion)
    }

    test("strict: cannot pause, cannot stop without request, cooldown enforced") {
        val strict = StrictRules(endCooldownMs = 2 * MIN, emergencyExitsLeft = 0)
        val s = SessionMachine.startTimer(1, 60 * MIN, at(0), strict = strict)
        eq(Reason.STRICT_LOCKED, rej(SessionMachine.pause(s, at(1))))
        eq(Reason.STRICT_LOCKED, rej(SessionMachine.stop(s, at(1))))
        val req = ok(SessionMachine.requestEnd(s, at(5))).session
        eq(Reason.COOLDOWN_NOT_ELAPSED, rej(SessionMachine.stop(req, at(6))))
        val end = ok(SessionMachine.stop(req, at(7))).session
        eq(SessionState.ABANDONED, end.state); eq(7 * MIN, end.focusMs)
    }

    test("strict: clock jump cannot skip the end cooldown") {
        val strict = StrictRules(10 * MIN, 0)
        val s = SessionMachine.startTimer(1, 120 * MIN, at(0), strict = strict)
        val req = ok(SessionMachine.requestEnd(s, at(5))).session
        val cheat = Tick(T0 + 5 * MIN + 5 * 60 * MIN, 6 * MIN)             // wall +5h, real +1min
        eq(Reason.COOLDOWN_NOT_ELAPSED, rej(SessionMachine.stop(req, cheat)))
    }

    test("strict: emergency exit needs exits left and is logged") {
        val none = SessionMachine.startTimer(1, 60 * MIN, at(0), strict = StrictRules(10 * MIN, 0))
        eq(Reason.NO_EMERGENCY_EXITS, rej(SessionMachine.stop(none, at(1), emergency = true)))
        val one = SessionMachine.startTimer(2, 60 * MIN, at(0), strict = StrictRules(10 * MIN, 1))
        val st = ok(SessionMachine.stop(one, at(3), emergency = true))
        eq(SessionState.ABANDONED, st.session.state); yes(st.logs.any { it.kind == LogKind.EMERGENCY_EXIT })
    }

    test("strict session that runs out completes normally with no cooldown needed") {
        val s = SessionMachine.startTimer(1, 30 * MIN, at(0), strict = StrictRules(10 * MIN, 0))
        val r = ok(SessionMachine.advance(s, at(31))).session
        eq(SessionState.COMPLETED, r.state)
    }

    test("finished sessions reject further operations") {
        val s = ok(SessionMachine.advance(SessionMachine.startTimer(1, 5 * MIN, at(0)), at(6))).session
        eq(Reason.ALREADY_ENDED, rej(SessionMachine.pause(s, at(7))))
        eq(Reason.ALREADY_ENDED, rej(SessionMachine.stop(s, at(7))))
    }
}
