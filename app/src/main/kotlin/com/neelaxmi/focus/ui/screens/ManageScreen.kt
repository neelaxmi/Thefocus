package com.neelaxmi.focus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neelaxmi.focus.R
import com.neelaxmi.focus.core.AppRef
import com.neelaxmi.focus.core.BlockGroup
import com.neelaxmi.focus.core.Category
import com.neelaxmi.focus.core.DaysKind
import com.neelaxmi.focus.core.Enforcement
import com.neelaxmi.focus.core.GroupKind
import com.neelaxmi.focus.core.ScheduleEngine
import com.neelaxmi.focus.core.ScheduleFormat
import com.neelaxmi.focus.core.ScheduleRule
import com.neelaxmi.focus.platform.Settings
import com.neelaxmi.focus.ui.AppIcon
import com.neelaxmi.focus.ui.ManageState
import com.neelaxmi.focus.ui.Panel
import com.neelaxmi.focus.ui.theme.Palette
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class ManageActions(
    val setGroupEnabled: (Long, Boolean) -> Unit, val setGroupApps: (Long, Set<String>) -> Unit,
    val createGroup: (String) -> Unit, val renameGroup: (Long, String) -> Unit, val deleteGroup: (Long) -> Unit,
    val saveRule: (ScheduleRule) -> Unit, val setRuleEnabled: (Long, Boolean) -> Unit, val deleteRule: (Long) -> Unit,
    val dismissError: () -> Unit, val refresh: () -> Unit,
)

@Composable
fun ManageScreen(state: ManageState, settings: Settings, a: ManageActions, onOpenAccessibilitySettings: () -> Unit) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    var pickerGroupId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingRuleId by rememberSaveable { mutableStateOf<Long?>(null) }     // 0 = new
    var showDisclosure by remember { mutableStateOf(false) }

    val picking = state.groups.firstOrNull { it.id == pickerGroupId }
    val editing = editingRuleId?.let { id -> if (id == 0L) NEW_RULE else state.rules.firstOrNull { it.id == id } }

    when {
        picking != null -> AppPicker(picking, state.apps, onSave = { a.setGroupApps(picking.id, it); pickerGroupId = null }, onCancel = { pickerGroupId = null })
        editing != null -> ScheduleEditor(editing, state, onSave = { a.saveRule(it); editingRuleId = null }, onCancel = { editingRuleId = null })
        else -> LazyColumn(contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.manage_title), style = MaterialTheme.typography.headlineMedium) }
            if (state.lockedNotice || state.configLocked) item { LockedCard(state.lockedNotice, onDismiss = a.dismissError) }
            state.error?.let { err -> item { ErrorCard(err, onRetry = { a.dismissError(); a.refresh() }) } }
            item { EnforcementCard(state, onEnable = { if (settings.a11yDisclosureAccepted) onOpenAccessibilitySettings() else showDisclosure = true }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(section == 0, { section = 0 }, { Text(stringResource(R.string.seg_groups)) })
                    FilterChip(section == 1, { section = 1 }, { Text(stringResource(R.string.seg_schedules)) })
                }
            }
            if (state.loading) item { Panel { Text(stringResource(R.string.loading_apps), color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(80.dp)) } }
            else if (section == 0) groupsSection(state, a, onEditApps = { pickerGroupId = it })
            else schedulesSection(state, a, onEdit = { editingRuleId = it })
        }
    }

    if (showDisclosure) AlertDialog(
        onDismissRequest = { showDisclosure = false },
        title = { Text(stringResource(R.string.disc_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(stringResource(R.string.disc_body)) } },
        confirmButton = { TextButton(onClick = { settings.a11yDisclosureAccepted = true; showDisclosure = false; onOpenAccessibilitySettings() }) { Text(stringResource(R.string.disc_agree)) } },
        dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text(stringResource(R.string.dlg_cancel)) } },
    )
}

private val NEW_RULE = ScheduleRule(0, "", ScheduleEngine.WEEKDAYS, 9 * 60, 11 * 60, true, false, true, emptySet())

