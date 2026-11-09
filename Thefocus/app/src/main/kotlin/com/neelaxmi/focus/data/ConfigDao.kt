package com.neelaxmi.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Block groups and schedules live in one DAO so that cross-table changes (e.g. deleting a group) are ONE transaction. */
@Dao
abstract class ConfigDao {
    // ---- groups ----
    @Query("SELECT * FROM block_groups ORDER BY sortOrder, id")
    abstract fun groups(): List<BlockGroupEntity>

    @Query("SELECT * FROM block_group_apps")
    abstract fun groupApps(): List<BlockGroupAppEntity>

    @Query("SELECT COUNT(*) FROM block_groups")
    abstract fun groupCount(): Int

    @Insert
    abstract fun insertGroup(e: BlockGroupEntity): Long

    @Query("UPDATE block_groups SET enabled = :enabled WHERE id = :id")
    abstract fun setGroupEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE block_groups SET name = :name WHERE id = :id")
    abstract fun renameGroup(id: Long, name: String)

    @Query("DELETE FROM block_groups WHERE id = :id")
    abstract fun deleteGroupRow(id: Long)

    @Query("DELETE FROM block_group_apps WHERE groupId = :groupId")
    abstract fun deleteGroupApps(groupId: Long)

    @Query("DELETE FROM schedule_groups WHERE groupId = :groupId")
    abstract fun deleteScheduleLinksForGroup(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertGroupApps(rows: List<BlockGroupAppEntity>)

    @Transaction
    open fun replaceGroupApps(groupId: Long, pkgs: Collection<String>) {
        deleteGroupApps(groupId)
        if (pkgs.isNotEmpty()) insertGroupApps(pkgs.map { BlockGroupAppEntity(groupId, it) })
    }

    @Transaction
    open fun deleteGroup(id: Long) {
        deleteGroupApps(id); deleteScheduleLinksForGroup(id); deleteGroupRow(id)
    }

    // ---- schedules ----
    @Query("SELECT * FROM schedules ORDER BY startMin, id")
    abstract fun schedules(): List<ScheduleEntity>

    @Query("SELECT * FROM schedule_groups")
    abstract fun scheduleGroups(): List<ScheduleGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsertSchedule(e: ScheduleEntity): Long

    @Query("DELETE FROM schedule_groups WHERE scheduleId = :scheduleId")
    abstract fun deleteScheduleGroups(scheduleId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertScheduleGroups(rows: List<ScheduleGroupEntity>)

    @Query("UPDATE schedules SET enabled = :enabled WHERE id = :id")
    abstract fun setScheduleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM schedules WHERE id = :id")
    abstract fun deleteScheduleRow(id: Long)

    @Query("DELETE FROM schedule_runs WHERE ruleId = :ruleId")
    abstract fun deleteRuns(ruleId: Long)

    @Transaction
    open fun saveSchedule(e: ScheduleEntity, groupIds: Collection<Long>): Long {
        val id = upsertSchedule(e).let { if (e.id != 0L) e.id else it }
        deleteScheduleGroups(id)
        if (groupIds.isNotEmpty()) insertScheduleGroups(groupIds.map { ScheduleGroupEntity(id, it) })
        return id
    }

    @Transaction
    open fun deleteSchedule(id: Long) {
        deleteScheduleGroups(id); deleteRuns(id); deleteScheduleRow(id)
    }

    /** Returns -1 when the (rule, key) pair already exists: that is the idempotency guard. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertRun(e: ScheduleRunEntity): Long

    @Query("DELETE FROM schedule_runs WHERE atWall < :before")
    abstract fun pruneRuns(before: Long)
}
