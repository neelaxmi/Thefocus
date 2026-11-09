package com.neelaxmi.focus.core

import java.time.LocalDate

data class StreakResult(val current: Int, val longest: Int)

object Streaks {
    /**
     * A day counts when focus minutes >= [goalMinutes]. Today does not break the current streak while it is
     * still in progress: if today has not met the goal yet, the streak is counted up to yesterday.
     */
    fun compute(minutesByDay: Map<LocalDate, Int>, goalMinutes: Int, today: LocalDate): StreakResult {
        require(goalMinutes > 0)
        fun met(d: LocalDate) = (minutesByDay[d] ?: 0) >= goalMinutes

        var cursor = if (met(today)) today else today.minusDays(1)
        var current = 0
        while (met(cursor)) { current++; cursor = cursor.minusDays(1) }

        val days = minutesByDay.keys.filter { it <= today && met(it) }.sorted()
        var longest = 0; var run = 0; var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > longest) longest = run
            prev = d
        }
        return StreakResult(current, longest)
    }
}
