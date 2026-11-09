package com.neelaxmi.focus.ui

import androidx.lifecycle.ViewModel
import com.neelaxmi.focus.core.DashboardCalculator
import com.neelaxmi.focus.core.DashboardState
import com.neelaxmi.focus.core.Enforcement
import com.neelaxmi.focus.core.EnforcementStatus
import com.neelaxmi.focus.core.HomeInsights
import com.neelaxmi.focus.core.Productivity
import com.neelaxmi.focus.core.ScheduleEngine
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.Outcome
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.platform.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

data class MostDistracting(val label: String, val pkg: String, val ms: Long, val today: Boolean)
data class UpcomingItem(val name: String, val startWallMs: Long, val endWallMs: Long, val strict: Boolean)

/** Everything the dashboard shows beyond M1. Fields are null / empty when the underlying real data does not exist. */
data class HomeState(
    val usageAccess: Boolean,
    val mostDistracting: MostDistracting?,
    val enforcement: Enforcement,
    val strictDefault: Boolean,
    val upcoming: List<UpcomingItem>,
    val productivity: Int?,
)

data class UiState(
    val home: HomeState? = null,
    val active: Session? = null,
    val dashboard: DashboardState? = null,
    val history: List<Session> = emptyList(),
    /** Last operation that did NOT apply (rejected / invalid / already active). Shown once, then consumed. */
    val notice: Outcome? = null,
)

/**
 * Thin: every mutation is forwarded to the SessionController on the single io thread; every number shown comes from
 * the persisted session / DashboardCalculator. The ViewModel owns no timer state, so process death loses nothing.
 */
class FocusViewModel(private val loc: ServiceLocator) : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val listener: (Session?) -> Unit = { s ->
        _ui.update { it.copy(active = s) }
        if (s == null) loc.io.execute { refreshStatsBlocking() }          // a session just ended: stats changed
    }

    init {
        loc.controller.addListener(listener)
        syncNow()
    }

    /** Catch up with the clock and reload statistics. Call when the screen becomes visible. */
    fun syncNow() = loc.io.execute {
        val s = loc.controller.sync()
        _ui.update { it.copy(active = s) }
        refreshStatsBlocking()
    }

    fun start(mode: Mode, timerOverrideMin: Int? = null) = submit { loc.controller.start(loc.settings.toStartConfig(mode, timerOverrideMin)) }
    fun pause() = submit { loc.controller.pause() }
    fun resume() = submit { loc.controller.resume() }
    fun startBreak() = submit { loc.controller.startBreak() }
    fun endBreakEarly() = submit { loc.controller.endBreakEarly() }
    fun requestEnd() = submit { loc.controller.requestEnd() }
    fun cancelEndRequest() = submit { loc.controller.cancelEndRequest() }
    fun restart() = submit { loc.controller.restart() }
    fun stop(emergency: Boolean = false) = submit { loc.controller.stop(emergency) }
    fun consumeNotice() = _ui.update { it.copy(notice = null) }

    private fun submit(block: () -> Outcome) = loc.io.execute {
        val out = block()
        if (out !is Outcome.Applied) _ui.update { it.copy(notice = out) }
    }

    private fun refreshStatsBlocking() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val since = today.minusDays(60).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = loc.store.finishedSince(since)
        val dash = DashboardCalculator.compute(sessions, loc.settings.dailyGoalMin, today, zone)
        _ui.update { it.copy(dashboard = dash, history = sessions.take(300), home = buildHome(today, zone, dash)) }
    }

    override fun onCleared() { loc.controller.removeListener(listener) }

    private fun buildHome(today: LocalDate, zone: ZoneId, dash: DashboardState): HomeState {
        val access = loc.usageAccess()
        val distracting = loc.distractingPackages()
        val rows = if (access) loc.usage.rows(today.minusDays(6), today) else emptyList()
        val md = HomeInsights.mostDistracting(rows, today, distracting)?.let { (t, isToday) -> MostDistracting(loc.catalog.label(t.pkg), t.pkg, t.ms, isToday) }
        val todayRow = rows.firstOrNull { it.date == today }
        val score = todayRow?.let { r -> Productivity.score(dash.todayFocusMin, dash.goalMin, (r.perApp.filterKeys { it in distracting }.values.sum() / 60_000L).toInt()) }
        val rules = loc.config.rules()
        val upcoming = ScheduleEngine.upcoming(rules, System.currentTimeMillis(), zone, 3).mapNotNull { o ->
            rules.firstOrNull { it.id == o.ruleId }?.let { UpcomingItem(it.name.ifBlank { "Focus" }, o.startWallMs, o.endWallMs, it.strict) }
        }
        val snap = loc.holder.snapshot.assembled
        return HomeState(access, md, EnforcementStatus.of(loc.accessibilityConnected(), snap.demanded, snap.blockedApps.size), loc.settings.strictEnabled, upcoming, score)
    }
}
