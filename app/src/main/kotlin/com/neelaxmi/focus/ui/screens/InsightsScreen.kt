package com.neelaxmi.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neelaxmi.focus.R
import com.neelaxmi.focus.core.AchievementProgress
import com.neelaxmi.focus.core.ProgressUnit
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.TimeFormat
import com.neelaxmi.focus.core.UsageReport
import com.neelaxmi.focus.platform.AchievementText
import com.neelaxmi.focus.ui.AppIcon
import com.neelaxmi.focus.ui.InsightsState
import com.neelaxmi.focus.ui.Panel
import com.neelaxmi.focus.ui.StatTile
import com.neelaxmi.focus.ui.ThinProgress
import com.neelaxmi.focus.ui.TapBars
import com.neelaxmi.focus.ui.UsageRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

class InsightsActions(val setRange: (UsageRange) -> Unit, val refresh: () -> Unit, val openUsageAccess: () -> Unit, val dismissError: () -> Unit)

@Composable
fun InsightsScreen(state: InsightsState, history: List<Session>, a: InsightsActions) {
    var section by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Text(stringResource(R.string.insights_title), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(section == 0, { section = 0 }, { Text(stringResource(R.string.seg_usage)) })
            FilterChip(section == 1, { section = 1 }, { Text(stringResource(R.string.tab_history)) })
            FilterChip(section == 2, { section = 2 }, { Text(stringResource(R.string.seg_achievements)) })
        }
        when (section) {
            0 -> UsageSection(state, a)
            1 -> HistoryScreen(history)
            else -> AchievementsSection(state)
        }
    }
}

@Composable
private fun UsageSection(s: InsightsState, a: InsightsActions) {
    LazyColumn(contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        s.error?.let { e -> item { ErrorCard(e, onRetry = { a.dismissError(); a.refresh() }) } }
        if (s.loading) { item { Panel { Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(120.dp)) } }; return@LazyColumn }

        if (!s.usageAccess) {
            item { NoAccessCard(a.openUsageAccess) }
            return@LazyColumn            // NO numbers without real data: nothing below is rendered
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(s.range == UsageRange.WEEK, { a.setRange(UsageRange.WEEK) }, { Text(stringResource(R.string.range_week)) })
                FilterChip(s.range == UsageRange.MONTH, { a.setRange(UsageRange.MONTH) }, { Text(stringResource(R.string.range_month)) })
                TextButton(onClick = a.refresh) { Text(stringResource(R.string.usage_refresh)) }
            }
        }
        val r = s.report
        if (r == null || r.daysWithData == 0) { item { EmptyUsageCard() }; return@LazyColumn }

        item { SummaryTiles(r, s) }
        item { DailyChart(r, s) }
        item { FocusVsDistractionCard(s) }
        item { AppsCard(stringResource(R.string.top_apps), r.topApps.map { it.pkg to it.ms }, s.labels, r.topApps.firstOrNull()?.ms ?: 1L, MaterialTheme.colorScheme.primary) }
        if (r.topDistracting.isNotEmpty()) item { AppsCard(stringResource(R.string.top_distracting), r.topDistracting.map { it.pkg to it.ms }, s.labels, r.topDistracting.first().ms, MaterialTheme.colorScheme.secondary) }
        item { SourceNote(s.firstDataDay) }
    }
}

@Composable
private fun NoAccessCard(onOpen: () -> Unit) {
    Panel {
        Text(stringResource(R.string.usage_noaccess_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.usage_noaccess_text), Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onOpen, Modifier.fillMaxWidth()) { Text(stringResource(R.string.usage_grant)) }
    }
}

