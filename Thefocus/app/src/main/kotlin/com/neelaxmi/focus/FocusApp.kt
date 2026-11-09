package com.neelaxmi.focus

import android.app.Application
import android.os.SystemClock
import androidx.room.Room
import com.neelaxmi.focus.core.AchievementService
import com.neelaxmi.focus.core.ConfigRepository
import com.neelaxmi.focus.core.DailyUsageStore
import com.neelaxmi.focus.core.DefaultGroups
import com.neelaxmi.focus.core.PolicyHolder
import com.neelaxmi.focus.core.RollupService
import com.neelaxmi.focus.core.ScheduleCoordinator
import com.neelaxmi.focus.core.SessionController
import com.neelaxmi.focus.core.SessionStore
import com.neelaxmi.focus.data.ALL_MIGRATIONS
import com.neelaxmi.focus.data.FocusDatabase
import com.neelaxmi.focus.data.RoomConfigStore
import com.neelaxmi.focus.data.RoomSessionStore
import com.neelaxmi.focus.data.RoomUsageStore
import com.neelaxmi.focus.platform.AchievementNotifierImpl
import com.neelaxmi.focus.platform.AlarmWakeScheduler
import com.neelaxmi.focus.platform.AlwaysAllowed
import com.neelaxmi.focus.platform.AppCatalog
import com.neelaxmi.focus.platform.NotificationPresenter
import com.neelaxmi.focus.platform.Notifications
import com.neelaxmi.focus.platform.RollupAlarm
import com.neelaxmi.focus.platform.ScheduleNotifierImpl
import com.neelaxmi.focus.platform.ServiceLocator
import com.neelaxmi.focus.platform.Settings
import com.neelaxmi.focus.platform.SystemTicks
import com.neelaxmi.focus.platform.UsageEventsSource
import com.neelaxmi.focus.platform.WallClockWaker
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Composition root. Everything is lazy so a receiver waking a cold process pays only for what it uses. */
class FocusApp : Application(), ServiceLocator {
    override val io: ExecutorService by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "focus-io") } }

    private val db: FocusDatabase by lazy {
        Room.databaseBuilder(this, FocusDatabase::class.java, "focus.db")
            .addMigrations(*ALL_MIGRATIONS)          // explicit migrations only: destructive fallback is deliberately NOT enabled
            .build()
    }
    override val store: SessionStore by lazy { RoomSessionStore(db.sessions()) }
    override val settings: Settings by lazy { Settings(this) }

    private val configStore: RoomConfigStore by lazy { RoomConfigStore(db.config()) }
    private val usageStore: RoomUsageStore by lazy { RoomUsageStore(db.usage()) }
    override val config: ConfigRepository get() = configStore
    override val usage: DailyUsageStore get() = usageStore
    override val catalog: AppCatalog by lazy { AppCatalog(this) }
    override val alwaysAllowed: AlwaysAllowed by lazy { AlwaysAllowed(this) }

    override val holder: PolicyHolder by lazy { PolicyHolder(store, configStore, configStore, { alwaysAllowed.get() }, SystemTicks) }

    override val controller: SessionController by lazy {
        SessionController(store, SystemTicks, AlarmWakeScheduler(this), NotificationPresenter(this, ::todayFocusMin)).also { c ->
            // Listener runs on the io thread: any session change re-publishes the blocking policy; a finished session may unlock achievements.
            c.addListener { s -> holder.rebuild(); if (s == null) achievements.sync() }
        }
    }

    override val scheduleCoordinator: ScheduleCoordinator by lazy {
        ScheduleCoordinator(configStore, SystemTicks, WallClockWaker(this), ScheduleNotifierImpl(this), controller,
            { settings.strictCooldownSec * 1_000L to settings.emergencyExitsPerDay }, { settings.reminderLeadMin })
    }

    override val rollup: RollupService by lazy { RollupService(UsageEventsSource(this), usageStore, SystemTicks, { alwaysAllowed.get() }) }

    override val achievements: AchievementService by lazy {
        AchievementService(store, usageStore, usageStore, AchievementNotifierImpl(this), SystemTicks, { settings.dailyGoalMin }, ::distractingPackages)
    }

    @Volatile private var distractingCache: Set<String> = emptySet()
    @Volatile private var distractingAt = 0L
    /** Installed distracting-category apps + every app in an enabled block group. Cached 5 min (package scan is not free). */
    override fun distractingPackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (distractingAt == 0L || now - distractingAt > 5 * 60_000L) {
            distractingCache = DefaultGroups.distractingPackages(catalog.installed(), configStore.groups()); distractingAt = now
        }
        return distractingCache
    }
    override fun invalidateDistractingCache() { distractingAt = 0L }
    override fun usageAccess(): Boolean = UsageEventsSource(this).hasAccess()
    override fun accessibilityConnected(): Boolean = com.neelaxmi.focus.platform.FocusAccessibilityService.connected

    private fun todayFocusMin(): Int {
        val z = ZoneId.systemDefault()
        val since = LocalDate.now(z).atStartOfDay(z).toInstant().toEpochMilli()
        return (store.finishedSince(since).filter { !it.clockAdjusted }.sumOf { it.focusMs } / 60_000L).toInt()
    }

    override fun bootstrap() {
        configStore.seedIfEmpty(DefaultGroups.suggest(catalog.installed()))
        resyncAll(); runRollup()
    }

    override fun resyncAll() {
        controller.sync(); scheduleCoordinator.sync(); holder.rebuild(); RollupAlarm.ensure(this)
    }

    override fun runRollup() {
        rollup.sync(); achievements.sync(); configStore.pruneRuns(System.currentTimeMillis() - 60L * 86_400_000L)
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
