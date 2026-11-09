package com.neelaxmi.focus.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions", indices = [Index("state"), Index("startedAtWall")])
data class SessionEntity(
    @PrimaryKey val id: Long,
    val mode: String, val state: String, val phase: String, val plannedMs: Long?,
    val pomoFocusMs: Long?, val pomoShortMs: Long?, val pomoLongMs: Long?,
    val pomoRounds: Int?, val pomoCycles: Int?, val breaksAllowed: Int, val breakDurationMs: Long,
    val strictCooldownMs: Long?, val strictEmergencyLeft: Int?, val startedAtWall: Long,
    val endedAtWall: Long?, val focusMs: Long, val phaseElapsedMs: Long, val round: Int,
    val cycle: Int, val breaksTaken: Int, val tickWall: Long, val tickMono: Long,
    val endReqWall: Long?, val endReqMono: Long?, val clockAdjusted: Boolean,
    val rebootCount: Int, val tagId: Long?,
)
@Entity(tableName = "session_logs", indices = [Index("kind", "atWall"), Index("sessionId")])
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long, val atWall: Long, val kind: String, val phase: String?,
)
