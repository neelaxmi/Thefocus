package com.neelaxmi.focus.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

private fun loc(ctx: Context) = ctx.applicationContext as ServiceLocator

/** Runs [block] on the controller thread while keeping the receiver alive (goAsync) until it finishes. */
private inline fun BroadcastReceiver.onController(ctx: Context, crossinline block: (ServiceLocator) -> Unit) {
    val pending = goAsync()
    val l = loc(ctx)
    l.io.execute { try { block(l) } finally { pending.finish() } }
}

/** Fired by AlarmManager at the end of a phase. Process may have been dead: the controller rebuilds state from storage. */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WAKE) onController(context) { it.controller.sync() }
    }
    companion object { const val ACTION_WAKE = "com.neelaxmi.focus.action.WAKE" }
}

/**
 * Re-arms everything after reboot / app update / clock or zone change (session alarm, schedule alarm, blocking policy,
 * rollup alarm). Alarms are wiped by a reboot, so this is what makes a running session and every schedule survive it. Only fires after the user unlocks the device (the database lives in
 * credential-encrypted storage; direct-boot is intentionally not used).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> onController(context) { it.resyncAll() }
        }
    }
}

/** Fired by the wall-clock alarm at a schedule boundary (start / end / reminder). */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) onController(context) { it.resyncAll() }
    }
    companion object { const val ACTION = "com.neelaxmi.focus.action.SCHEDULE_WAKE" }
}

/** Periodic: persist usage rollups and evaluate achievements. */
class RollupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RollupAlarm.ACTION) onController(context) { it.runRollup() }
    }
}

/** Notification buttons (Pause / Resume / Skip break). Never offered for strict sessions. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        onController(context) {
            when (action) {
                ACTION_PAUSE -> it.controller.pause()
                ACTION_RESUME -> it.controller.resume()
                ACTION_SKIP_BREAK -> it.controller.endBreakEarly()
            }
        }
    }
    companion object {
        const val ACTION_PAUSE = "com.neelaxmi.focus.action.PAUSE"
        const val ACTION_RESUME = "com.neelaxmi.focus.action.RESUME"
        const val ACTION_SKIP_BREAK = "com.neelaxmi.focus.action.SKIP_BREAK"
    }
}
