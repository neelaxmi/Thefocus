package com.neelaxmi.focus.data

import com.neelaxmi.focus.core.AchievementId
import com.neelaxmi.focus.core.AchievementStore
import com.neelaxmi.focus.core.DailyUsageStore
import com.neelaxmi.focus.core.DailyUsage
import java.time.LocalDate

/** Persisted daily usage rollups (real OS data only) and unlocked achievements. Blocking: call from the io thread. */
class RoomUsageStore(private val dao: UsageDao) : DailyUsageStore, AchievementStore {

    override fun saveDays(days: List<DailyUsage>, nowWallMs: Long) = days.forEach { d ->
        val key = d.date.toString()
        dao.replaceDay(
            DailySummaryEntity(key, d.totalMs, d.unlocks, nowWallMs),
            d.perApp.filterValues { it > 0 }.map { (pkg, ms) -> DailyAppUsageEntity(key, pkg, ms) },
        )
    }

    override fun rows(from: LocalDate, to: LocalDate): List<DailyUsage> {
        val f = from.toString(); val t = to.toString()
        val apps = dao.appUsage(f, t).groupBy { it.dateKey }
        return dao.summaries(f, t).map { s ->
            DailyUsage(LocalDate.parse(s.dateKey), apps[s.dateKey]?.associate { it.pkg to it.durationMs } ?: emptyMap(), s.totalMs, s.unlocks)
        }
    }

    /** First day for which real usage data exists (shown in the UI so missing history is explained, not hidden). */
    override fun firstDay(): LocalDate? = dao.firstDay()?.let(LocalDate::parse)

    override fun unlockedAchievements(): Set<AchievementId> =
        dao.achievements().mapNotNull { runCatching { AchievementId.valueOf(it.id) }.getOrNull() }.toSet()

    /** true only for the call that actually unlocked it (so the notification is sent exactly once). */
    override fun unlock(id: AchievementId, wallMs: Long): Boolean = dao.insertAchievement(AchievementEntity(id.name, wallMs)) != -1L

    override fun unlockTimes(): Map<AchievementId, Long> =
        dao.achievements().mapNotNull { e -> runCatching { AchievementId.valueOf(e.id) }.getOrNull()?.let { it to e.unlockedAtWall } }.toMap()
}