@Composable
private fun EmptyUsageCard() = Panel {
    Text(stringResource(R.string.usage_empty_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.usage_empty_text), Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SummaryTiles(r: UsageReport, s: InsightsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(TimeFormat.minutes((r.avgPerDayMs / 60_000L).toInt()), stringResource(R.string.usage_avg_day), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatTile("${r.unlocks}", stringResource(R.string.usage_unlocks), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(s.productivityToday?.let { "$it" } ?: "—", stringResource(R.string.usage_score), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            StatTile(r.trendPct?.let { (if (it >= 0) "+" else "") + "$it%" } ?: "—", stringResource(R.string.usage_trend), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
        Text(stringResource(R.string.usage_score_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DailyChart(r: UsageReport, s: InsightsState) {
    var sel by remember(r.from, r.to) { mutableStateOf<Int?>(r.days.lastIndex) }
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Panel {
        Text(stringResource(R.string.usage_daily), style = MaterialTheme.typography.titleMedium)
        val d = sel?.let { r.days.getOrNull(it) }
        Text(
            if (d == null) stringResource(R.string.usage_tap) else d.date.format(fmt) + " · " + if (d.hasData) TimeFormat.minutes((d.totalMs / 60_000L).toInt()) + " · " + stringResource(R.string.usage_unlocks_n, d.unlocks) else d.date.format(fmt) + " · " + stringResource(R.string.usage_no_data_day),
            Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TapBars(r.days.map { if (it.hasData) it.totalMs else null }, r.days.map { label(it.date, r.days.size) }, sel, { sel = it }, MaterialTheme.colorScheme.primary)
    }
}

private fun label(d: LocalDate, n: Int) = if (n <= 7) d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) else if (d.dayOfMonth % 5 == 0) "${d.dayOfMonth}" else ""

@Composable
private fun FocusVsDistractionCard(s: InsightsState) {
    val f = s.focusVsDistraction
    var sel by remember(f.size) { mutableStateOf<Int?>(f.lastIndex) }
    Panel {
        Text(stringResource(R.string.usage_focus_vs), style = MaterialTheme.typography.titleMedium)
        val p = sel?.let { f.getOrNull(it) }
        Text(
            if (p == null) stringResource(R.string.usage_tap) else p.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + " · " +
                stringResource(R.string.usage_focus_n, TimeFormat.minutes(p.focusMin)) + " · " + (p.distractingMin?.let { stringResource(R.string.usage_distract_n, TimeFormat.minutes(it)) } ?: stringResource(R.string.usage_no_data_day)),
            Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.usage_focus_legend), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        TapBars(f.map { it.focusMin.toLong() }, f.map { label(it.date, f.size) }, sel, { sel = it }, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.usage_distract_legend), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        TapBars(f.map { it.distractingMin?.toLong() }, f.map { "" }, sel, { sel = it }, MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun AppsCard(title: String, rows: List<Pair<String, Long>>, labels: Map<String, String>, max: Long, accent: androidx.compose.ui.graphics.Color) {
    Panel {
        Text(title, style = MaterialTheme.typography.titleMedium)
        rows.forEach { (pkg, ms) ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 36.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(labels[pkg] ?: pkg, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                    ThinProgress(ms.toFloat() / max, Modifier.padding(top = 4.dp), accent)
                }
                Text(TimeFormat.minutes((ms / 60_000L).toInt()), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SourceNote(first: LocalDate?) {
    Text(
        stringResource(R.string.usage_source_note) + (first?.let { " " + stringResource(R.string.usage_since, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) } ?: ""),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AchievementsSection(s: InsightsState) {
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    LazyColumn(contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val done = s.achievements.count { it.unlocked }
            Text(stringResource(R.string.ach_summary, done, s.achievements.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!s.usageAccess) item { Text(stringResource(R.string.ach_needs_usage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
        items(s.achievements, key = { it.id.name }) { p -> AchievementCard(p, s.unlockedAt[p.id]?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt) }) }
    }
}

@Composable
private fun AchievementCard(p: AchievementProgress, unlockedOn: String?) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(AchievementText.title(p.id)), style = MaterialTheme.typography.titleMedium, color = if (p.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(stringResource(AchievementText.description(p.id)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(if (p.unlocked) R.string.ach_state_unlocked else R.string.ach_state_locked), style = MaterialTheme.typography.labelLarge, color = if (p.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ThinProgress(p.fraction, Modifier.padding(top = 10.dp), if (p.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
        Text(
            progressText(p) + (unlockedOn?.let { " · " + stringResource(R.string.ach_unlocked_on, it) } ?: ""),
            Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun progressText(p: AchievementProgress): String = when (p.unit) {
    ProgressUnit.SESSIONS -> stringResource(R.string.ach_progress_sessions, p.current, p.target)
    ProgressUnit.MINUTES -> TimeFormat.minutes(p.current.toInt()) + " / " + TimeFormat.minutes(p.target.toInt())
    ProgressUnit.DAYS -> stringResource(R.string.ach_progress_days, p.current, p.target)
}
