package com.neelaxmi.focus.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class AchievementId { FIRST_SESSION, FOCUS_1H, FOCUS_5H, FOCUS_10H, STREAK_3, STREAK_7, STREAK_30, DISTRACTION_FREE_DAY, WEEKLY_CHAMPION }

/** [current]/[target] are in the unit named by [unit]. */
enum class ProgressUnit { SESSIONS, MINUTES, DAYS }
data class AchievementProgress(val id: AchievementId, val current: Long, val target: Long, val unit: ProgressUnit, val unlocked: Boolean) {
    val fraction: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
}

/** Facts about one day taken from persisted usage rollups; absent day = no usage data (so it can never earn "distraction-free"). */
data class DayFacts(val distractingMs: Long)

data class AchievementInput(
    val sessions: List<Session>, val goalMin: Int, val today: LocalDate, val zone: ZoneId, val usageFacts: Map<LocalDate, DayFacts>,
)

object Achievements {
    const val DISTRACTION_FREE_MAX_MS = 10 * 60_000L
    const val WEEKLY_CHAMPION_DAYS = 5

    /** Same counting rule as the dashboard: finished sessions with an untouched clock. */
    fun counted(sessions: List<Session>) = sessions.filter { it.isFinished && !it.clockAdjusted }

    fun evaluate(i: AchievementInput): List<AchievementProgress> {
        val ss = counted(i.sessions)
        fun day(s: Session) = Instant.ofEpochMilli(s.startedAtWallMs).atZone(i.zone).toLocalDate()
        val minutesByDay: Map<LocalDate, Int> = ss.groupBy(::day).mapValues { (_, l) -> (l.sumOf { it.focusMs } / 60_000L).toInt() }
        val totalMin = ss.sumOf { it.focusMs } / 60_000L
        val streak = Streaks.compute(minutesByDay, i.goalMin, i.today)

        val dfDays = minutesByDay.keys.count { d ->
            ss.any { day(it) == d } && (i.usageFacts[d]?.let { it.distractingMs <= DISTRACTION_FREE_MAX_MS } == true)
        }
        // Weekly champion: best number of goal-met days inside any calendar week (Mon-Sun)
        val goalDays = minutesByDay.filter { (d, m) -> !d.isAfter(i.today) && m >= i.goalMin }.keys
        val bestWeek = goalDays.groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.values.maxOfOrNull { it.size } ?: 0

        fun p(id: AchievementId, cur: Long, target: Long, unit: ProgressUnit) = AchievementProgress(id, cur.coerceAtMost(target), target, unit, cur >= target)
        return listOf(
            p(AchievementId.FIRST_SESSION, ss.count { it.state == SessionState.COMPLETED }.toLong(), 1, ProgressUnit.SESSIONS),
            p(AchievementId.FOCUS_1H, totalMin, 60, ProgressUnit.MINUTES),
            p(AchievementId.FOCUS_5H, totalMin, 300, ProgressUnit.MINUTES),
            p(AchievementId.FOCUS_10H, totalMin, 600, ProgressUnit.MINUTES),
            p(AchievementId.STREAK_3, streak.longest.toLong(), 3, ProgressUnit.DAYS),
            p(AchievementId.STREAK_7, streak.longest.toLong(), 7, ProgressUnit.DAYS),
            p(AchievementId.STREAK_30, streak.longest.toLong(), 30, ProgressUnit.DAYS),
            p(AchievementId.DISTRACTION_FREE_DAY, dfDays.toLong(), 1, ProgressUnit.DAYS),
            p(AchievementId.WEEKLY_CHAMPION, bestWeek.toLong(), WEEKLY_CHAMPION_DAYS.toLong(), ProgressUnit.DAYS),
        )
    }

    /** Unlocked now but not yet stored. Unlocks are permanent: an achievement never re-locks if data changes later. */
    fun newlyUnlocked(alreadyStored: Set<AchievementId>, evaluated: List<AchievementProgress>): List<AchievementId> =
        evaluated.filter { it.unlocked && it.id !in alreadyStored }.map { it.id }
}
