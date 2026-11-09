package com.neelaxmi.focus.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Version 2 (v1 = Milestone 1 sessions; see Migrations.kt). exportSchema is off for now; BEFORE the first public release: turn it on, commit the schema JSON and
 * add migration tests (tracked in DEVELOPMENT_PROGRESS.md). Future modules (limits, schedules, rules) add tables
 * through explicit migrations; destructive fallback is deliberately NOT enabled.
 */
@Database(
    entities = [
        SessionEntity::class, SessionLogEntity::class,
        BlockGroupEntity::class, BlockGroupAppEntity::class, ScheduleEntity::class, ScheduleGroupEntity::class, ScheduleRunEntity::class,
        AchievementEntity::class, DailySummaryEntity::class, DailyAppUsageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun config(): ConfigDao
    abstract fun usage(): UsageDao
}
