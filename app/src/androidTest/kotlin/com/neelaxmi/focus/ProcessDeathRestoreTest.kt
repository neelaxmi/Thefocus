package com.neelaxmi.focus

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neelaxmi.focus.core.LogKind
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.Outcome
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.SessionController
import com.neelaxmi.focus.core.SessionPresenter
import com.neelaxmi.focus.core.SessionState
import com.neelaxmi.focus.core.StartConfig
import com.neelaxmi.focus.core.Tick
import com.neelaxmi.focus.core.TickSource
import com.neelaxmi.focus.core.WakeScheduler
import com.neelaxmi.focus.data.FocusDatabase
import com.neelaxmi.focus.data.RoomSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The persistence half of the "survives process death and reboot" promise, on a REAL on-disk Room database:
 * a second controller built over the same file must rebuild the running session from storage alone.
 */
@RunWith(AndroidJUnit4::class)
class ProcessDeathRestoreTest {
    private val m = 60_000L
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "restore-test.db"
    private var wall = 1_800_000_000_000L
    private var mono = 7_000_000L
    private val ticks = TickSource { Tick(wall, mono) }

    private class Wake : WakeScheduler { var delay: Long? = null; override fun scheduleIn(delayMs: Long) { delay = delayMs }; override fun cancel() { delay = null } }
    private class Pres : SessionPresenter {
        val events = mutableListOf<LogKind>(); var ongoing: Session? = null
        override fun ongoing(session: Session) { ongoing = session }; override fun cleared() { ongoing = null }
        override fun event(kind: LogKind, session: Session) { events += kind }
    }

    private fun openDb() = Room.databaseBuilder(ctx, FocusDatabase::class.java, name).allowMainThreadQueries().build()
    @Before fun clean() { ctx.deleteDatabase(name) }
    @After fun cleanup() { ctx.deleteDatabase(name) }

    @Test fun runningTimerIsRebuiltFromDiskByANewController() {
        val db1 = openDb()
        val c1 = SessionController(RoomSessionStore(db1.sessions()), ticks, Wake(), Pres())
        assertTrue(c1.start(StartConfig(Mode.TIMER, plannedMs = 25 * m)) is Outcome.Applied)
        db1.close()                                            // "process dies": nothing in memory survives

        wall += 10 * m; mono += 10 * m
        val db2 = openDb(); val wake = Wake(); val pres = Pres()
        val s = SessionController(RoomSessionStore(db2.sessions()), ticks, wake, pres).sync()!!
        assertEquals(10 * m, s.focusTotalMs); assertEquals(15 * m, wake.delay); assertEquals(s, pres.ongoing)
        db2.close()
    }

    @Test fun rebootIsSurvivedAndCountedWhenMonotonicClockResets() {
        val db1 = openDb()
        SessionController(RoomSessionStore(db1.sessions()), ticks, Wake(), Pres()).start(StartConfig(Mode.TIMER, plannedMs = 25 * m))
        db1.close()
        wall += 8 * m; mono = 2_000                            // device was off 8 minutes; elapsedRealtime restarted
        val db2 = openDb()
        val s = SessionController(RoomSessionStore(db2.sessions()), ticks, Wake(), Pres()).sync()!!
        assertEquals(1, s.rebootCount); assertEquals(8 * m, s.focusTotalMs); db2.close()
    }

    @Test fun sessionFinishedWhileProcessWasDeadCompletesOnNextSync() {
        val db1 = openDb()
        SessionController(RoomSessionStore(db1.sessions()), ticks, Wake(), Pres()).start(StartConfig(Mode.TIMER, plannedMs = 25 * m))
        db1.close()
        wall += 90 * m; mono += 90 * m
        val db2 = openDb(); val pres = Pres(); val store = RoomSessionStore(db2.sessions())
        assertNull(SessionController(store, ticks, Wake(), pres).sync())
        assertEquals(listOf(LogKind.COMPLETED), pres.events)
        assertEquals(SessionState.COMPLETED, store.finishedSince(0).single().state); db2.close()
    }
}
