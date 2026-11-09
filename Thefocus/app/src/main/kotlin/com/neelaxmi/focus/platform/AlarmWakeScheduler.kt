package com.neelaxmi.focus.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.neelaxmi.focus.core.WakeScheduler

/**
 * Uses the MONOTONIC alarm clock (ELAPSED_REALTIME_WAKEUP) so that changing the wall clock cannot move the end of a
 * session. Exact + Doze-exempt when the exact-alarm permission is held; otherwise falls back to an inexact
 * Doze-tolerant alarm (may fire minutes late; the foreground service's own timer covers the gap while it lives).
 */
class AlarmWakeScheduler(private val ctx: Context) : WakeScheduler {
    private val am = ctx.service<AlarmManager>()

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, WakeReceiver::class.java).setAction(WakeReceiver.ACTION_WAKE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun scheduleIn(delayMs: Long) {
        val at = SystemClock.elapsedRealtime() + delayMs
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending())
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending())
        } catch (e: SecurityException) {            // permission revoked between check and call
            Log.w(TAG, "exact alarm refused, using inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending())
        }
    }

    override fun cancel() { am.cancel(pending()) }

    private companion object { const val TAG = "WakeScheduler" }
}
