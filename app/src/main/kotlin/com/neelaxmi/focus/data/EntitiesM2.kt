package com.neelaxmi.focus.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---- Milestone 2 tables (database version 2). Every change here needs a matching statement in Migrations.kt;
// scripts/check_room_v2.py compares a MIGRATED v1 database with a FRESH v2 database and fails on any difference. ----

@Entity(tableName = "block_groups")
data class BlockGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val enabled: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "block_group_apps", primaryKeys = ["groupId", "pkg"], indices = [Index("pkg")])
data class BlockGroupAppEntity(val groupId: Long, val pkg: String)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysMask: Int,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean,
    val strict: Boolean,
    val autoStart: Boolean,
)

@Entity(tableName = "schedule_groups", primaryKeys = ["scheduleId", "groupId"])
data class ScheduleGroupEntity(val scheduleId: Long, val groupId: Long)

/** One row per (schedule, occurrence, kind). The composite key is what makes auto-start / reminders idempotent. */
@Entity(tableName = "schedule_runs", primaryKeys = ["ruleId", "runKey"])
data class ScheduleRunEntity(val ruleId: Long, val runKey: String, val atWall: Long)

@Entity(tableName = "achievements")
data class AchievementEntity(@PrimaryKey val id: String, val unlockedAtWall: Long)

/** Daily rollup of REAL OS usage. dateKey = ISO local date ("2026-11-05"), which sorts chronologically as text. */
@Entity(tableName = "daily_summary")
data class DailySummaryEntity(@PrimaryKey val dateKey: String, val totalMs: Long, val unlocks: Int, val updatedAtWall: Long)

@Entity(tableName = "daily_app_usage", primaryKeys = ["dateKey", "pkg"])
data class DailyAppUsageEntity(val dateKey: String, val pkg: String, val durationMs: Long)
