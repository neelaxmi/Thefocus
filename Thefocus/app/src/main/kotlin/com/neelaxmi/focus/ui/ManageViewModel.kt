package com.neelaxmi.focus.ui

import androidx.lifecycle.ViewModel
import com.neelaxmi.focus.core.AppRef
import com.neelaxmi.focus.core.BlockGroup
import com.neelaxmi.focus.core.Enforcement
import com.neelaxmi.focus.core.EnforcementStatus
import com.neelaxmi.focus.core.GroupKind
import com.neelaxmi.focus.core.ScheduleRule
import com.neelaxmi.focus.platform.PermissionHelper
import com.neelaxmi.focus.platform.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ManageState(
    val loading: Boolean = true,
    /** Shown in an error card with a retry; never swallowed silently. */
    val error: String? = null,
    val apps: List<AppRef> = emptyList(),
    val groups: List<BlockGroup> = emptyList(),
    val rules: List<ScheduleRule> = emptyList(),
    val enforcement: Enforcement = Enforcement.OFF_NEEDS_ACCESSIBILITY,
    val accessibilityEnabledInSettings: Boolean = false,
    val blockedNow: Int = 0,
    /** A change was refused because strict protection is active (shown as a message, never silently ignored). */
    val lockedNotice: Boolean = false,
    /** True while edits are refused, so the UI can show it up front. */
    val configLocked: Boolean = false,
)

/** Blocking groups, installed apps and schedules. All persistence and policy rules live in the engine; this only dispatches. */
class ManageViewModel(private val loc: ServiceLocator, private val appContext: android.content.Context) : ViewModel() {
    private val _s = MutableStateFlow(ManageState())
    val state: StateFlow<ManageState> = _s.asStateFlow()

    init { refresh() }

    fun refresh() = loc.io.execute { load() }

    private fun load() {
        try {
            val snap = loc.holder.rebuild().assembled
            _s.value = ManageState(
                loading = false, apps = loc.catalog.installed(), groups = loc.config.groups(), rules = loc.config.rules(),
                enforcement = EnforcementStatus.of(loc.accessibilityConnected(), snap.demanded, snap.blockedApps.size),
                accessibilityEnabledInSettings = PermissionHelper.accessibilityEnabled(appContext), blockedNow = snap.blockedApps.size,
                configLocked = snap.configLocked,
            )
        } catch (e: Exception) {
            _s.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    /** Runs a config change, then re-publishes the blocking policy and re-arms schedules, then reloads. Errors surface in the UI. */
    private fun change(block: () -> Unit) = loc.io.execute {
        try {
            if (loc.holder.rebuild().assembled.configLocked) { _s.update { it.copy(lockedNotice = true, configLocked = true) }; return@execute }
            block(); loc.invalidateDistractingCache(); loc.holder.rebuild(); loc.scheduleCoordinator.sync(); load()
        } catch (e: Exception) { _s.update { it.copy(error = e.message ?: e.javaClass.simpleName) } }
    }

    fun dismissError() = _s.update { it.copy(error = null, lockedNotice = false) }
    fun setGroupEnabled(id: Long, on: Boolean) = change { loc.config.setGroupEnabled(id, on) }
    fun setGroupApps(id: Long, apps: Set<String>) = change { loc.config.setGroupApps(id, apps) }
    fun createGroup(name: String) = change { if (name.isNotBlank()) loc.config.createGroup(name, GroupKind.CUSTOM, true, emptySet()) }
    fun renameGroup(id: Long, name: String) = change { if (name.isNotBlank()) loc.config.renameGroup(id, name) }
    fun deleteGroup(id: Long) = change { loc.config.deleteGroup(id) }
    fun saveRule(r: ScheduleRule) = change { loc.config.saveRule(r) }
    fun setRuleEnabled(id: Long, on: Boolean) = change { loc.config.setRuleEnabled(id, on) }
    fun deleteRule(id: Long) = change { loc.config.deleteRule(id) }
}
