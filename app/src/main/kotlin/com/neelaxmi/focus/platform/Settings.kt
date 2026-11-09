package com.neelaxmi.focus.platform

import android.content.Context
import com.neelaxmi.focus.core.BreakRules
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.PomodoroConfig
import com.neelaxmi.focus.core.StartConfig

/** User preferences (SharedPreferences, device-local, never uploaded). Minutes everywhere except where noted. */
class Settings(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var dailyGoalMin: Int get() = p.getInt("goal", 120); set(v) = p.edit().putInt("goal", v.coerceIn(10, 720)).apply()
    var timerMin: Int get() = p.getInt("timer", 25); set(v) = p.edit().putInt("timer", v.coerceIn(1, 360)).apply()
    var pomoFocusMin: Int get() = p.getInt("pf", 25); set(v) = p.edit().putInt("pf", v.coerceIn(1, 180)).apply()
    var pomoShortMin: Int get() = p.getInt("ps", 5); set(v) = p.edit().putInt("ps", v.coerceIn(1, 60)).apply()
    var pomoLongMin: Int get() = p.getInt("pl", 15); set(v) = p.edit().putInt("pl", v.coerceIn(1, 120)).apply()
    var pomoRounds: Int get() = p.getInt("pr", 4); set(v) = p.edit().putInt("pr", v.coerceIn(1, 12)).apply()
    var pomoCycles: Int get() = p.getInt("pc", 2); set(v) = p.edit().putInt("pc", v.coerceIn(1, 12)).apply()
    var breaksAllowed: Int get() = p.getInt("ba", 0); set(v) = p.edit().putInt("ba", v.coerceIn(0, 10)).apply()
    var breakMin: Int get() = p.getInt("bm", 5); set(v) = p.edit().putInt("bm", v.coerceIn(1, 60)).apply()
    var strictEnabled: Boolean get() = p.getBoolean("strict", false); set(v) = p.edit().putBoolean("strict", v).apply()
    var strictCooldownSec: Int get() = p.getInt("sc", 120); set(v) = p.edit().putInt("sc", v.coerceIn(10, 900)).apply()
    var emergencyExitsPerDay: Int get() = p.getInt("ee", 1); set(v) = p.edit().putInt("ee", v.coerceIn(0, 5)).apply()
    var reminderLeadMin: Int get() = p.getInt("rl", 5); set(v) = p.edit().putInt("rl", v.coerceIn(0, 60)).apply()
    var a11yDisclosureAccepted: Boolean get() = p.getBoolean("a11y", false); set(v) = p.edit().putBoolean("a11y", v).apply()
    var lastMode: Mode get() = runCatching { Mode.valueOf(p.getString("mode", "TIMER")!!) }.getOrDefault(Mode.TIMER); set(v) = p.edit().putString("mode", v.name).apply()

    private fun m(min: Int) = min * 60_000L

    fun toStartConfig(mode: Mode, timerOverrideMin: Int? = null): StartConfig {
        val strict = if (strictEnabled) strictCooldownSec * 1_000L else null
        return when (mode) {
            Mode.TIMER -> StartConfig(Mode.TIMER, plannedMs = m(timerOverrideMin ?: timerMin), breaks = BreakRules(breaksAllowed, m(breakMin)),
                strictEndCooldownMs = strict, emergencyExitsPerDay = emergencyExitsPerDay)
            Mode.STOPWATCH -> StartConfig(Mode.STOPWATCH, breaks = BreakRules(breaksAllowed, m(breakMin)),
                strictEndCooldownMs = strict, emergencyExitsPerDay = emergencyExitsPerDay)
            Mode.POMODORO -> StartConfig(Mode.POMODORO, pomodoro = PomodoroConfig(m(pomoFocusMin), m(pomoShortMin), m(pomoLongMin), pomoRounds, pomoCycles),
                strictEndCooldownMs = strict, emergencyExitsPerDay = emergencyExitsPerDay)
        }
    }
}
