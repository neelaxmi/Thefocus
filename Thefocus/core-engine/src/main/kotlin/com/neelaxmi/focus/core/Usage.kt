package com.neelaxmi.focus.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

enum class EventType { FOREGROUND, BACKGROUND, SCREEN_ON, SCREEN_OFF, UNLOCK }
data class UsageEvent(val tsMs: Long, val pkg: String?, val type: EventType)
data class UsageInterval(val pkg: String, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Turns raw OS usage events into foreground intervals. Android can omit BACKGROUND events, so an interval
 * is also closed by (a) another app moving to the foreground, (b) the screen turning off, (c) the end of the window.
 * An interval that is still open at the end of the window is capped at [maxOpenMs] to avoid inflating usage
 * from a lost event. All of these are deliberate, documented approximations.
 */
object UsageSessionizer {

    fun intervals(
        events: List<UsageEvent>,
        windowEndMs: Long,
        maxOpenMs: Long = 2 * 60 * 60 * 1000L,
        mergeGapMs: Long = 2_000L,
    ): List<UsageInterval> {
        val out = mutableListOf<UsageInterval>()
        var curPkg: String? = null
        var curStart = 0L

        fun close(at: Long) {
            val p = curPkg ?: return
            if (at > curStart) out += UsageInterval(p, curStart, at)
            curPkg = null
        }

        for (e in events.sortedBy { it.tsMs }) {
            when (e.type) {
                EventType.FOREGROUND -> {
                    val p = e.pkg ?: continue
                    if (curPkg == p) continue
                    close(e.tsMs)
                    curPkg = p; curStart = e.tsMs
                }
                EventType.BACKGROUND -> if (curPkg != null && curPkg == e.pkg) close(e.tsMs)
                EventType.SCREEN_OFF -> close(e.tsMs)
                EventType.SCREEN_ON, EventType.UNLOCK -> Unit
            }
        }
        if (curPkg != null) close(minOf(windowEndMs, curStart + maxOpenMs))

        // merge near-adjacent intervals of the same app (activity switches inside one app)
        val merged = mutableListOf<UsageInterval>()
        for (iv in out) {
            val last = merged.lastOrNull()
            if (last != null && last.pkg == iv.pkg && iv.startMs - last.endMs <= mergeGapMs) {
                merged[merged.lastIndex] = last.copy(endMs = iv.endMs)
            } else merged += iv
        }
        return merged
    }

    /** Splits intervals at local-midnight boundaries (DST-safe) and sums per day per app. */
    fun dailyTotals(intervals: List<UsageInterval>, zone: ZoneId): Map<LocalDate, Map<String, Long>> {
        val res = HashMap<LocalDate, HashMap<String, Long>>()
        for (iv in intervals) {
            var cursor = ZonedDateTime.ofInstant(Instant.ofEpochMilli(iv.startMs), zone)
            val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(iv.endMs), zone)
            while (cursor.isBefore(end)) {
                val nextMidnight = cursor.toLocalDate().plusDays(1).atStartOfDay(zone)
                val sliceEnd = if (nextMidnight.isBefore(end)) nextMidnight else end
                val ms = ChronoUnit.MILLIS.between(cursor, sliceEnd)
                res.getOrPut(cursor.toLocalDate()) { HashMap() }.merge(iv.pkg, ms, Long::plus)
                cursor = sliceEnd
            }
        }
        return res
    }

    /** Total usage (all apps) per local date-hour, e.g. key = 2026-11-04T09:00. */
    fun hourlyTotals(intervals: List<UsageInterval>, zone: ZoneId): Map<java.time.LocalDateTime, Long> {
        val res = HashMap<java.time.LocalDateTime, Long>()
        for (iv in intervals) {
            var cursor = ZonedDateTime.ofInstant(Instant.ofEpochMilli(iv.startMs), zone)
            val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(iv.endMs), zone)
            while (cursor.isBefore(end)) {
                val nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                val sliceEnd = if (nextHour.isBefore(end)) nextHour else end
                res.merge(cursor.truncatedTo(ChronoUnit.HOURS).toLocalDateTime(), ChronoUnit.MILLIS.between(cursor, sliceEnd), Long::plus)
                cursor = sliceEnd
            }
        }
        return res
    }
}
