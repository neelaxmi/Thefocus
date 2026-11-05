package com.neelaxmi.focus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neelaxmi.focus.R
import com.neelaxmi.focus.core.DashboardState
import com.neelaxmi.focus.core.Motivation
import com.neelaxmi.focus.core.Phase
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.SessionMachine
import com.neelaxmi.focus.core.SessionState
import com.neelaxmi.focus.core.TimeFormat
import com.neelaxmi.focus.ui.Panel
import com.neelaxmi.focus.ui.ProgressRing
import com.neelaxmi.focus.ui.StatTile
import com.neelaxmi.focus.ui.WeekBars
import com.neelaxmi.focus.ui.rememberSecondTick
import java.time.LocalTime

@Composable
fun DashboardScreen(active: Session?, dash: DashboardState?, onStart: () -> Unit, onOpenActive: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(greeting()), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.dash_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (active != null) item { ActiveBanner(active, onOpenActive) }
        if (dash == null) {
            item { Panel { Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(140.dp)) } }
        } else {
            item { GoalCard(dash, active == null, onStart) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("${dash.streak.current}", stringResource(R.string.stat_streak), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    StatTile("${dash.completedToday}", stringResource(R.string.stat_sessions_today), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatTile(TimeFormat.minutes(dash.remainingMin), stringResource(R.string.stat_remaining), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
            }
            item { WeekCard(dash) }
            item { InsightsCard(dash) }
        }
    }
}

private fun greeting(): Int = when (LocalTime.now().hour) {
    in 5..11 -> R.string.greet_morning
    in 12..16 -> R.string.greet_afternoon
    in 17..21 -> R.string.greet_evening
    else -> R.string.greet_night
}

@Composable
private fun GoalCard(d: DashboardState, canStart: Boolean, onStart: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ProgressRing(d.goalProgress, size = 148.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(TimeFormat.minutes(d.todayFocusMin), fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.goal_of, TimeFormat.minutes(d.goalMin)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_focus), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(motivationText(d.motivation)), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (canStart) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.start_focus), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun motivationText(m: Motivation): Int = when (m) {
    Motivation.START_TODAY -> R.string.mot_start_today
    Motivation.GOOD_START -> R.string.mot_good_start
    Motivation.MORE_THAN_HALF -> R.string.mot_more_than_half
    Motivation.ALMOST_THERE -> R.string.mot_almost_there
    Motivation.GOAL_REACHED -> R.string.mot_goal_reached
    Motivation.STREAK_ALIVE -> R.string.mot_streak_alive
}

@Composable
private fun ActiveBanner(s: Session, onOpen: () -> Unit) {
    val tick by rememberSecondTick(true)
    val p = SessionMachine.advance(s, tick).session
    val rem = SessionMachine.remainingMs(p)
    Panel(Modifier.clickable(onClick = onOpen)) {
        Text(stringResource(if (s.state == SessionState.PAUSED) R.string.banner_paused else if (p.phase == Phase.FOCUS) R.string.banner_running else R.string.banner_break), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(TimeFormat.clock(rem ?: p.focusTotalMs), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.banner_tap), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekCard(d: DashboardState) {
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.this_week), style = MaterialTheme.typography.titleMedium)
            Text(TimeFormat.minutes(d.weekTotalMin), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        WeekBars(d.week, d.goalMin)
    }
}

@Composable
private fun InsightsCard(d: DashboardState) {
    Panel {
        Text(stringResource(R.string.insights), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        InsightRow(stringResource(R.string.insight_avg_day), TimeFormat.minutes(d.weekAvgMin))
        InsightRow(stringResource(R.string.insight_avg_session), if (d.sessions30d == 0) "–" else TimeFormat.minutes(d.avgSessionMin))
        InsightRow(stringResource(R.string.insight_best_day), d.bestDay?.let { "${it.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar(Char::uppercase)} · ${TimeFormat.minutes(it.focusMin)}" } ?: "–")
        InsightRow(stringResource(R.string.insight_vs_last_week), d.weekChangePct?.let { (if (it >= 0) "+" else "") + "$it%" } ?: "–")
        InsightRow(stringResource(R.string.insight_sessions_30d), "${d.sessions30d}")
        Text(stringResource(R.string.insight_note), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
    }
}

@Composable
private fun InsightRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
