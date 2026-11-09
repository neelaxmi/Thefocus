package com.neelaxmi.focus.core

/** Locale-neutral formatting kept in the tested engine so the UI layer stays declarative. */
object TimeFormat {
    /** 83_000 -> "01:23"; 3_725_000 -> "1:02:05". Negative values clamp to zero. */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000          // round UP so a countdown shows 00:01 until it really ends
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** 45 -> "45m"; 80 -> "1h 20m"; 120 -> "2h". */
    fun minutes(min: Int): String {
        val m = min.coerceAtLeast(0)
        return when {
            m < 60 -> "${m}m"
            m % 60 == 0 -> "${m / 60}h"
            else -> "${m / 60}h ${m % 60}m"
        }
    }
}
