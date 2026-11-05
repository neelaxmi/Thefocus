package com.neelaxmi.focus

import android.app.Application
import androidx.room.Room
import com.neelaxmi.focus.core.SessionController
import com.neelaxmi.focus.core.SessionStore
import com.neelaxmi.focus.data.FocusDatabase
import com.neelaxmi.focus.data.RoomSessionStore
import com.neelaxmi.focus.platform.AlarmWakeScheduler
import com.neelaxmi.focus.platform.NotificationPresenter
import com.neelaxmi.focus.platform.Notifications
import com.neelaxmi.focus.platform.ServiceLocator
import com.neelaxmi.focus.platform.Settings
import com.neelaxmi.focus.platform.SystemTicks
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FocusApp : Application(), ServiceLocator {
    override val io: ExecutorService by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "focus-io") } }
    private val db: FocusDatabase by lazy { Room.databaseBuilder(this, FocusDatabase::class.java, "focus.db").build() }
    override val store: SessionStore by lazy { RoomSessionStore(db.sessions()) }
    override val settings: Settings by lazy { Settings(this) }
    override val controller: SessionController by lazy {
        SessionController(store, SystemTicks, AlarmWakeScheduler(this), NotificationPresenter(this))
    }
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
