package com.neelaxmi.focus.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** [daysMask]: bit0 = Monday ... bit6 = Sunday. A window may cross midnight (endMin <= startMin means "until tomorrow"). */
data class ScheduleRule(
    val id: Long, val name: String, val daysMask: Int, val startMin: Int, val endMin: Int,
    val enabled: Boolean, val strict: Boolean, val autoStart: Boolean, val groupIds: Set<Long>,
) {
    /** null when valid. */
    fun validate(): String? = when {
        daysMask and 0x7F == 0 -> "no days selected"
        startMin !in 0..1439 || endMin !in 0..1439 -> "time out of range"
        startMin == endMin -> "start and end are the same"
        else -> null
    }
}

data class Occurrence(val ruleId: Long, val dateKey: String, val startWallMs: Long, val endWallMs: Long)

object ScheduleEngine {
    const val MON = 1; const val TUE = 2; const val WED = 4; const val THU = 8; const val FRI = 16; const val SAT = 32; const val SUN = 64
    const val WEEKDAYS = MON or TUE or WED or THU or FRI
    const val EVERY_DAY = 0x7F

    /** Occurrences starting from [backDays] before today to [forwardDays] after (DST-safe: built from local date/time + zone). */
    fun occurrences(rule: ScheduleRule, nowWall: Long, zone: ZoneId, backDays: Long = 1, forwardDays: Long = 8): List<Occurrence> {
        if (rule.validate() != null) return emptyList()
        val today = Instant.ofEpochMilli(nowWall).atZone(zone).toLocalDate()
        val out = mutableListOf<Occurrence>()
        var d: LocalDate = today.minusDays(backDays)
        val last = today.plusDays(forwardDays)
        while (!d.isAfter(last)) {
            if ((rule.daysMask shr (d.dayOfWeek.value - 1)) and 1 == 1) {
                val start = d.atTime(rule.startMin / 60, rule.startMin % 60).atZone(zone)
                val endDate = if (rule.endMin > rule.startMin) d else d.plusDays(1)
                val end = endDate.atTime(rule.endMin / 60, rule.endMin % 60).atZone(zone)
                out += Occurrence(rule.id, d.toString(), start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
            }
            d = d.plusDays(1)
        }
        return out
    }

    fun activeOccurrence(rule: ScheduleRule, nowWall: Long, zone: ZoneId): Occurrence? =
        if (!rule.enabled) null else occurrences(rule, nowWall, zone).firstOrNull { nowWall >= it.startWallMs && nowWall < it.endWallMs }

    fun isActive(rule: ScheduleRule, nowWall: Long, zone: ZoneId) = activeOccurrence(rule, nowWall, zone) != null

    /** Next instant strictly after [nowWall] at which anything changes: a start, an end, or a reminder ([reminderLeadMin] before a start). */
    fun nextBoundary(rules: List<ScheduleRule>, nowWall: Long, zone: ZoneId, reminderLeadMin: Int = 0): Long? =
        rules.filter { it.enabled }.flatMap { r -> occurrences(r, nowWall, zone).flatMap { o ->
            listOfNotNull(o.startWallMs, o.endWallMs, if (reminderLeadMin > 0) o.startWallMs - reminderLeadMin * 60_000L else null)
        } }.filter { it > nowWall }.minOrNull()

    /** Not-yet-started occurrences, soonest first. */
    fun upcoming(rules: List<ScheduleRule>, nowWall: Long, zone: ZoneId, limit: Int): List<Occurrence> =
        rules.filter { it.enabled }.flatMap { occurrences(it, nowWall, zone) }.filter { it.startWallMs > nowWall }.sortedBy { it.startWallMs }.take(limit)
}

interface ScheduleStore {
    fun rules(): List<ScheduleRule>
    /** Records that [key] ran for [ruleId]; returns true only the FIRST time (idempotency guard against duplicate wake-ups). */
    fun markRun(ruleId: Long, key: String, wallMs: Long): Boolean
}
interface ScheduleNotifier {
    fun reminder(rule: ScheduleRule, startWallMs: Long)
    fun started(rule: ScheduleRule, sessionStarted: Boolean)
}
/** Wall-clock alarm: schedules are defined in local wall time, so a wall-clock (RTC) alarm is the right tool. */
interface ScheduleWaker { fun scheduleAt(wallMs: Long); fun cancel() }

data class ScheduleSyncResult(val startedSessionForRules: List<Long>, val reminded: List<Long>, val nextWakeWallMs: Long?)

/**
 * Applies schedules to the world. Idempotent: calling [sync] twice (duplicate alarm, boot + alarm, app open) never starts a
 * second session or sends a second reminder for the same occurrence. Run on the controller thread.
 */
class ScheduleCoordinator(
    private val store: ScheduleStore, private val ticks: TickSource, private val waker: ScheduleWaker,
    private val notifier: ScheduleNotifier, private val controller: SessionController,
    private val strictParams: () -> Pair<Long, Int>,        // (end-cooldown ms, emergency exits per day)
    private val reminderLeadMin: () -> Int,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun sync(): ScheduleSyncResult {
        val now = ticks.now().wallMs
        val z = zone()
        val lead = reminderLeadMin().coerceAtLeast(0)
        val rules = store.rules().filter { it.enabled && it.validate() == null }
        val started = mutableListOf<Long>(); val reminded = mutableListOf<Long>()

        for (r in rules) {
            val occ = ScheduleEngine.activeOccurrence(r, now, z)
            if (occ != null && store.markRun(r.id, "S:" + occ.dateKey, now)) {
                var did = false
                if (r.autoStart) {
                    val (cooldown, exits) = strictParams()
                    val remaining = (occ.endWallMs - now).coerceAtMost(12 * 3_600_000L)
                    val cfg = StartConfig(Mode.TIMER, plannedMs = remaining, strictEndCooldownMs = if (r.strict) cooldown else null, emergencyExitsPerDay = exits)
                    did = controller.start(cfg) is Outcome.Applied
                    if (did) started += r.id
                }
                notifier.started(r, did)
            }
            if (lead > 0) ScheduleEngine.occurrences(r, now, z).firstOrNull { it.startWallMs > now && now >= it.startWallMs - lead * 60_000L }?.let { up ->
                if (store.markRun(r.id, "R:" + up.dateKey, now)) { notifier.reminder(r, up.startWallMs); reminded += r.id }
            }
        }
        val next = ScheduleEngine.nextBoundary(rules, now, z, lead)
        if (next != null) waker.scheduleAt(next) else waker.cancel()
        return ScheduleSyncResult(started, reminded, next)
    }
}

enum class DaysKind { EVERY_DAY, WEEKDAYS, WEEKEND, CUSTOM }

/** Locale-neutral pieces for displaying a rule; the UI supplies the translated day names. */
object ScheduleFormat {
    fun time(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
    /** True when the window ends on the next calendar day (e.g. 22:00 -> 06:00). */
    fun crossesMidnight(r: ScheduleRule) = r.endMin <= r.startMin
    fun daysKind(mask: Int): DaysKind = when (mask and 0x7F) {
        ScheduleEngine.EVERY_DAY -> DaysKind.EVERY_DAY
        ScheduleEngine.WEEKDAYS -> DaysKind.WEEKDAYS
        ScheduleEngine.SAT or ScheduleEngine.SUN -> DaysKind.WEEKEND
        else -> DaysKind.CUSTOM
    }
    /** 0 = Monday .. 6 = Sunday. */
    fun dayIndexes(mask: Int): List<Int> = (0..6).filter { (mask shr it) and 1 == 1 }
    fun toggleDay(mask: Int, index: Int): Int = (mask xor (1 shl index)) and 0x7F
}
