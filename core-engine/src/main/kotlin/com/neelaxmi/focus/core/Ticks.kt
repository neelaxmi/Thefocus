package com.neelaxmi.focus.core

import kotlin.math.abs

/** A paired wall and monotonic clock reading for clock-change and reboot recovery. */
data class Tick(val wallMs: Long, val monoMs: Long)
data class TickDelta(val ms: Long, val clockAdjusted: Boolean, val rebooted: Boolean)

object Ticks {
    const val TOLERANCE_MS = 30_000L
    fun delta(from: Tick, to: Tick): TickDelta {
        val wall = to.wallMs - from.wallMs
        val mono = to.monoMs - from.monoMs
        return when {
            mono < 0 -> if (wall < 0) TickDelta(0, true, true) else TickDelta(wall, false, true)
            abs(wall - mono) <= TOLERANCE_MS -> TickDelta(mono, false, false)
            else -> TickDelta(mono, true, false)
        }
    }
}
