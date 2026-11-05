package com.neelaxmi.focus.platform

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.neelaxmi.focus.core.Session
import com.neelaxmi.focus.core.SessionMachine
import com.neelaxmi.focus.core.SessionState

/**
 * Foreground service that keeps the process alive and the notification visible while a session is active.
 *
 * It holds NO timer state. The truth is the persisted session; this service only (a) keeps the process in the
 * foreground tier, (b) offers an in-process precise wake-up as a backup to the alarm, and (c) calls
 * controller.sync() on every (re)start so that a system restart (START_STICKY) re-arms everything.
 */
class FocusService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var loc: ServiceLocator

    private val listener: (Session?) -> Unit = { s -> handler.post { scheduleInProcessWake(s) } }
    private val wakeRunnable = Runnable { loc.io.execute { loc.controller.sync() } }

    override fun onCreate() {
        super.onCreate()
        running = true
        loc = applicationContext as ServiceLocator
        loc.controller.addListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val n = NotificationPresenter.placeholder(this)
            if (Build.VERSION.SDK_INT >= 34) startForeground(Notifications.ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(Notifications.ID_ONGOING, n)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused: ${e.javaClass.simpleName}", e)
            stopSelf(); return START_NOT_STICKY
        }
        loc.io.execute { loc.controller.sync() }
        return START_STICKY
    }

    private fun scheduleInProcessWake(s: Session?) {
        handler.removeCallbacks(wakeRunnable)
        if (s == null || s.state != SessionState.RUNNING) return
        val projected = SessionMachine.advance(s, SystemTicks.now()).session
        val remaining = SessionMachine.remainingMs(projected) ?: return
        handler.postDelayed(wakeRunnable, remaining + 50)
    }

    override fun onDestroy() {
        handler.removeCallbacks(wakeRunnable)
        loc.controller.removeListener(listener)
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.neelaxmi.focus.action.START"
        private const val TAG = "FocusService"
        @Volatile var running = false
    }
}
