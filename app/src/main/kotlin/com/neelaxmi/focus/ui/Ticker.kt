package com.neelaxmi.focus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import com.neelaxmi.focus.core.Tick
import com.neelaxmi.focus.platform.SystemTicks
import kotlinx.coroutines.delay

@Composable
fun rememberSecondTick(enabled: Boolean): State<Tick> {
    val tick = remember {
        mutableStateOf(SystemTicks.now())
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            tick.value = SystemTicks.now()
            return@LaunchedEffect
        }

        while (true) {
            tick.value = SystemTicks.now()
            delay(1000)
        }
    }

    return tick
}
