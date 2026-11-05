package com.neelaxmi.focus.core

/** Locale-neutral formatting kept in the tested engine so the UI layer stays declarative. */
object TimeFormat {
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
    fun minutes(min: Int): String {
        val m = min.coerceAtLeast(0)
        return when {
            m < 60 -> m.toString() + "m"
            m % 60 == 0 -> (m / 60).toString() + "h"
            else -> (m / 60).toString() + "h " + (m % 60) + "m"
        }
    }
}
