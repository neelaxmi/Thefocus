package com.neelaxmi.focus.platform

import android.content.Context

/** Typed system-service lookup that fails with a clear message instead of an NPE if the service is missing. */
internal inline fun <reified T : Any> Context.service(): T =
    getSystemService(T::class.java) ?: error("System service ${T::class.java.simpleName} unavailable")
