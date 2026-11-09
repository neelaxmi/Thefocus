package com.neelaxmi.focus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.neelaxmi.focus.core.Tick
import com.neelaxmi.focus.platform.SystemTicks
import kotlinx.coroutines.delay

/**
 * Emits a fresh clock reading once per second while [enabled] and composed. This only drives DISPLAY; the session
 * itself is advanced by the controller (alarm / service / sync), so a stalled UI never affects correctness.
 */
@Composable
fun rememberSecondTick(enabled: Boolean): State<Tick> {
    val tick = remember { mutableStateOf(SystemTicks.now()) }
    LaunchedEffect(enabled) {
        tick.value = SystemTicks.now()
        while (enabled) {
            delay(1000)
            tick.value = SystemTicks.now()
        }
    }
    return tick
}