/** Strict protection is active: groups and schedules cannot be changed until it ends. */
@Composable
private fun LockedCard(refused: Boolean, onDismiss: () -> Unit) {
    Panel {
        Text(stringResource(R.string.manage_locked_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Text(stringResource(R.string.manage_locked_text), Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (refused) TextButton(onClick = onDismiss) { Text(stringResource(R.string.dlg_yes)) }
    }
}

@Composable
fun ErrorCard(message: String, onRetry: () -> Unit) {
    Panel {
        Text(stringResource(R.string.err_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(message, Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.err_retry)) }
    }
}

/** The honest status line: never says "blocking" unless the accessibility service is really connected. */
@Composable
private fun EnforcementCard(s: ManageState, onEnable: () -> Unit) {
    val (text, ok) = when (s.enforcement) {
        Enforcement.ENFORCING -> stringResource(R.string.enf_enforcing, s.blockedNow) to true
        Enforcement.READY -> stringResource(R.string.enf_ready) to true
        Enforcement.NOTHING_SELECTED -> stringResource(R.string.enf_nothing) to false
        Enforcement.UNAVAILABLE_NEEDS_ACCESSIBILITY -> stringResource(R.string.enf_unavailable) to false
        Enforcement.OFF_NEEDS_ACCESSIBILITY -> stringResource(R.string.enf_off) to false
    }
    val needsService = s.enforcement == Enforcement.UNAVAILABLE_NEEDS_ACCESSIBILITY || s.enforcement == Enforcement.OFF_NEEDS_ACCESSIBILITY
    Panel {
        Text(stringResource(if (ok) R.string.enf_title_on else R.string.enf_title_off), style = MaterialTheme.typography.titleMedium, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text(text, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (needsService && s.accessibilityEnabledInSettings) Text(stringResource(R.string.enf_enabled_not_running), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        if (needsService) Button(onClick = onEnable, Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.enf_enable)) }
    }
}

// ============================ groups ============================
private fun androidx.compose.foundation.lazy.LazyListScope.groupsSection(s: ManageState, a: ManageActions, onEditApps: (Long) -> Unit) {
    items(s.groups, key = { "g${it.id}" }) { g -> GroupCard(g, s.apps, a, onEditApps) }
    item { NewGroupButton(a) }
}

@Composable
private fun GroupCard(g: BlockGroup, apps: List<AppRef>, a: ManageActions, onEditApps: (Long) -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val members = apps.filter { it.pkg in g.apps }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.group_apps_count, g.apps.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = g.enabled, onCheckedChange = { a.setGroupEnabled(g.id, it) })
        }
        Text(stringResource(R.string.group_enabled_hint), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (members.isNotEmpty()) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            members.take(7).forEach { AppIcon(it.pkg, 32.dp) }
            if (members.size > 7) Text("+${members.size - 7}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(onClick = { onEditApps(g.id) }) { Text(stringResource(R.string.group_edit_apps)) }
            TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.group_rename)) }
            if (g.kind == GroupKind.CUSTOM) TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.group_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (renaming) NameDialog(stringResource(R.string.group_rename), g.name, onDone = { renaming = false; a.renameGroup(g.id, it) }, onCancel = { renaming = false })
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.group_delete_title)) },
        text = { Text(stringResource(R.string.group_delete_text, g.name)) },
        confirmButton = { TextButton(onClick = { deleting = false; a.deleteGroup(g.id) }) { Text(stringResource(R.string.group_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.dlg_cancel)) } },
    )
}

@Composable
private fun NewGroupButton(a: ManageActions) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.group_new)) }
    if (open) NameDialog(stringResource(R.string.group_new), "", onDone = { open = false; a.createGroup(it) }, onCancel = { open = false })
}

@Composable
private fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel, title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.group_name_hint)) }) },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.picker_save)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.dlg_cancel)) } },
    )
}

