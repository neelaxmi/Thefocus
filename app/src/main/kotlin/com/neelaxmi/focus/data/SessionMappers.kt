package com.neelaxmi.focus.data

import com.neelaxmi.focus.core.BreakRules
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.Phase
import com.neelaxmi.focus.core.PomodoroConfig
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.SessionLog
import com.neelaxmi.focus.core.SessionState
import com.neelaxmi.focus.core.StrictRules
import com.neelaxmi.focus.core.Tick

fun Session.toEntity() = SessionEntity(
    id = id, mode = mode.name, state = state.name, phase = phase.name, plannedMs = plannedMs,
    pomoFocusMs = pomodoro?.focusMs, pomoShortMs = pomodoro?.shortBreakMs, pomoLongMs = pomodoro?.longBreakMs,
    pomoRounds = pomodoro?.roundsPerCycle, pomoCycles = pomodoro?.cycles,
    breaksAllowed = breakRules.allowed, breakDurationMs = breakRules.durationMs,
    strictCooldownMs = strict?.endCooldownMs, strictEmergencyLeft = strict?.emergencyExitsLeft,
    startedAtWall = startedAtWallMs, endedAtWall = endedAtWallMs, focusMs = focusMs, phaseElapsedMs = phaseElapsedMs,
    round = round, cycle = cycle, breaksTaken = breaksTaken, tickWall = lastTick.wallMs, tickMono = lastTick.monoMs,
    endReqWall = endRequestedTick?.wallMs, endReqMono = endRequestedTick?.monoMs,
    clockAdjusted = clockAdjusted, rebootCount = rebootCount, tagId = tagId,
)
fun SessionEntity.toSession() = Session(
    id = id, mode = Mode.valueOf(mode), state = SessionState.valueOf(state), phase = Phase.valueOf(phase),
    plannedMs = plannedMs,
    pomodoro = if (pomoFocusMs != null) PomodoroConfig(pomoFocusMs, pomoShortMs!!, pomoLongMs!!, pomoRounds!!, pomoCycles!!) else null,
    breakRules = BreakRules(breaksAllowed, breakDurationMs),
    strict = if (strictCooldownMs != null) StrictRules(strictCooldownMs, strictEmergencyLeft ?: 0) else null,
    startedAtWallMs = startedAtWall, endedAtWallMs = endedAtWall, focusMs = focusMs, phaseElapsedMs = phaseElapsedMs,
    round = round, cycle = cycle, breaksTaken = breaksTaken, lastTick = Tick(tickWall, tickMono),
    endRequestedTick = if (endReqWall != null && endReqMono != null) Tick(endReqWall, endReqMono) else null,
    clockAdjusted = clockAdjusted, rebootCount = rebootCount, tagId = tagId,
)
fun SessionLog.toEntity(sessionId: Long) = SessionLogEntity(sessionId = sessionId, atWall = atWallMs, kind = kind.name, phase = phase?.name)
