package com.neelaxmi.focus

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neelaxmi.focus.core.AchievementId
import com.neelaxmi.focus.core.DailyUsage
import com.neelaxmi.focus.core.DefaultGroups
import com.neelaxmi.focus.core.GroupKind
import com.neelaxmi.focus.core.ScheduleEngine
import com.neelaxmi.focus.core.ScheduleRule
import com.neelaxmi.focus.data.FocusDatabase
import com.neelaxmi.focus.data.RoomConfigStore
import com.neelaxmi.focus.data.RoomUsageStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Assert.fail
import org.junit.runner.RunWith
import java.time.LocalDate

/** Real Room: block groups, schedules (incl. idempotent runs), usage rollups and achievement persistence. */
@RunWith(AndroidJUnit4::class)
class ConfigAndUsageStoreTest {
    private lateinit var db: FocusDatabase
    private lateinit var cfg: RoomConfigStore
    private lateinit var usage: RoomUsageStore

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FocusDatabase::class.java).allowMainThreadQueries().build()
        cfg = RoomConfigStore(db.config()); usage = RoomUsageStore(db.usage())
    }
    @After fun close() = db.close()

    @Test fun seedCreatesTheFiveBuiltInGroupsOnceAndBrowsersAreOffByDefault() {
        assertTrue(cfg.seedIfEmpty(mapOf(GroupKind.SOCIAL to setOf("com.instagram.android"))))
        assertFalse(cfg.seedIfEmpty(emptyMap()))
        val g = cfg.groups(); assertEquals(5, g.size)
        assertEquals(setOf("com.instagram.android"), g.first { it.kind == GroupKind.SOCIAL }.apps)
        assertFalse(g.first { it.kind == GroupKind.BROWSERS }.enabled); assertTrue(g.first { it.kind == GroupKind.SOCIAL }.enabled)
        assertEquals(DefaultGroups.names.getValue(GroupKind.GAMES), g.first { it.kind == GroupKind.GAMES }.name)
    }

    @Test fun editingGroupsPersistsAndDeletingAGroupRemovesItFromSchedules() {
        val a = cfg.createGroup("A", GroupKind.CUSTOM, true, setOf("x", "y"))
        cfg.setGroupApps(a, setOf("y", "z")); assertEquals(setOf("y", "z"), cfg.groups().single().apps)
        cfg.setGroupEnabled(a, false); assertFalse(cfg.groups().single().enabled)
        val rid = cfg.saveRule(ScheduleRule(0, "Study", ScheduleEngine.WEEKDAYS, 540, 660, true, false, true, setOf(a)))
        assertEquals(setOf(a), cfg.rules().single { it.id == rid }.groupIds)
        cfg.deleteGroup(a)
        assertTrue(cfg.groups().isEmpty()); assertEquals(emptySet<Long>(), cfg.rules().single { it.id == rid }.groupIds)
    }

    @Test fun invalidScheduleIsRejectedBeforeTouchingTheDatabase() {
        try { cfg.saveRule(ScheduleRule(0, "bad", 0, 540, 660, true, false, false, emptySet())); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
        assertTrue(cfg.rules().isEmpty())
    }

    @Test fun scheduleRunsAreIdempotentPerOccurrenceAndKind() {
        val id = cfg.saveRule(ScheduleRule(0, "S", ScheduleEngine.EVERY_DAY, 60, 120, true, false, true, emptySet()))
        assertTrue(cfg.markRun(id, "S:2026-11-05", 1)); assertFalse(cfg.markRun(id, "S:2026-11-05", 2))
        assertTrue(cfg.markRun(id, "R:2026-11-05", 1)); assertTrue(cfg.markRun(id, "S:2026-11-06", 1))
        cfg.deleteRule(id); assertTrue(cfg.markRun(id, "S:2026-11-05", 3))          // runs are removed with the rule
    }

    @Test fun updatingARuleKeepsItsIdAndReplacesItsGroups() {
        val g1 = cfg.createGroup("G1", GroupKind.CUSTOM, true, emptySet()); val g2 = cfg.createGroup("G2", GroupKind.CUSTOM, true, emptySet())
        val id = cfg.saveRule(ScheduleRule(0, "S", ScheduleEngine.MON, 60, 120, true, false, false, setOf(g1)))
        cfg.saveRule(cfg.rules().single().copy(name = "S2", groupIds = setOf(g2)))
        val r = cfg.rules().single(); assertEquals(id, r.id); assertEquals("S2", r.name); assertEquals(setOf(g2), r.groupIds)
    }

    @Test fun dailyUsageRoundTripsAndRecomputeReplacesStaleAppRows() {
        val d = LocalDate.of(2026, 11, 5)
        usage.saveDays(listOf(DailyUsage(d, mapOf("a" to 600_000L, "b" to 300_000L), 900_000L, 4)), 1)
        assertEquals(mapOf("a" to 600_000L, "b" to 300_000L), usage.rows(d, d).single().perApp)
        usage.saveDays(listOf(DailyUsage(d, mapOf("a" to 700_000L), 700_000L, 5)), 2)
        val r = usage.rows(d, d).single(); assertEquals(mapOf("a" to 700_000L), r.perApp); assertEquals(5, r.unlocks); assertEquals(d, usage.firstDay())
        assertTrue(usage.rows(d.plusDays(1), d.plusDays(5)).isEmpty())
    }

    @Test fun achievementUnlockHappensOnceAndKeepsTheFirstTimestamp() {
        assertTrue(usage.unlock(AchievementId.FOCUS_1H, 100)); assertFalse(usage.unlock(AchievementId.FOCUS_1H, 200))
        assertEquals(setOf(AchievementId.FOCUS_1H), usage.unlockedAchievements()); assertEquals(100L, usage.unlockTimes().getValue(AchievementId.FOCUS_1H))
    }
}
