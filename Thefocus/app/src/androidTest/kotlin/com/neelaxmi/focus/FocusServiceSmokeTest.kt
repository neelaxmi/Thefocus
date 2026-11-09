package com.neelaxmi.focus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neelaxmi.focus.core.Mode
import com.neelaxmi.focus.core.Outcome
import com.neelaxmi.focus.core.StartConfig
import com.neelaxmi.focus.platform.FocusService
import com.neelaxmi.focus.platform.ServiceLocator
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** End-to-end on the real app object: starting a session starts the foreground service; stopping it stops the service. */
@RunWith(AndroidJUnit4::class)
class FocusServiceSmokeTest {
    private val loc = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ServiceLocator

    private fun waitFor(timeoutMs: Long = 8_000, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(100) }
        return cond()
    }
    private fun onIo(block: () -> Unit) { loc.io.submit(block).get(5, TimeUnit.SECONDS) }

    @After fun end() { onIo { loc.controller.stop(emergency = false) } }

    @Test fun startingASessionStartsTheForegroundServiceAndStoppingItStopsTheService() {
        onIo { loc.controller.stop() }                                  // clean slate if a previous run left a session
        var started: Outcome? = null
        onIo { started = loc.controller.start(StartConfig(Mode.STOPWATCH)) }
        assertTrue("start must apply: $started", started is Outcome.Applied)
        assertTrue("FocusService should be running", waitFor { FocusService.running })
        onIo { loc.controller.stop() }
        assertTrue("FocusService should stop", waitFor { !FocusService.running })
        assertFalse(FocusService.running)
    }
}
