package com.neelaxmi.focus

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.Outcome
import com.neelaxmi.focus.core.Reason
import com.neelaxmi.focus.platform.PermissionHelper
import com.neelaxmi.focus.platform.ServiceLocator
import com.neelaxmi.focus.ui.FocusViewModel
import com.neelaxmi.focus.ui.InsightsViewModel
import com.neelaxmi.focus.ui.LocalCatalog
import com.neelaxmi.focus.ui.ManageViewModel
import com.neelaxmi.focus.ui.screens.DashboardScreen
import com.neelaxmi.focus.ui.screens.FocusActions
import com.neelaxmi.focus.ui.screens.FocusScreen
import com.neelaxmi.focus.ui.screens.InsightsActions
import com.neelaxmi.focus.ui.screens.InsightsScreen
import com.neelaxmi.focus.ui.screens.ManageActions
import com.neelaxmi.focus.ui.screens.ManageScreen
import com.neelaxmi.focus.ui.screens.SettingsScreen
import com.neelaxmi.focus.ui.theme.NeelaxmiTheme

class MainActivity : ComponentActivity() {
    private val loc get() = application as ServiceLocator
    private val vm: FocusViewModel by viewModels { viewModelFactory { initializer { FocusViewModel(application as ServiceLocator) } } }
    private val manage: ManageViewModel by viewModels { viewModelFactory { initializer { ManageViewModel(application as ServiceLocator, applicationContext) } } }
    private val insights: InsightsViewModel by viewModels { viewModelFactory { initializer { InsightsViewModel(application as ServiceLocator) } } }
    private val resumeKey = mutableIntStateOf(0)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeKey.intValue++ }

    private fun requestNotifications() {
        if (PermissionHelper.needsNotificationRuntimePermission() && !PermissionHelper.notificationsGranted(this))
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loc.io.execute { loc.bootstrap() }          // seeds built-in groups on first run, re-arms schedules, saves usage rollups
        setContent { NeelaxmiTheme { CompositionLocalProvider(LocalCatalog provides loc.catalog) { Root() } } }
    }

    override fun onResume() {
        super.onResume()
        resumeKey.intValue++
        vm.syncNow(); manage.refresh(); insights.load()       // statuses (permissions, service connection) are re-read when returning from system settings
    }

    private fun openUsageAccess() = startActivity(PermissionHelper.usageAccessSettings(this))
    private fun openAccessibility() = startActivity(PermissionHelper.accessibilitySettings())

    @Composable
    private fun Root() {
        val ui by vm.ui.collectAsStateWithLifecycle()
        val manageState by manage.state.collectAsStateWithLifecycle()
        val insightsState by insights.state.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val snackbar = remember { SnackbarHostState() }
        val ctx = LocalContext.current

        LaunchedEffect(ui.notice) { ui.notice?.let { snackbar.showSnackbar(ctx.getString(noticeText(it))); vm.consumeNotice() } }

        val actions = remember {
            FocusActions(
                start = { m, min -> vm.start(m, min) }, pause = vm::pause, resume = vm::resume,
                startBreak = vm::startBreak, endBreakEarly = vm::endBreakEarly,
                requestEnd = vm::requestEnd, cancelEnd = vm::cancelEndRequest, stop = { e -> vm.stop(e) },
                syncNow = vm::syncNow, ensureNotifications = ::requestNotifications, restart = vm::restart,
            )
        }
        val manageActions = remember {
            ManageActions(manage::setGroupEnabled, manage::setGroupApps, manage::createGroup, manage::renameGroup, manage::deleteGroup,
                manage::saveRule, manage::setRuleEnabled, manage::deleteRule, manage::dismissError, manage::refresh)
        }
        val insightsActions = remember { InsightsActions(insights::setRange, insights::refreshFromSystem, ::openUsageAccess, insights::dismissError) }

        val bg = MaterialTheme.colorScheme.background
        Scaffold(
            containerColor = bg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text(stringResource(R.string.tab_home)) })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.PlayArrow, null) }, label = { Text(stringResource(R.string.tab_focus)) })
                    NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.DateRange, null) }, label = { Text(stringResource(R.string.tab_insights)) })
                    NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Filled.Lock, null) }, label = { Text(stringResource(R.string.tab_blocking)) })
                    NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Icons.Filled.Settings, null) }, label = { Text(stringResource(R.string.tab_settings)) })
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).background(Brush.verticalGradient(listOf(bg, MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))))) {
                Crossfade(targetState = tab, label = "tab") { t ->
                    when (t) {
                        0 -> DashboardScreen(ui.active, ui.dashboard, ui.home, onStart = { tab = 1 }, onOpenActive = { tab = 1 },
                            onQuickPomodoro = { vm.start(Mode.POMODORO); requestNotifications(); tab = 1 }, onOpenBlocking = { tab = 3 }, onOpenInsights = { tab = 2 })
                        1 -> FocusScreen(ui.active, loc.settings, blockingAvailable = ui.home?.let { it.enforcement != com.neelaxmi.focus.core.Enforcement.OFF_NEEDS_ACCESSIBILITY && it.enforcement != com.neelaxmi.focus.core.Enforcement.UNAVAILABLE_NEEDS_ACCESSIBILITY } ?: false, actions = actions)
                        2 -> InsightsScreen(insightsState, ui.history, insightsActions)
                        3 -> ManageScreen(manageState, loc.settings, manageActions, onOpenAccessibilitySettings = ::openAccessibility)
                        else -> SettingsScreen(loc.settings, resumeKey.intValue, onGoalChanged = vm::syncNow, requestNotifications = ::requestNotifications,
                            onOpenUsageAccess = ::openUsageAccess, onEnableBlocking = { tab = 3 })
                    }
                }
            }
        }
    }

    private fun noticeText(o: Outcome): Int = when (o) {
        Outcome.AlreadyActive -> R.string.notice_already_active
        Outcome.NoActive -> R.string.notice_no_active
        is Outcome.Invalid -> R.string.notice_invalid
        is Outcome.Applied -> R.string.notice_not_possible
        is Outcome.Rejected -> when (o.reason) {
            Reason.STRICT_LOCKED -> R.string.notice_strict_locked
            Reason.COOLDOWN_NOT_ELAPSED -> R.string.notice_cooldown
            Reason.NO_BREAKS_LEFT -> R.string.notice_no_breaks
            Reason.NO_EMERGENCY_EXITS -> R.string.notice_no_emergency
            else -> R.string.notice_not_possible
        }
    }
}
