package com.neelaxmi.focus.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ============================ ports ============================

/** Blocking groups + schedules CRUD (Room on Android). Blocking calls: use from the single io thread. */
interface BlockConfigSource { fun groups(): List<BlockGroup> }

interface ConfigRepository : ScheduleStore, BlockConfigSource {
    fun createGroup(name: String, kind: GroupKind, enabled: Boolean, apps: Set<String>): Long
    fun setGroupApps(groupId: Long, apps: Set<String>)
    fun setGroupEnabled(groupId: Long, enabled: Boolean)
    fun renameGroup(groupId: Long, name: String)
    fun deleteGroup(groupId: Long)
    /** First run only; returns true if it created the built-in groups. */
    fun seedIfEmpty(suggestions: Map<GroupKind, Set<String>>): Boolean
    fun saveRule(r: ScheduleRule): Long
    fun setRuleEnabled(id: Long, enabled: Boolean)
    fun deleteRule(id: Long)
}

interface UsageEventSource {
    /** False when the user has not granted usage access. The engine then produces NO usage numbers at all. */
    fun hasAccess(): Boolean
    fun read(fromMs: Long, toMs: Long): List<UsageEvent>
}

interface DailyUsageStore {
    fun saveDays(days: List<DailyUsage>, nowWallMs: Long)
    fun rows(from: LocalDate, to: LocalDate): List<DailyUsage>
    fun firstDay(): LocalDate?
}

interface AchievementStore {
    fun unlockedAchievements(): Set<AchievementId>
    /** true only for the call that really unlocked it (so notification fires once). */
    fun unlock(id: AchievementId, wallMs: Long): Boolean
    /** When each achievement was unlocked (wall-clock ms). */
    fun unlockTimes(): Map<AchievementId, Long>
}
interface AchievementNotifier { fun unlocked(id: AchievementId) }

// ============================ services ============================

enum class RollupStatus { NO_ACCESS, NO_EVENTS, OK }
data class RollupResult(val status: RollupStatus, val daysStored: Int)

/**
 * Persists daily usage from the OS. The OS keeps raw events only for a limited time, so rollups are saved before they expire.
 * Never writes anything without usage access, and never stores a day the requested window did not fully cover.
 * Assumption (documented in KNOWN_LIMITATIONS): the OS retains at least [firstRunLookbackDays] of events.
 */
class RollupService(
    private val source: UsageEventSource, private val store: DailyUsageStore, private val ticks: TickSource,
    private val ignored: () -> Set<String>,
    private val firstRunLookbackDays: Long = 5, private val lookbackDays: Long = 3,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun sync(): RollupResult {
        if (!source.hasAccess()) return RollupResult(RollupStatus.NO_ACCESS, 0)
        val z = zone(); val now = ticks.now().wallMs
        val today = Instant.ofEpochMilli(now).atZone(z).toLocalDate()
        val back = if (store.firstDay() == null) firstRunLookbackDays else lookbackDays
        val windowStart = today.minusDays(back).atStartOfDay(z).toInstant().toEpochMilli()
        val events = source.read(windowStart, now)
        if (events.isEmpty()) return RollupResult(RollupStatus.NO_EVENTS, 0)
        val days = UsageRollup.compute(events, windowStart, now, z, ignored())
        store.saveDays(days, now)
        return RollupResult(RollupStatus.OK, days.size)
    }
}

/** Evaluates achievements from stored sessions + stored usage rollups; unlocks are permanent and notified once. */
class AchievementService(
    private val sessions: SessionStore, private val usage: DailyUsageStore, private val store: AchievementStore,
    private val notifier: AchievementNotifier, private val ticks: TickSource,
    private val goalMin: () -> Int, private val distracting: () -> Set<String>, private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun evaluate(): List<AchievementProgress> {
        val z = zone(); val now = ticks.now().wallMs
        val today = Instant.ofEpochMilli(now).atZone(z).toLocalDate()
        val d = distracting()
        val facts = usage.rows(today.minusDays(400), today).associate { r -> r.date to DayFacts(r.perApp.filterKeys { it in d }.values.sum()) }
        return Achievements.evaluate(AchievementInput(sessions.finishedSince(0), goalMin(), today, z, facts))
    }

    fun unlockTimes(): Map<AchievementId, Long> = store.unlockTimes()

    /** Returns achievements unlocked by THIS call. */
    fun sync(): List<AchievementId> {
        val ev = evaluate(); val now = ticks.now().wallMs
        val fresh = Achievements.newlyUnlocked(store.unlockedAchievements(), ev).filter { store.unlock(it, now) }
        fresh.forEach(notifier::unlocked)
        return fresh
    }
}

object HomeInsights {
    /** Most-used distracting app today; falls back to the last 7 days; null when there is no real usage data. */
    fun mostDistracting(rows: List<DailyUsage>, today: LocalDate, distracting: Set<String>): Pair<AppUsageTotal, Boolean>? {
        val t = UsageReports.build(rows, today, today, distracting, 1).topDistracting.firstOrNull()
        if (t != null) return t to true
        val w = UsageReports.build(rows, today.minusDays(6), today, distracting, 1).topDistracting.firstOrNull()
        return w?.let { it to false }
    }
}
