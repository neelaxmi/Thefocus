package com.neelaxmi.focus.data

import com.neelaxmi.focus.core.BlockGroup
import com.neelaxmi.focus.core.ConfigRepository
import com.neelaxmi.focus.core.DefaultGroups
import com.neelaxmi.focus.core.GroupKind
import com.neelaxmi.focus.core.ScheduleRule

/** Block groups + schedules. Blocking API: call from the controller/io thread only. */
class RoomConfigStore(private val dao: ConfigDao) : ConfigRepository {

    // ---------------- groups ----------------
    override fun groups(): List<BlockGroup> {
        val apps = dao.groupApps().groupBy({ it.groupId }, { it.pkg })
        return dao.groups().map { g ->
            BlockGroup(g.id, g.name, runCatching { GroupKind.valueOf(g.kind) }.getOrDefault(GroupKind.CUSTOM), g.enabled, apps[g.id]?.toSet() ?: emptySet())
        }
    }

    override fun createGroup(name: String, kind: GroupKind, enabled: Boolean, apps: Set<String>): Long {
        val id = dao.insertGroup(BlockGroupEntity(name = name.trim(), kind = kind.name, enabled = enabled, sortOrder = dao.groupCount()))
        dao.replaceGroupApps(id, apps)
        return id
    }

    override fun setGroupApps(groupId: Long, apps: Set<String>) = dao.replaceGroupApps(groupId, apps)
    override fun setGroupEnabled(groupId: Long, enabled: Boolean) = dao.setGroupEnabled(groupId, enabled)
    override fun renameGroup(groupId: Long, name: String) = dao.renameGroup(groupId, name.trim())
    override fun deleteGroup(groupId: Long) = dao.deleteGroup(groupId)

    /** First run only: creates the five built-in groups from what is really installed. Returns true if it seeded. */
    override fun seedIfEmpty(suggestions: Map<GroupKind, Set<String>>): Boolean {
        if (dao.groupCount() > 0) return false
        GroupKind.entries.forEach { kind ->
            createGroup(DefaultGroups.names.getValue(kind), kind, DefaultGroups.enabledByDefault(kind), suggestions[kind] ?: emptySet())
        }
        return true
    }

    // ---------------- schedules ----------------
    override fun rules(): List<ScheduleRule> {
        val links = dao.scheduleGroups().groupBy({ it.scheduleId }, { it.groupId })
        return dao.schedules().map { s ->
            ScheduleRule(s.id, s.name, s.daysMask, s.startMin, s.endMin, s.enabled, s.strict, s.autoStart, links[s.id]?.toSet() ?: emptySet())
        }
    }

    /** Insert (id == 0) or update. Returns the id. Invalid rules are rejected before touching the database. */
    override fun saveRule(r: ScheduleRule): Long {
        r.validate()?.let { throw IllegalArgumentException("invalid schedule: $it") }
        return dao.saveSchedule(ScheduleEntity(r.id, r.name.trim(), r.daysMask, r.startMin, r.endMin, r.enabled, r.strict, r.autoStart), r.groupIds)
    }
    override fun setRuleEnabled(id: Long, enabled: Boolean) = dao.setScheduleEnabled(id, enabled)
    override fun deleteRule(id: Long) = dao.deleteSchedule(id)

    override fun markRun(ruleId: Long, key: String, wallMs: Long): Boolean =
        dao.insertRun(ScheduleRunEntity(ruleId, key, wallMs)) != -1L

    fun pruneRuns(beforeWallMs: Long) = dao.pruneRuns(beforeWallMs)
}
