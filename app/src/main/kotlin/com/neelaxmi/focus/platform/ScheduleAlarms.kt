package com.neelaxmi.focus.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.neelaxmi.focus.R
import com.neelaxmi.focus.core.AchievementId
import com.neelaxmi.focus.core.AchievementNotifier
import com.neelaxmi.focus.core.ScheduleNotifier
import com.neelaxmi.focus.core.ScheduleRule
import com.neelaxmi.focus.core.ScheduleWaker

/**
 * Schedules are defined in LOCAL WALL TIME ("weekdays 09:00"), so their alarm is a wall-clock (RTC) alarm. If the user
 * changes the clock or time zone, BootReceiver (TIME_SET / TIMEZONE_CHANGED) re-runs the coordinator, which re-arms it.
 */
class WallClockWaker(private val ctx: Context) : ScheduleWaker {
    private val am = ctx.service<AlarmManager>()
    private fun pending() = PendingIntent.getBroadcast(ctx, 20, Intent(ctx, ScheduleReceiver::class.java).setAction(ScheduleReceiver.ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun scheduleAt(wallMs: Long) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallMs, pending()) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallMs, pending())
        } catch (e: SecurityException) {
            Log.w("WallClockWaker", "exact alarm refused, using inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallMs, pending())
        }
    }
    override fun cancel() { am.cancel(pending()) }
}

/** Inexact repeating alarm that keeps usage rollups saved before the OS discards the raw events. */
object RollupAlarm {
    const val ACTION = "com.neelaxmi.focus.action.ROLLUP"
    private const val INTERVAL = 6 * 60 * 60 * 1000L
    fun ensure(ctx: Context) {
        val pi = PendingIntent.getBroadcast(ctx, 21, Intent(ctx, RollupReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ctx.service<AlarmManager>().setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 30 * 60_000L, INTERVAL, pi)
    }
}

class ScheduleNotifierImpl(private val ctx: Context) : ScheduleNotifier {
    private val nm = ctx.service<NotificationManager>()
    private fun post(id: Int, title: String, text: String) {
        nm.notify(id, Notification.Builder(ctx, Notifications.CH_ALERTS).setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(NotificationPresenter.openApp(ctx)).build())
    }

    override fun reminder(rule: ScheduleRule, startWallMs: Long) {
        val min = ((startWallMs - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1)
        post(2000 + (rule.id % 500).toInt(), ctx.getString(R.string.sched_reminder_title), ctx.getString(R.string.sched_reminder_text, rule.name, min))
    }

    /** Honest about enforcement: only says blocking is on if the accessibility service is really connected. */
    override fun started(rule: ScheduleRule, sessionStarted: Boolean) {
        val blocking = ctx.getString(if (FocusAccessibilityService.connected) R.string.sched_blocking_on else R.string.sched_blocking_off)
        val head = ctx.getString(if (sessionStarted) R.string.sched_started_session else R.string.sched_started_window, rule.name)
        post(2500 + (rule.id % 500).toInt(), ctx.getString(R.string.sched_started_title), "$head $blocking")
    }
}

class AchievementNotifierImpl(private val ctx: Context) : AchievementNotifier {
    override fun unlocked(id: AchievementId) {
        val n = Notification.Builder(ctx, Notifications.CH_ALERTS).setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(ctx.getString(R.string.ach_unlocked)).setContentText(ctx.getString(AchievementText.title(id)))
            .setAutoCancel(true).setContentIntent(NotificationPresenter.openApp(ctx)).build()
        ctx.service<NotificationManager>().notify(3000 + id.ordinal, n)
    }
}

object AchievementText {
    fun title(id: AchievementId): Int = when (id) {
        AchievementId.FIRST_SESSION -> R.string.ach_first_session
        AchievementId.FOCUS_1H -> R.string.ach_focus_1h
        AchievementId.FOCUS_5H -> R.string.ach_focus_5h
        AchievementId.FOCUS_10H -> R.string.ach_focus_10h
        AchievementId.STREAK_3 -> R.string.ach_streak_3
        AchievementId.STREAK_7 -> R.string.ach_streak_7
        AchievementId.STREAK_30 -> R.string.ach_streak_30
        AchievementId.DISTRACTION_FREE_DAY -> R.string.ach_distraction_free
        AchievementId.WEEKLY_CHAMPION -> R.string.ach_weekly_champion
    }
    fun description(id: AchievementId): Int = when (id) {
        AchievementId.FIRST_SESSION -> R.string.achd_first_session
        AchievementId.FOCUS_1H -> R.string.achd_focus_1h
        AchievementId.FOCUS_5H -> R.string.achd_focus_5h
        AchievementId.FOCUS_10H -> R.string.achd_focus_10h
        AchievementId.STREAK_3 -> R.string.achd_streak_3
        AchievementId.STREAK_7 -> R.string.achd_streak_7
        AchievementId.STREAK_30 -> R.string.achd_streak_30
        AchievementId.DISTRACTION_FREE_DAY -> R.string.achd_distraction_free
        AchievementId.WEEKLY_CHAMPION -> R.string.achd_weekly_champion
    }
}
