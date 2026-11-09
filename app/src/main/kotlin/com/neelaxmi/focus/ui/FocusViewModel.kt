package com.neelaxmi.focus.ui

import androidx.lifecycle.ViewModel
import com.neelaxmi.focus.core.DashboardCalculator
import com.neelaxmi.focus.core.DashboardState
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

data class UiState(
    val active: Session? = null,
    val dashboard: DashboardState? = null,
    val history: List<Session> = emptyList(),
    val notice: Outcome? = null,
)

class FocusViewModel(private val loc: ServiceLocator) : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val listener: (Session?) -> Unit = { s ->
        _ui.update { it.copy(active = s) }
        if (s == null) loc.io.execute { refreshStatsBlocking() }
    }

    init {
        loc.controller.addListener(listener)
        syncNow()
    }

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
        _ui.update { it.copy(dashboard = dash, history = sessions.take(300)) }
    }

    override fun onCleared() { loc.controller.removeListener(listener) }
}
