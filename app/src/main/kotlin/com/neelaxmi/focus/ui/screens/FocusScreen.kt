package com.neelaxmi.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neelaxmi.focus.R
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.Phase
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.SessionMachine
import com.neelaxmi.focus.core.SessionState
import com.neelaxmi.focus.core.TimeFormat
import com.neelaxmi.focus.core.Ticks
import com.neelaxmi.focus.platform.Settings
import com.neelaxmi.focus.ui.Panel
import com.neelaxmi.focus.ui.ProgressRing
import com.neelaxmi.focus.ui.rememberSecondTick
import com.neelaxmi.focus.ui.theme.Palette
import com.neelaxmi.focus.ui.theme.TimerStyle

class FocusActions(
    val start: (Mode, Int) -> Unit, val pause: () -> Unit, val resume: () -> Unit,
    val startBreak: () -> Unit, val endBreakEarly: () -> Unit, val requestEnd: () -> Unit,
    val cancelEnd: () -> Unit, val stop: (Boolean) -> Unit, val syncNow: () -> Unit,
    val ensureNotifications: () -> Unit,
)
@Composable
fun FocusScreen(active: Session?, settings: Settings, actions: FocusActions) {
    if (active == null) SetupView(settings, actions) else RunningView(active, actions)
}
@Composable
private fun SetupView(settings: Settings, actions: FocusActions) {
    var mode by remember { mutableStateOf(settings.lastMode) }
    var timerMin by remember { mutableIntStateOf(settings.timerMin) }
    var strict by remember { mutableStateOf(settings.strictEnabled) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 28.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(stringResource(modeLabel(m))) }) }
        }
        Panel {
            when (mode) {
                Mode.TIMER -> {
                    Text(TimeFormat.minutes(timerMin), style = MaterialTheme.typography.headlineMedium)
                    Slider(value = timerMin.toFloat(), onValueChange = { timerMin = (Math.round(it / 5f) * 5).coerceIn(5, 180) }, valueRange = 5f..180f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 25, 45, 60, 90).forEach { q -> FilterChip(selected = timerMin == q, onClick = { timerMin = q }, label = { Text("$q") }) } }
                }
                Mode.STOPWATCH -> Text(stringResource(R.string.mode_stopwatch_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Mode.POMODORO -> {
                    Text(stringResource(R.string.pomo_summary, settings.pomoFocusMin, settings.pomoShortMin, settings.pomoLongMin, settings.pomoRounds, settings.pomoCycles), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.pomo_edit_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.strict_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.strict_desc, settings.strictCooldownSec / 60.0, settings.emergencyExitsPerDay), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Switch(checked = strict, onCheckedChange = { strict = it })
            }
        }
        Button(onClick = { settings.lastMode = mode; settings.timerMin = timerMin; settings.strictEnabled = strict; actions.ensureNotifications(); actions.start(mode, timerMin) },
            modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.start_focus), style = MaterialTheme.typography.titleMedium) }
    }
}
private fun modeLabel(m: Mode) = when (m) { Mode.TIMER -> R.string.mode_timer; Mode.STOPWATCH -> R.string.mode_stopwatch; Mode.POMODORO -> R.string.mode_pomodoro }
@Composable
private fun RunningView(session: Session, a: FocusActions) {
    val tick by rememberSecondTick(session.state == SessionState.RUNNING)
    val p = SessionMachine.advance(session, tick).session
    LaunchedEffect(p.isFinished) { if (p.isFinished) a.syncNow() }
    val remaining = SessionMachine.remainingMs(p)
    val len = SessionMachine.phaseLengthMs(p)
    val progress = when {
        p.mode == Mode.TIMER && p.phase == Phase.FOCUS -> p.focusTotalMs.toFloat() / p.plannedMs!!
        len != null -> p.phaseElapsedMs.toFloat() / len
        else -> (p.focusTotalMs % 3_600_000L).toFloat() / 3_600_000L
    }
    val onBreak = p.phase != Phase.FOCUS
    val paused = p.state == SessionState.PAUSED
    var confirmStop by remember { mutableStateOf(false) }
    var confirmEmergency by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 36.dp, 20.dp, 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(when { paused -> R.string.phase_paused; p.phase == Phase.SHORT_BREAK -> R.string.phase_short_break; p.phase == Phase.LONG_BREAK -> R.string.phase_long_break; p.phase == Phase.USER_BREAK -> R.string.phase_break; else -> R.string.phase_focus }),
            style = MaterialTheme.typography.titleLarge, color = if (onBreak) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
        ProgressRing(progress, size = 290.dp, stroke = 18.dp, colors = if (onBreak) listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary) else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(TimeFormat.clock(remaining ?: p.focusTotalMs), style = TimerStyle)
                if (p.mode == Mode.POMODORO) Text(stringResource(R.string.pomo_round, p.round, p.pomodoro!!.roundsPerCycle, p.cycle, p.pomodoro!!.cycles), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (p.mode == Mode.TIMER) Text(stringResource(R.string.focused_so_far, TimeFormat.clock(p.focusTotalMs)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val strict = p.strict
        if (strict == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (paused) FilledTonalButton(onClick = a.resume) { Text(stringResource(R.string.action_resume)) }
                else if (p.phase == Phase.FOCUS) FilledTonalButton(onClick = a.pause) { Text(stringResource(R.string.action_pause)) }
                if (p.phase == Phase.USER_BREAK) FilledTonalButton(onClick = a.endBreakEarly) { Text(stringResource(R.string.action_skip_break)) }
                if (p.phase == Phase.FOCUS && !paused && p.mode != Mode.POMODORO && p.breaksTaken < p.breakRules.allowed) FilledTonalButton(onClick = a.startBreak) { Text(stringResource(R.string.action_take_break, p.breakRules.allowed - p.breaksTaken)) }
                OutlinedButton(onClick = { confirmStop = true }) { Text(stringResource(if (p.mode == Mode.STOPWATCH) R.string.action_finish else R.string.action_end)) }
            }
        } else {
            Panel {
                Text(stringResource(R.string.strict_active), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.strict_active_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                val req = p.endRequestedTick
                if (req == null) OutlinedButton(onClick = a.requestEnd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.strict_request_end)) }
                else {
                    val left = strict.endCooldownMs - Ticks.delta(req, tick).ms
                    if (left > 0) {
                        Text(stringResource(R.string.strict_end_in, TimeFormat.clock(left)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = a.cancelEnd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.strict_keep_going)) }
                    } else {
                        Button(onClick = { a.stop(false) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger)) { Text(stringResource(R.string.strict_end_now)) }
                        TextButton(onClick = a.cancelEnd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.strict_keep_going)) }
                    }
                }
                if (strict.emergencyExitsLeft > 0) TextButton(onClick = { confirmEmergency = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.strict_emergency, strict.emergencyExitsLeft), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmStop) AlertDialog(onDismissRequest = { confirmStop = false },
        title = { Text(stringResource(if (p.mode == Mode.STOPWATCH) R.string.dlg_finish_title else R.string.dlg_end_title)) },
        text = { Text(stringResource(if (p.mode == Mode.STOPWATCH) R.string.dlg_finish_text else R.string.dlg_end_text, TimeFormat.minutes((p.focusTotalMs / 60_000L).toInt()))) },
        confirmButton = { TextButton(onClick = { confirmStop = false; a.stop(false) }) { Text(stringResource(R.string.dlg_yes)) } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.dlg_cancel)) } })
    if (confirmEmergency) AlertDialog(onDismissRequest = { confirmEmergency = false },
        title = { Text(stringResource(R.string.dlg_emergency_title)) }, text = { Text(stringResource(R.string.dlg_emergency_text)) },
        confirmButton = { TextButton(onClick = { confirmEmergency = false; a.stop(true) }) { Text(stringResource(R.string.dlg_emergency_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmEmergency = false }) { Text(stringResource(R.string.dlg_cancel)) } })
}
