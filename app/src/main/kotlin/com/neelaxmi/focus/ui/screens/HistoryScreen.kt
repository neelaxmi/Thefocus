package com.neelaxmi.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neelaxmi.focus.R
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.SessionState
import com.neelaxmi.focus.core.TimeFormat
import com.neelaxmi.focus.ui.Panel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(history: List<Session>) {
    val zone = ZoneId.systemDefault()
    if (history.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.history_empty_text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    val byDay: Map<LocalDate, List<Session>> = history.groupBy { Instant.ofEpochMilli(it.startedAtWallMs).atZone(zone).toLocalDate() }
    val dayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    LazyColumn(contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium) }
        byDay.forEach { (day, list) ->
            item(key = "h$day") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(day.format(dayFmt), style = MaterialTheme.typography.titleMedium)
                    Text(TimeFormat.minutes((list.filter { !it.clockAdjusted }.sumOf { it.focusMs } / 60_000L).toInt()), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
            }
            items(list, key = { it.id }) { s ->
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(Instant.ofEpochMilli(s.startedAtWallMs).atZone(zone).toLocalTime().format(timeFmt) + " · " + stringResource(modeName(s.mode)), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(statusText(s)), color = if (s.clockAdjusted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(TimeFormat.minutes((s.focusMs / 60_000L).toInt()), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
    }
}

private fun modeName(m: Mode) = when (m) { Mode.TIMER -> R.string.mode_timer; Mode.STOPWATCH -> R.string.mode_stopwatch; Mode.POMODORO -> R.string.mode_pomodoro }
private fun statusText(s: Session) = when {
    s.clockAdjusted -> R.string.status_clock_changed
    s.state == SessionState.COMPLETED -> R.string.status_completed
    else -> R.string.status_ended_early
}
