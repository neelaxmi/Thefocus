package com.neelaxmi.focus.data

import com.neelaxmi.focus.core.LogKind
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.SessionLog
import com.neelaxmi.focus.core.SessionStore

class RoomSessionStore(private val dao: SessionDao) : SessionStore {
    override fun active(): Session? = dao.active()?.toSession()
    override fun save(session: Session, logs: List<SessionLog>) = dao.save(session.toEntity(), logs.map { it.toEntity(session.id) })
    override fun allocateId(): Long = dao.maxId() + 1
    override fun finishedSince(sinceWallMs: Long): List<Session> = dao.finishedSince(sinceWallMs).map { it.toSession() }
    override fun countLogs(kind: LogKind, sinceWallMs: Long): Int = dao.countLogs(kind.name, sinceWallMs)
}
