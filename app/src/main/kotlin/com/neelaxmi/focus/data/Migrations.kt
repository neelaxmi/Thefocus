package com.neelaxmi.focus.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2 (Milestone 2): adds block groups, schedules, schedule runs, achievements and daily usage rollups.
 * The v1 tables (sessions, session_logs) are NOT touched, so no session data can be lost.
 * Statements must equal what Room would generate for the entities in EntitiesM2.kt (verified by scripts/check_room_v2.py).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `block_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `block_group_apps` (`groupId` INTEGER NOT NULL, `pkg` TEXT NOT NULL, PRIMARY KEY(`groupId`, `pkg`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_block_group_apps_pkg` ON `block_group_apps` (`pkg`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `daysMask` INTEGER NOT NULL, `startMin` INTEGER NOT NULL, `endMin` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `strict` INTEGER NOT NULL, `autoStart` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `schedule_groups` (`scheduleId` INTEGER NOT NULL, `groupId` INTEGER NOT NULL, PRIMARY KEY(`scheduleId`, `groupId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `schedule_runs` (`ruleId` INTEGER NOT NULL, `runKey` TEXT NOT NULL, `atWall` INTEGER NOT NULL, PRIMARY KEY(`ruleId`, `runKey`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `achievements` (`id` TEXT NOT NULL, `unlockedAtWall` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_summary` (`dateKey` TEXT NOT NULL, `totalMs` INTEGER NOT NULL, `unlocks` INTEGER NOT NULL, `updatedAtWall` INTEGER NOT NULL, PRIMARY KEY(`dateKey`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_app_usage` (`dateKey` TEXT NOT NULL, `pkg` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, PRIMARY KEY(`dateKey`, `pkg`))")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
