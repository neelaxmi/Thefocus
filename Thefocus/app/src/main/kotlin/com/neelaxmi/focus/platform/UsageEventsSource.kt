package com.neelaxmi.focus.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.neelaxmi.focus.core.EventType
import com.neelaxmi.focus.core.UsageEvent
import com.neelaxmi.focus.core.UsageEventSource

/**
 * REAL usage from the OS (UsageStatsManager events). Needs the special "Usage access" grant; without it [hasAccess] is
 * false and callers must show "no data", never a made-up number. Accuracy note: results can differ slightly from the
 * system Digital Wellbeing screen (split-screen, picture-in-picture, activities that never report a pause event).
 */
class UsageEventsSource(private val ctx: Context) : UsageEventSource {

    @Suppress("DEPRECATION")
    override fun hasAccess(): Boolean {
        val ops = ctx.service<AppOpsManager>()
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
                   else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    override fun read(fromMs: Long, toMs: Long): List<UsageEvent> {
        val usm = ctx.service<UsageStatsManager>()
        val events = usm.queryEvents(fromMs, toMs) ?: return emptyList()
        val e = UsageEvents.Event()
        val out = ArrayList<UsageEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> EventType.FOREGROUND            // == ACTIVITY_RESUMED (1)
                UsageEvents.Event.MOVE_TO_BACKGROUND -> EventType.BACKGROUND            // == ACTIVITY_PAUSED (2)
                UsageEvents.Event.SCREEN_INTERACTIVE -> EventType.SCREEN_ON
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> EventType.SCREEN_OFF
                UsageEvents.Event.KEYGUARD_HIDDEN -> EventType.UNLOCK                   // counted as an unlock (API 28+)
                else -> continue
            }
            val pkg = if (type == EventType.FOREGROUND || type == EventType.BACKGROUND) e.packageName else null
            out += UsageEvent(e.timeStamp, pkg, type)
        }
        return out
    }
}
