package com.neelaxmi.focus.core

import kotlin.math.abs

/**
 * A paired clock reading. [wallMs] = System.currentTimeMillis(), [monoMs] = SystemClock.elapsedRealtime().
 * The monotonic value survives user clock changes but resets on reboot; the wall value survives reboot
 * but can be changed by the user. Using both lets us measure elapsed time honestly.
 */
data class Tick(val wallMs: Long, val monoMs: Long)

data class TickDelta(
    /** Best estimate of real elapsed milliseconds (never negative). */
    val ms: Long,
    /** True when the wall clock disagreed with the monotonic clock (user changed time / NTP jump). */
    val clockAdjusted: Boolean,
    /** True when the monotonic clock went backwards, i.e. the device rebooted between the ticks. */
    val rebooted: Boolean,
)

object Ticks {
    /** Wall/monotonic disagreement larger than this is treated as a clock change. */
    const val TOLERANCE_MS = 30_000L

    fun delta(from: Tick, to: Tick): TickDelta {
        val wall = to.wallMs - from.wallMs
        val mono = to.monoMs - from.monoMs
        return when {
            mono < 0 -> // reboot: monotonic clock reset, wall clock is the only evidence we have
                if (wall < 0) TickDelta(0, clockAdjusted = true, rebooted = true)
                else TickDelta(wall, clockAdjusted = false, rebooted = true)
            abs(wall - mono) <= TOLERANCE_MS -> TickDelta(mono, false, false)
            else -> TickDelta(mono, clockAdjusted = true, rebooted = false)
        }
    }
}
