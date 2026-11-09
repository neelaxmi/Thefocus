package com.neelaxmi.focus.platform

import com.neelaxmi.focus.core.AchievementService
import com.neelaxmi.focus.core.ConfigRepository
import com.neelaxmi.focus.core.DailyUsageStore
import com.neelaxmi.focus.core.PolicyHolder
import com.neelaxmi.focus.core.RollupService
import com.neelaxmi.focus.core.ScheduleCoordinator
import com.neelaxmi.focus.core.SessionController
import com.neelaxmi.focus.core.SessionStore
import java.util.concurrent.ExecutorService

/**
 * Implemented by the Application. Receivers, services and the UI reach shared objects through this interface so platform
 * code does not depend on Room or Compose.
 *
 * THREADING RULE: every call into [controller], [config], [usage], [holder], [scheduleCoordinator], [rollup] and
 * [achievements] must run on [io] (a single-thread executor). That one rule serialises alarms, receivers, the service,
 * notification actions, the accessibility service and the UI.
 */
interface ServiceLocator {
    val controller: SessionController
    val store: SessionStore
    val settings: Settings
    val io: ExecutorService

    val config: ConfigRepository
    val usage: DailyUsageStore
    val holder: PolicyHolder
    val scheduleCoordinator: ScheduleCoordinator
    val rollup: RollupService
    val achievements: AchievementService
    val catalog: AppCatalog
    val alwaysAllowed: AlwaysAllowed

    /** Live permission/status facts (cheap). */
    fun usageAccess(): Boolean
    fun accessibilityConnected(): Boolean
    /** Installed distracting-category apps + every app in an enabled block group (cached; call [invalidateDistractingCache] after group edits). */
    fun distractingPackages(): Set<String>
    fun invalidateDistractingCache()

    /** First start of the UI: seeds the built-in block groups from the installed apps, then [resyncAll] + [runRollup]. Run on [io]. */
    fun bootstrap()
    /** Re-reads everything that can change what is enforced and re-arms alarms. Run on [io]. Idempotent. */
    fun resyncAll()
    /** Persist usage rollups and evaluate achievements. Run on [io]. */
    fun runRollup()
}
