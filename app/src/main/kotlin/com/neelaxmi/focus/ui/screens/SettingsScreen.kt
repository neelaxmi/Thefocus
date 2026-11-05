package com.neelaxmi.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neelaxmi.focus.R
import com.neelaxmi.focus.core.TimeFormat
import com.neelaxmi.focus.platform.PermissionHelper
import com.neelaxmi.focus.platform.Settings
import com.neelaxmi.focus.ui.Panel

@Composable
fun SettingsScreen(settings: Settings, resumeKey: Int, onGoalChanged: () -> Unit, requestNotifications: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 28.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Panel {
            var goal by remember { mutableIntStateOf(settings.dailyGoalMin) }
            Text(stringResource(R.string.set_goal), style = MaterialTheme.typography.titleMedium)
            Text(TimeFormat.minutes(goal), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
            Slider(value = goal.toFloat(), onValueChange = { goal = (Math.round(it / 10f) * 10).coerceIn(10, 720) },
                onValueChangeFinished = { settings.dailyGoalMin = goal; onGoalChanged() }, valueRange = 10f..720f)
        }

        Panel {
            Text(stringResource(R.string.set_pomodoro), style = MaterialTheme.typography.titleMedium)
            Stepper(stringResource(R.string.set_pomo_focus), settings.pomoFocusMin, 5, 5..180) { settings.pomoFocusMin = it }
            Stepper(stringResource(R.string.set_pomo_short), settings.pomoShortMin, 1, 1..60) { settings.pomoShortMin = it }
            Stepper(stringResource(R.string.set_pomo_long), settings.pomoLongMin, 5, 1..120) { settings.pomoLongMin = it }
            Stepper(stringResource(R.string.set_pomo_rounds), settings.pomoRounds, 1, 1..12) { settings.pomoRounds = it }
            Stepper(stringResource(R.string.set_pomo_cycles), settings.pomoCycles, 1, 1..12) { settings.pomoCycles = it }
        }

        Panel {
            Text(stringResource(R.string.set_breaks), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.set_breaks_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stepper(stringResource(R.string.set_breaks_allowed), settings.breaksAllowed, 1, 0..10) { settings.breaksAllowed = it }
            Stepper(stringResource(R.string.set_break_minutes), settings.breakMin, 1, 1..60) { settings.breakMin = it }
        }

        Panel {
            Text(stringResource(R.string.set_strict), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.set_strict_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stepper(stringResource(R.string.set_strict_cooldown), settings.strictCooldownSec, 30, 10..900) { settings.strictCooldownSec = it }
            Stepper(stringResource(R.string.set_strict_emergency), settings.emergencyExitsPerDay, 1, 0..5) { settings.emergencyExitsPerDay = it }
        }

        Panel {
            Text(stringResource(R.string.rel_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.rel_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            key(resumeKey) {
                PermRow(R.string.rel_notifications, PermissionHelper.notificationsGranted(ctx)) {
                    if (PermissionHelper.needsNotificationRuntimePermission()) requestNotifications() else ctx.startActivity(PermissionHelper.notificationSettings(ctx))
                }
                PermRow(R.string.rel_exact_alarms, PermissionHelper.exactAlarmsAllowed(ctx)) { ctx.startActivity(PermissionHelper.exactAlarmSettings(ctx)) }
                PermRow(R.string.rel_battery, PermissionHelper.batteryUnrestricted(ctx)) { ctx.startActivity(PermissionHelper.batterySettings()) }
            }
            TextButton(onClick = { ctx.startActivity(PermissionHelper.appDetails(ctx)) }) { Text(stringResource(R.string.rel_app_info)) }
            Text(stringResource(R.string.rel_oem_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Panel {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_text), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PermRow(label: Int, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (ok) R.string.rel_ok else R.string.rel_needs_attention), color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        if (!ok) FilledTonalButton(onClick = onFix) { Text(stringResource(R.string.rel_fix)) }
    }
}

@Composable
private fun Stepper(label: String, value: Int, step: Int, range: IntRange, onChange: (Int) -> Unit) {
    var v by remember { mutableIntStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { v = (v - step).coerceIn(range); onChange(v) }, enabled = v > range.first) { Text("−") }
        Text("$v", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        TextButton(onClick = { v = (v + step).coerceIn(range); onChange(v) }, enabled = v < range.last) { Text("+") }
    }
}
