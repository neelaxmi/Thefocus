package com.neelaxmi.focus.data

import com.neelaxmi.focus.core.BreakRules
import com.neelaxmi.focus.core.LogKind
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.PomodoroConfig
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.SessionMachine
import com.neelaxmi.focus.core.SessionState
import com.neelaxmi.focus.core.Step
import com.neelaxmi.focus.core.StrictRules
import com.neelaxmi.focus.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionMappersTest {
    private val m = 60_000L
    private fun t(min: Long) = Tick(1_800_000_000_000L + min * m, 5_000_000L + min * m)
    private fun roundTrip(s: Session) = assertEquals(s, s.toEntity().toSession())

    @Test fun timerRoundTrips() = roundTrip(SessionMachine.startTimer(1, 25 * m, t(0), BreakRules(2, 5 * m), tagId = 7))

    @Test fun pomodoroMidRunRoundTrips() {
        val s = SessionMachine.startPomodoro(2, PomodoroConfig(25 * m, 5 * m, 15 * m, 4, 2), t(0))
        roundTrip((SessionMachine.advance(s, t(40)).session))
    }

    @Test fun strictWithEndRequestRoundTrips() {
        val s = SessionMachine.startTimer(3, 60 * m, t(0), strict = StrictRules(120_000, 1))
        val req = (SessionMachine.requestEnd(s, t(5)) as Step.Ok).session
        roundTrip(req); assertEquals(t(5), req.toEntity().toSession().endRequestedTick)
    }

    @Test fun finishedStopwatchAndNullablesRoundTrip() {
        val s = SessionMachine.startStopwatch(4, t(0))
        val done = (SessionMachine.stop(s, t(30)) as Step.Ok).session
        roundTrip(done); assertNull(done.toEntity().pomoFocusMs); assertNull(done.toEntity().strictCooldownMs)
    }

    @Test fun clockAdjustedAndRebootFlagsSurvive() {
        val s = SessionMachine.startTimer(5, 25 * m, t(0))
        val tampered = SessionMachine.advance(s, Tick(t(0).wallMs + 180 * m, t(0).monoMs + 2 * m)).session
        roundTrip(tampered); assertEquals(true, tampered.toEntity().clockAdjusted)
    }

    /** The DAO's SQL compares against these literal strings; renaming an enum constant must break this test, not production. */
    @Test fun enumNamesUsedBySqlArePinned() {
        assertEquals("RUNNING", SessionState.RUNNING.name); assertEquals("PAUSED", SessionState.PAUSED.name)
        assertEquals("COMPLETED", SessionState.COMPLETED.name); assertEquals("ABANDONED", SessionState.ABANDONED.name)
        assertEquals("EMERGENCY_EXIT", LogKind.EMERGENCY_EXIT.name); assertEquals("TIMER", Mode.TIMER.name)
    }
}
