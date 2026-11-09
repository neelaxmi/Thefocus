package com.neelaxmi.focus.ui

import androidx.lifecycle.ViewModel
import com.neelaxmi.focus.core.AchievementId
import com.neelaxmi.focus.core.AchievementProgress
import com.neelaxmi.focus.core.Productivity
import com.neelaxmi.focus.core.UsageReport
import com.neelaxmi.focus.core.UsageReports
import com.neelaxmi.focus.platform.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

enum class UsageRange(val days: Long) { WEEK(7), MONTH(30) }

data class InsightsState(
    val loading: Boolean = true,
    val error: String? = null,
    /** False => the UI must show "grant usage access"; NO numbers are displayed. */
    val usageAccess: Boolean = false,
    val range: UsageRange = UsageRange.WEEK,
    val report: UsageReport? = null,
    val labels: Map<String, String> = emptyMap(),
    val firstDataDay: LocalDate? = null,
    val focusVsDistraction: List<UsageReports.FocusVsDistraction> = emptyList(),
    /** null = cannot be computed (no usage data), never a guess. */
    val productivityToday: Int? = null,
    val achievements: List<AchievementProgress> = emptyList(),
    val unlockedAt: Map<AchievementId, Long> = emptyMap(),
)

class InsightsViewModel(private val loc: ServiceLocator) : ViewModel() {
    private val _s = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _s.asStateFlow()
    private var range = UsageRange.WEEK

    init { load() }

    fun setRange(r: UsageRange) { range = r; load() }
    fun dismissError() = _s.update { it.copy(error = null) }

    /** Pull fresh OS usage into the rollups, then reload (used on open and by pull-to-refresh style button). */
    fun refreshFromSystem() = loc.io.execute { try { loc.runRollup() } catch (e: Exception) { _s.update { it.copy(error = e.message) } }; loadBlocking() }

    fun load() = loc.io.execute { loadBlocking() }

    private fun loadBlocking() {
        try {
            val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
            val access = loc.usageAccess()
            val rows = loc.usage.rows(today.minusDays(60), today)
            val distracting = loc.distractingPackages()
            val from = today.minusDays(range.days - 1)
            val report = UsageReports.build(rows, from, today, distracting, topN = 8)

            val sessions = loc.store.finishedSince(from.atStartOfDay(zone).toInstant().toEpochMilli()).filter { !it.clockAdjusted }
            val focusByDay = sessions.groupBy { java.time.Instant.ofEpochMilli(it.startedAtWallMs).atZone(zone).toLocalDate() }
                .mapValues { (_, l) -> (l.sumOf { it.focusMs } / 60_000L).toInt() }
            val todayPoint = report.days.lastOrNull()
            val score = if (access && todayPoint != null && todayPoint.hasData)
                Productivity.score(focusByDay[today] ?: 0, loc.settings.dailyGoalMin, (todayPoint.distractingMs / 60_000L).toInt()) else null

            _s.value = InsightsState(
                loading = false, usageAccess = access, range = range, report = report,
                labels = (report.topApps + report.topDistracting).map { it.pkg }.distinct().associateWith { loc.catalog.label(it) },
                firstDataDay = loc.usage.firstDay(), focusVsDistraction = UsageReports.focusVsDistraction(report.days, focusByDay),
                productivityToday = score, achievements = loc.achievements.evaluate(), unlockedAt = loc.achievements.unlockTimes(),
            )
        } catch (e: Exception) {
            _s.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
        }
    }
}
