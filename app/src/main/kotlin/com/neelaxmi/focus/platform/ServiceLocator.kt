package com.neelaxmi.focus.platform

import com.neelaxmi.focus.core.SessionController
import com.neelaxmi.focus.core.SessionStore
import java.util.concurrent.ExecutorService

interface ServiceLocator {
    val controller: SessionController
    val store: SessionStore
    val settings: Settings
    val io: ExecutorService
}
