package com.neelaxmi.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class UsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertSummary(e: DailySummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertApps(rows: List<DailyAppUsageEntity>)

    @Query("DELETE FROM daily_app_usage WHERE dateKey = :dateKey")
    abstract fun deleteAppsForDay(dateKey: String)

    /** One transaction per day: a summary row never exists without its per-app rows. */
    @Transaction
    open fun replaceDay(summary: DailySummaryEntity, apps: List<DailyAppUsageEntity>) {
        deleteAppsForDay(summary.dateKey); upsertSummary(summary)
        if (apps.isNotEmpty()) insertApps(apps)
    }

    @Query("SELECT * FROM daily_summary WHERE dateKey BETWEEN :from AND :to ORDER BY dateKey")
    abstract fun summaries(from: String, to: String): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_app_usage WHERE dateKey BETWEEN :from AND :to")
    abstract fun appUsage(from: String, to: String): List<DailyAppUsageEntity>

    @Query("SELECT MIN(dateKey) FROM daily_summary")
    abstract fun firstDay(): String?

    // ---- achievements ----
    @Query("SELECT * FROM achievements")
    abstract fun achievements(): List<AchievementEntity>

    /** -1 when already unlocked: unlock (and its notification) happens once. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertAchievement(e: AchievementEntity): Long
}
