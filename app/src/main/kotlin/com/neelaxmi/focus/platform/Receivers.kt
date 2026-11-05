package com.neelaxmi.focus.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

private fun loc(ctx: Context) = ctx.applicationContext as ServiceLocator
private inline fun BroadcastReceiver.onController(ctx: Context, crossinline block: (ServiceLocator) -> Unit) {
    val pending = goAsync()
    val l = loc(ctx)
    l.io.execute { try { block(l) } finally { pending.finish() } }
}
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WAKE) onController(context) { it.controller.sync() }
    }
    companion object { const val ACTION_WAKE = "com.neelaxmi.focus.action.WAKE" }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> onController(context) { it.controller.sync() }
        }
    }
}
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
