package com.neelaxmi.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class SessionDao {
    @Query("SELECT * FROM sessions WHERE state IN ('RUNNING','PAUSED') ORDER BY id DESC LIMIT 1")
    abstract fun active(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsert(entity: SessionEntity)

    @Insert
    abstract fun insertLogs(logs: List<SessionLogEntity>)

    /** Session row and its new log rows are written in ONE transaction: no half-saved state after a crash. */
    @Transaction
    open fun save(entity: SessionEntity, logs: List<SessionLogEntity>) {
        upsert(entity)
        if (logs.isNotEmpty()) insertLogs(logs)
    }

    @Query("SELECT COALESCE(MAX(id), 0) FROM sessions")
    abstract fun maxId(): Long

    @Query("SELECT * FROM sessions WHERE state IN ('COMPLETED','ABANDONED') AND startedAtWall >= :since ORDER BY startedAtWall DESC")
    abstract fun finishedSince(since: Long): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM session_logs WHERE kind = :kind AND atWall >= :since")
    abstract fun countLogs(kind: String, since: Long): Int

    @Query("SELECT * FROM session_logs WHERE sessionId = :sessionId ORDER BY atWall, id")
    abstract fun logsFor(sessionId: Long): List<SessionLogEntity>
}
