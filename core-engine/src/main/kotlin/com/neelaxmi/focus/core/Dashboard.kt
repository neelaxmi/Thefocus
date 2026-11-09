package com.neelaxmi.focus.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Motivation { START_TODAY, GOOD_START, MORE_THAN_HALF, ALMOST_THERE, GOAL_REACHED, STREAK_ALIVE }

data class DayBar(val date: LocalDate, val focusMin: Int, val goalMet: Boolean)

data class DashboardState(
    val todayFocusMin: Int,
    val goalMin: Int,
    /** 0.0 .. 1.0 (clamped) */
    val goalProgress: Float,
    val remainingMin: Int,
    val completedToday: Int,
    val streak: StreakResult,
    /** Seven bars, oldest first, ending today. */
    val week: List<DayBar>,
    val weekTotalMin: Int,
    val weekAvgMin: Int,
    val bestDay: DayBar?,
    /** vs the previous 7 days; null when there is no previous data to compare with. */
    val weekChangePct: Int?,
    val sessions30d: Int,
    val avgSessionMin: Int,
    val motivation: Motivation,
)

/**
 * Every number comes from stored sessions. Rules (documented because they are product decisions):
 *  - Only finished sessions without clock tampering count.
 *  - Focus minutes = real focus time (completed AND early-ended sessions both count what was actually focused).
 *  - A session belongs to the local day it started on (a session that crosses midnight is not split).
 *  - "Completed" = ran to its planned end (or stopwatch stopped normally).
 */
object DashboardCalculator {
    fun compute(sessions: List<Session>, goalMin: Int, today: LocalDate, zone: ZoneId): DashboardState {
        require(goalMin > 0)
        val counted = sessions.filter { it.isFinished && !it.clockAdjusted }
        fun day(s: Session) = Instant.ofEpochMilli(s.startedAtWallMs).atZone(zone).toLocalDate()

        val minutesByDay: Map<LocalDate, Int> = counted.groupBy(::day).mapValues { (_, l) -> (l.sumOf { it.focusMs } / 60_000L).toInt() }

        val todayMin = minutesByDay[today] ?: 0
        val week = (6L downTo 0L).map { off ->
            val d = today.minusDays(off); val m = minutesByDay[d] ?: 0
            DayBar(d, m, m >= goalMin)
        }
        val weekTotal = week.sumOf { it.focusMin }
        val prevWeekTotal = (7L..13L).sumOf { minutesByDay[today.minusDays(it)] ?: 0 }
        val change = if (prevWeekTotal > 0) (((weekTotal - prevWeekTotal) * 100.0) / prevWeekTotal).toInt() else null

        val last30 = counted.filter { day(it) > today.minusDays(30) && day(it) <= today }
        val streak = Streaks.compute(minutesByDay, goalMin, today)
        val progress = (todayMin.toFloat() / goalMin).coerceIn(0f, 1f)

        val motivation = when {
            todayMin >= goalMin && streak.current >= 3 -> Motivation.STREAK_ALIVE
            todayMin >= goalMin -> Motivation.GOAL_REACHED
            todayMin == 0 -> Motivation.START_TODAY
            progress >= 0.75f -> Motivation.ALMOST_THERE
            progress >= 0.5f -> Motivation.MORE_THAN_HALF
            else -> Motivation.GOOD_START
        }
        return DashboardState(
            todayFocusMin = todayMin, goalMin = goalMin, goalProgress = progress,
            remainingMin = (goalMin - todayMin).coerceAtLeast(0),
            completedToday = counted.count { day(it) == today && it.state == SessionState.COMPLETED },
            streak = streak, week = week, weekTotalMin = weekTotal, weekAvgMin = weekTotal / 7,
            bestDay = week.filter { it.focusMin > 0 }.maxByOrNull { it.focusMin },
            weekChangePct = change, sessions30d = last30.size,
            avgSessionMin = if (last30.isEmpty()) 0 else (last30.sumOf { it.focusMs } / last30.size / 60_000L).toInt(),
            motivation = motivation,
        )
    }
}
