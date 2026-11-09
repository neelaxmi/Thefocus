package com.neelaxmi.focus.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.neelaxmi.focus.R
import com.neelaxmi.focus.core.LogKind
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.Phase
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.SessionMachine
import com.neelaxmi.focus.core.SessionPresenter
import com.neelaxmi.focus.core.SessionState

object Notifications {
    const val CH_ONGOING = "focus_ongoing"
    const val CH_ALERTS = "focus_alerts"
    const val ID_ONGOING = 1001
    const val ID_ALERT = 1002

    fun ensureChannels(ctx: Context) {
        val nm = ctx.service<NotificationManager>()
        nm.createNotificationChannel(NotificationChannel(CH_ONGOING, ctx.getString(R.string.notif_channel_ongoing), NotificationManager.IMPORTANCE_LOW).apply {
            description = ctx.getString(R.string.notif_channel_ongoing_desc); setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, ctx.getString(R.string.notif_channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.notif_channel_alerts_desc); enableVibration(true)
        })
    }
}

/** Android implementation of [SessionPresenter]: a foreground-service notification plus phase alerts. */
class NotificationPresenter(private val ctx: Context) : SessionPresenter {
    private val nm = ctx.service<NotificationManager>()

    override fun ongoing(session: Session) {
        if (FocusService.running) {
            nm.notify(Notifications.ID_ONGOING, buildOngoing(ctx, session))
        } else {
            try {
                ctx.startForegroundService(Intent(ctx, FocusService::class.java).setAction(FocusService.ACTION_START))
            } catch (e: Exception) {
                Log.w(TAG, "cannot start foreground service now: ${e.javaClass.simpleName}", e)
                nm.notify(Notifications.ID_ONGOING, buildOngoing(ctx, session))
            }
        }
    }

    override fun cleared() {
        ctx.stopService(Intent(ctx, FocusService::class.java))
        nm.cancel(Notifications.ID_ONGOING)
    }

    override fun event(kind: LogKind, session: Session) {
        val (title, text) = when {
            kind == LogKind.COMPLETED -> ctx.getString(R.string.alert_done_title) to
                ctx.getString(R.string.alert_done_text, (session.focusMs / 60_000L).toInt())
            session.phase == Phase.FOCUS -> ctx.getString(R.string.alert_focus_title) to ctx.getString(R.string.alert_focus_text)
            else -> ctx.getString(R.string.alert_break_title) to ctx.getString(R.string.alert_break_text)
        }
        val n = Notification.Builder(ctx, Notifications.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_focus).setContentTitle(title).setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM).setAutoCancel(true).setContentIntent(openApp(ctx)).build()
        nm.notify(Notifications.ID_ALERT, n)
    }

    companion object {
        private const val TAG = "NotifPresenter"

        fun openApp(ctx: Context): PendingIntent {
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent()
            return PendingIntent.getActivity(ctx, 10, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun actionIntent(ctx: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, ActionReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun placeholder(ctx: Context): Notification = Notification.Builder(ctx, Notifications.CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_focus).setContentTitle(ctx.getString(R.string.notif_placeholder))
            .setOngoing(true).setContentIntent(openApp(ctx)).build()

        fun buildOngoing(ctx: Context, session: Session): Notification {
            val now = SystemTicks.now()
            val s = SessionMachine.advance(session, now).session
            val remaining = SessionMachine.remainingMs(s)
            val b = Notification.Builder(ctx, Notifications.CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(openApp(ctx))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

            val paused = s.state == SessionState.PAUSED
            b.setContentTitle(ctx.getString(when {
                paused -> R.string.notif_title_paused
                s.phase == Phase.SHORT_BREAK -> R.string.notif_title_short_break
                s.phase == Phase.LONG_BREAK -> R.string.notif_title_long_break
                s.phase == Phase.USER_BREAK -> R.string.notif_title_break
                else -> R.string.notif_title_focus
            }))
            val detail = when (s.mode) {
                Mode.TIMER -> ctx.getString(R.string.notif_text_timer)
                Mode.STOPWATCH -> ctx.getString(R.string.notif_text_stopwatch)
                Mode.POMODORO -> ctx.getString(R.string.notif_text_pomodoro, s.round, s.pomodoro!!.roundsPerCycle)
            }
            b.setContentText(if (s.strict != null) "$detail · " + ctx.getString(R.string.notif_text_strict) else detail)

            if (!paused) {
                b.setShowWhen(true).setUsesChronometer(true)
                if (remaining != null) b.setChronometerCountDown(true).setWhen(System.currentTimeMillis() + remaining)
                else b.setWhen(System.currentTimeMillis() - s.focusTotalMs)
            } else {
                b.setShowWhen(false)
            }

            if (s.strict == null) {
                if (paused) b.addAction(Notification.Action.Builder(null, ctx.getString(R.string.action_resume), actionIntent(ctx, ActionReceiver.ACTION_RESUME, 1)).build())
                else if (s.phase == Phase.FOCUS) b.addAction(Notification.Action.Builder(null, ctx.getString(R.string.action_pause), actionIntent(ctx, ActionReceiver.ACTION_PAUSE, 2)).build())
                else if (s.phase == Phase.USER_BREAK) b.addAction(Notification.Action.Builder(null, ctx.getString(R.string.action_skip_break), actionIntent(ctx, ActionReceiver.ACTION_SKIP_BREAK, 3)).build())
            }
            return b.build()
        }
    }
}
