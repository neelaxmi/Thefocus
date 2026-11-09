package com.neelaxmi.focus

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neelaxmi.focus.core.SessionState
import com.neelaxmi.focus.data.ALL_MIGRATIONS
import com.neelaxmi.focus.data.FocusDatabase
import com.neelaxmi.focus.data.RoomConfigStore
import com.neelaxmi.focus.data.RoomSessionStore
import com.neelaxmi.focus.core.GroupKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The REAL Room v1 -> v2 migration. Builds a genuine Milestone-1 database file with raw SQL (user_version = 1, holding a
 * finished session), then opens it with Room + the shipped migrations. Room itself validates the migrated schema against the
 * entities and throws if a single column, type, key or index differs, which is the check scripts/check_room_v2.py can only model.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    @Before fun clean() { ctx.deleteDatabase(name) }
    @After fun cleanup() { ctx.deleteDatabase(name) }

    private fun createV1() {
        val db = SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name), null)
        db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER NOT NULL, `mode` TEXT NOT NULL, `state` TEXT NOT NULL, `phase` TEXT NOT NULL, `plannedMs` INTEGER, `pomoFocusMs` INTEGER, `pomoShortMs` INTEGER, `pomoLongMs` INTEGER, `pomoRounds` INTEGER, `pomoCycles` INTEGER, `breaksAllowed` INTEGER NOT NULL, `breakDurationMs` INTEGER NOT NULL, `strictCooldownMs` INTEGER, `strictEmergencyLeft` INTEGER, `startedAtWall` INTEGER NOT NULL, `endedAtWall` INTEGER, `focusMs` INTEGER NOT NULL, `phaseElapsedMs` INTEGER NOT NULL, `round` INTEGER NOT NULL, `cycle` INTEGER NOT NULL, `breaksTaken` INTEGER NOT NULL, `tickWall` INTEGER NOT NULL, `tickMono` INTEGER NOT NULL, `endReqWall` INTEGER, `endReqMono` INTEGER, `clockAdjusted` INTEGER NOT NULL, `rebootCount` INTEGER NOT NULL, `tagId` INTEGER, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_state` ON `sessions` (`state`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_startedAtWall` ON `sessions` (`startedAtWall`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `session_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `atWall` INTEGER NOT NULL, `kind` TEXT NOT NULL, `phase` TEXT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_logs_kind_atWall` ON `session_logs` (`kind`, `atWall`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_logs_sessionId` ON `session_logs` (`sessionId`)")
        db.execSQL("INSERT INTO sessions VALUES (7,'TIMER','COMPLETED','FOCUS',1500000,NULL,NULL,NULL,NULL,NULL,0,0,NULL,NULL,1000,2000,1500000,0,1,1,0,0,0,NULL,NULL,0,0,NULL)")
        db.version = 1
        db.close()
    }

    @Test fun v1DatabaseMigratesToV2WithoutLosingSessionsAndRoomAcceptsTheSchema() {
        createV1()
        val db = Room.databaseBuilder(ctx, FocusDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val sessions = RoomSessionStore(db.sessions())
            val old = sessions.finishedSince(0).single()
            assertEquals(7L, old.id); assertEquals(SessionState.COMPLETED, old.state); assertEquals(1_500_000L, old.focusMs)
            // the new tables exist and work (this also forces Room's schema validation of the migrated file)
            val cfg = RoomConfigStore(db.config())
            val id = cfg.createGroup("Test", GroupKind.CUSTOM, true, setOf("a.b.c"))
            assertEquals(setOf("a.b.c"), cfg.groups().single { it.id == id }.apps)
        } finally { db.close() }
    }

    @Test fun migrationIsRepeatableOnAFreshInstall() {
        val db = Room.databaseBuilder(ctx, FocusDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try { assertTrue(RoomConfigStore(db.config()).groups().isEmpty()) } finally { db.close() }
    }
}
