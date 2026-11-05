package com.neelaxmi.focus.platform

import android.os.SystemClock
import com.neelaxmi.focus.core.Tick
import com.neelaxmi.focus.core.TickSource

object SystemTicks : TickSource {
    override fun now() = Tick(System.currentTimeMillis(), SystemClock.elapsedRealtime())
}
