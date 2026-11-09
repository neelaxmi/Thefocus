package com.neelaxmi.focus.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One local day of REAL usage, derived from OS usage events (never estimated, never defaulted). */
data class DailyUsage(val date: LocalDate, val perApp: Map<String, Long>, val totalMs: Long, val unlocks: Int)

object UsageRollup {
    /**
     * Converts raw OS events into per-day usage. [ignored] (launchers, system UI) is excluded from totals, as the system
     * Digital Wellbeing does. Only days whose start is at/after [windowStartMs] are returned: the OS keeps events for a limited
     * time, so an earlier day would be incomplete and must never be stored as if it were complete.
     */
    fun compute(events: List<UsageEvent>, windowStartMs: Long, windowEndMs: Long, zone: ZoneId, ignored: Set<String>): List<DailyUsage> {
        val intervals = UsageSessionizer.intervals(events, windowEndMs).filter { it.pkg !in ignored }
        val perDay = UsageSessionizer.dailyTotals(intervals, zone)
        val unlockDays = events.filter { it.type == EventType.UNLOCK }
            .groupingBy { Instant.ofEpochMilli(it.tsMs).atZone(zone).toLocalDate() }.eachCount()
        fun covered(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli() >= windowStartMs
        return (perDay.keys + unlockDays.keys).filter(::covered).sorted().map { d ->
            val apps = perDay[d] ?: emptyMap()
            DailyUsage(d, apps, apps.values.sum(), unlockDays[d] ?: 0)
        }
    }
}

data class AppUsageTotal(val pkg: String, val ms: Long)
data class DayPoint(val date: LocalDate, val totalMs: Long, val distractingMs: Long, val unlocks: Int, val hasData: Boolean)

data class UsageReport(
    val from: LocalDate, val to: LocalDate,
    val days: List<DayPoint>,
    val totalMs: Long, val avgPerDayMs: Long, val unlocks: Int,
    val topApps: List<AppUsageTotal>, val topDistracting: List<AppUsageTotal>,
    /** Average daily screen time of the last 7 days vs the 7 days before, in percent; null if either side has no data. */
    val trendPct: Int?,
    val daysWithData: Int,
)

object UsageReports {
    fun build(rows: List<DailyUsage>, from: LocalDate, to: LocalDate, distracting: Set<String>, topN: Int = 5): UsageReport {
        val byDate = rows.associateBy { it.date }
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
            val r = byDate[d]
            DayPoint(d, r?.totalMs ?: 0, r?.perApp?.filterKeys { it in distracting }?.values?.sum() ?: 0, r?.unlocks ?: 0, r != null)
        }.toList()
        val inRange = rows.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val apps = HashMap<String, Long>()
        inRange.forEach { r -> r.perApp.forEach { (p, ms) -> apps.merge(p, ms, Long::plus) } }
        val ranked = apps.map { AppUsageTotal(it.key, it.value) }.sortedWith(compareByDescending<AppUsageTotal> { it.ms }.thenBy { it.pkg })
        val withData = days.count { it.hasData }
        val total = days.sumOf { it.totalMs }
        return UsageReport(
            from, to, days, total, if (withData == 0) 0 else total / withData, days.sumOf { it.unlocks },
            ranked.take(topN), ranked.filter { it.pkg in distracting }.take(topN), trendPct(rows, to), withData,
        )
    }

    fun trendPct(rows: List<DailyUsage>, end: LocalDate, window: Int = 7): Int? {
        val by = rows.associateBy { it.date }
        fun avg(offStart: Long): Double? {
            val xs = (offStart until offStart + window).mapNotNull { by[end.minusDays(it)]?.totalMs }
            return if (xs.isEmpty()) null else xs.average()
        }
        val recent = avg(0) ?: return null
        val prev = avg(window.toLong()) ?: return null
        if (prev <= 0.0) return null
        return (((recent - prev) / prev) * 100).toInt()
    }

    /** Focus minutes (from sessions) next to distracting minutes (from usage) per day; days without usage data have null distraction. */
    data class FocusVsDistraction(val date: LocalDate, val focusMin: Int, val distractingMin: Int?)
    fun focusVsDistraction(days: List<DayPoint>, focusMinByDay: Map<LocalDate, Int>): List<FocusVsDistraction> =
        days.map { FocusVsDistraction(it.date, focusMinByDay[it.date] ?: 0, if (it.hasData) (it.distractingMs / 60_000L).toInt() else null) }
}

object Productivity {
    /**
     * 0..100 = 100 x (0.6 x min(1, focus/goal) + 0.4 x (1 - min(1, distractingMin/120))).
     * Returns null when distraction time is unknown (usage access not granted): a score is never invented.
     */
    fun score(focusMin: Int, goalMin: Int, distractingMin: Int?): Int? {
        require(goalMin > 0)
        if (distractingMin == null) return null
        val focus = (focusMin.toDouble() / goalMin).coerceIn(0.0, 1.0)
        val penalty = (distractingMin / 120.0).coerceIn(0.0, 1.0)
        return Math.round(100 * (0.6 * focus + 0.4 * (1 - penalty))).toInt()
    }
}