// ============================ app picker ============================
@Composable
private fun AppPicker(group: BlockGroup, apps: List<AppRef>, onSave: (Set<String>) -> Unit, onCancel: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Category?>(null) }
    var selected by remember(group.id) { mutableStateOf(group.apps) }
    val shown = remember(apps, query, cat) {
        apps.filter { (cat == null || it.category == cat) && (query.isBlank() || it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true)) }
    }
    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.picker_title, group.name), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true, label = { Text(stringResource(R.string.picker_search)) })
            LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(cat == null, { cat = null }, { Text(stringResource(R.string.picker_all)) }) }
                items(Category.entries.toList()) { c -> FilterChip(cat == c, { cat = c }, { Text(stringResource(categoryName(c))) }) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.picker_count, selected.size), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { selected = selected + shown.map { it.pkg } }) { Text(stringResource(R.string.picker_select_shown)) }
                TextButton(onClick = { selected = selected - shown.map { it.pkg }.toSet() }) { Text(stringResource(R.string.picker_clear_shown)) }
            }
        }
        Box(Modifier.weight(1f)) {
            if (apps.isEmpty()) Text(stringResource(R.string.picker_none_installed), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (shown.isEmpty()) Text(stringResource(R.string.picker_empty), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(shown, key = { it.pkg }) { app ->
                    val on = app.pkg in selected
                    Row(Modifier.fillMaxWidth().clickable { selected = if (on) selected - app.pkg else selected + app.pkg }.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.pkg, 42.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(stringResource(categoryName(app.category)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = on, onCheckedChange = { selected = if (it) selected + app.pkg else selected - app.pkg })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, Modifier.weight(1f)) { Text(stringResource(R.string.dlg_cancel)) }
            Button(onClick = { onSave(selected) }, Modifier.weight(1f)) { Text(stringResource(R.string.picker_save)) }
        }
    }
}

fun categoryName(c: Category): Int = when (c) {
    Category.SOCIAL -> R.string.cat_social; Category.ENTERTAINMENT -> R.string.cat_entertainment; Category.GAMES -> R.string.cat_games
    Category.BROWSERS -> R.string.cat_browsers; Category.COMMUNICATION -> R.string.cat_communication
    Category.PRODUCTIVITY -> R.string.cat_productivity; Category.OTHER -> R.string.cat_other
}

// ============================ schedules ============================
private fun androidx.compose.foundation.lazy.LazyListScope.schedulesSection(s: ManageState, a: ManageActions, onEdit: (Long) -> Unit) {
    if (s.rules.isEmpty()) item {
        Panel {
            Text(stringResource(R.string.sched_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.sched_empty_text), Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    items(s.rules, key = { "r${it.id}" }) { r -> ScheduleCard(r, s.groups, a, onEdit) }
    item { Button(onClick = { onEdit(0L) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.sched_add)) } }
}

@Composable
private fun daysText(mask: Int): String = when (ScheduleFormat.daysKind(mask)) {
    DaysKind.EVERY_DAY -> stringResource(R.string.sched_every_day)
    DaysKind.WEEKDAYS -> stringResource(R.string.sched_weekdays)
    DaysKind.WEEKEND -> stringResource(R.string.sched_weekend)
    DaysKind.CUSTOM -> ScheduleFormat.dayIndexes(mask).joinToString(" ") { DayOfWeek.of(it + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
private fun ScheduleCard(r: ScheduleRule, groups: List<BlockGroup>, a: ManageActions, onEdit: (Long) -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name.ifBlank { stringResource(R.string.sched_unnamed) }, style = MaterialTheme.typography.titleMedium)
                Text(daysText(r.daysMask) + " · " + ScheduleFormat.time(r.startMin) + " – " + ScheduleFormat.time(r.endMin) + if (ScheduleFormat.crossesMidnight(r)) " " + stringResource(R.string.sched_overnight) else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = r.enabled, onCheckedChange = { a.setRuleEnabled(r.id, it) })
        }
        val names = groups.filter { it.id in r.groupIds }.joinToString(", ") { it.name }
        Text(if (names.isBlank()) stringResource(R.string.sched_no_groups) else names, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (r.strict) Text(stringResource(R.string.sched_badge_strict), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            if (r.autoStart) Text(stringResource(R.string.sched_badge_auto), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.padding(top = 4.dp)) {
            TextButton(onClick = { onEdit(r.id) }) { Text(stringResource(R.string.sched_edit)) }
            TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.group_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.sched_delete_title)) }, text = { Text(stringResource(R.string.sched_delete_text, r.name)) },
        confirmButton = { TextButton(onClick = { deleting = false; a.deleteRule(r.id) }) { Text(stringResource(R.string.group_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.dlg_cancel)) } },
    )
}

@Composable
private fun ScheduleEditor(initial: ScheduleRule, state: ManageState, onSave: (ScheduleRule) -> Unit, onCancel: () -> Unit) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var mask by remember(initial.id) { mutableIntStateOf(initial.daysMask) }
    var start by remember(initial.id) { mutableIntStateOf(initial.startMin) }
    var end by remember(initial.id) { mutableIntStateOf(initial.endMin) }
    var strict by remember(initial.id) { mutableStateOf(initial.strict) }
    var auto by remember(initial.id) { mutableStateOf(initial.autoStart) }
    var groupIds by remember(initial.id) { mutableStateOf(initial.groupIds) }

    val candidate = initial.copy(name = name.trim(), daysMask = mask, startMin = start, endMin = end, strict = strict, autoStart = auto, groupIds = groupIds)
    val invalid = candidate.validate()
    val next = ScheduleEngine.upcoming(listOf(candidate.copy(enabled = true)), System.currentTimeMillis(), ZoneId.systemDefault(), 1).firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp, 28.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(if (initial.id == 0L) R.string.sched_add else R.string.sched_edit_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.sched_name)) })
        Panel {
            Text(stringResource(R.string.sched_days), style = MaterialTheme.typography.titleMedium)
            LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((0..6).toList()) { i -> FilterChip((mask shr i) and 1 == 1, { mask = ScheduleFormat.toggleDay(mask, i) }, { Text(DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())) }) }
            }
            TimeStepper(stringResource(R.string.sched_start), start) { start = it }
            TimeStepper(stringResource(R.string.sched_end), end) { end = it }
            if (invalid == null && ScheduleFormat.crossesMidnight(candidate)) Text(stringResource(R.string.sched_overnight_note), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            Text(stringResource(R.string.sched_groups), style = MaterialTheme.typography.titleMedium)
            if (state.groups.isEmpty()) Text(stringResource(R.string.sched_no_groups_exist), Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.groups, key = { it.id }) { g -> FilterChip(g.id in groupIds, { groupIds = if (g.id in groupIds) groupIds - g.id else groupIds + g.id }, { Text(g.name) }) }
            }
            if (groupIds.isEmpty()) Text(stringResource(R.string.sched_no_groups_warn), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Panel {
            ToggleRow(stringResource(R.string.sched_auto), stringResource(R.string.sched_auto_desc), auto) { auto = it }
            Spacer(Modifier.height(8.dp))
            ToggleRow(stringResource(R.string.sched_strict), stringResource(R.string.sched_strict_desc), strict) { strict = it }
            if (strict && state.enforcement != Enforcement.ENFORCING && state.enforcement != Enforcement.READY) Text(stringResource(R.string.sched_strict_no_a11y), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (invalid != null) Text(stringResource(R.string.sched_invalid, invalid), color = MaterialTheme.colorScheme.error)
        else next?.let { o ->
            val f = java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
            Text(stringResource(R.string.sched_next, java.time.Instant.ofEpochMilli(o.startWallMs).atZone(ZoneId.systemDefault()).format(f)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, Modifier.weight(1f)) { Text(stringResource(R.string.dlg_cancel)) }
            Button(onClick = { onSave(candidate) }, Modifier.weight(1f), enabled = invalid == null) { Text(stringResource(R.string.picker_save)) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/** Hour +/-1 and minute +/-5 steppers (no experimental time-picker API). Wraps around midnight. */
@Composable
private fun TimeStepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange((minutes - 60 + 1440) % 1440) }) { Text("−h") }
        TextButton(onClick = { onChange((minutes - 5 + 1440) % 1440) }) { Text("−5") }
        Text(ScheduleFormat.time(minutes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 6.dp))
        TextButton(onClick = { onChange((minutes + 5) % 1440) }) { Text("+5") }
        TextButton(onClick = { onChange((minutes + 60) % 1440) }) { Text("+h") }
    }
}
