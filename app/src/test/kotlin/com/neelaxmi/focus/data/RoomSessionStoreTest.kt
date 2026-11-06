package com.neelaxmi.focus

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neelaxmi.focus.core.LogKind
import com.neelaxmi.focus.core.PomodoroConfig
import com.neelaxmi.focus.core.SessionLog
import com.neelaxmi.focus.core.SessionMachine
import com.neelaxmi.focus.core.SessionState
import com.neelaxmi.focus.core.Step
import com.neelaxmi.focus.core.Tick
import com.neelaxmi.focus.data.FocusDatabase
import com.neelaxmi.focus.data.RoomSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room + real SQLite on a device/emulator. Verifies the DAO queries and the transactional save. */
@RunWith(AndroidJUnit4::class)
class RoomSessionStoreTest {
    private lateinit var db: FocusDatabase
    private lateinit var store: RoomSessionStore
    private val m = 60_000L
    private fun t(min: Long) = Tick(1_800_000_000_000L + min * m, 9_000_000L + min * m)

    @Before fun open() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, FocusDatabase::class.java).allowMainThreadQueries().build()
        store = RoomSessionStore(db.sessions())
    }
    @After fun close() = db.close()

    @Test fun emptyDatabaseHasNoActiveSessionAndFirstIdIsOne() {
        assertNull(store.active()); assertEquals(1L, store.allocateId())
    }

    @Test fun savedSessionIsReturnedAsActiveAndRoundTripsExactly() {
        val s = SessionMachine.startPomodoro(store.allocateId(), PomodoroConfig(25 * m, 5 * m, 15 * m, 4, 2), t(0))
        store.save(s, listOf(SessionLog(t(0).wallMs, LogKind.STARTED)))
        assertEquals(s, store.active()); assertEquals(2L, store.allocateId())
    }

    @Test fun finishedSessionsLeaveActiveAndAppearInHistoryNewestFirst() {
        val a = SessionMachine.startTimer(1, 10 * m, t(0))
        val b = SessionMachine.startTimer(2, 10 * m, t(100))
        store.save((SessionMachine.advance(a, t(20)).session), emptyList())
        store.save((SessionMachine.advance(b, t(130)).session), emptyList())
        assertNull(store.active())
        assertEquals(listOf(2L, 1L), store.finishedSince(0).map { it.id })
        assertEquals(listOf(2L), store.finishedSince(t(50).wallMs).map { it.id })
        assertEquals(SessionState.COMPLETED, store.finishedSince(0).first().state)
    }

    @Test fun emergencyExitsAreCountedFromLogsWithinTheWindow() {
        val s = SessionMachine.startTimer(1, 60 * m, t(0))
        store.save(s, listOf(SessionLog(t(1).wallMs, LogKind.EMERGENCY_EXIT), SessionLog(t(500).wallMs, LogKind.EMERGENCY_EXIT), SessionLog(t(2).wallMs, LogKind.EARLY_EXIT)))
        assertEquals(2, store.countLogs(LogKind.EMERGENCY_EXIT, 0))
        assertEquals(1, store.countLogs(LogKind.EMERGENCY_EXIT, t(100).wallMs))
    }

    @Test fun pausedSessionStillCountsAsActive() {
        val s = SessionMachine.startTimer(1, 60 * m, t(0))
        val paused = (SessionMachine.pause(s, t(5)) as Step.Ok).session
        store.save(paused, emptyList()); assertEquals(SessionState.PAUSED, store.active()!!.state)
    }
}
