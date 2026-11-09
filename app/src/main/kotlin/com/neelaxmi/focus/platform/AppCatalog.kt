package com.neelaxmi.focus.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.neelaxmi.focus.core.AppCategorizer
import com.neelaxmi.focus.core.AppRef
import com.neelaxmi.focus.core.PlatformCategory

/**
 * Launchable apps, found through the narrow <queries> declaration in the manifest (no QUERY_ALL_PACKAGES permission).
 * Apps without a launcher icon (pure services) are intentionally not listed: they cannot be "opened", so they cannot be blocked.
 */
class AppCatalog(private val ctx: Context) {
    private val pm get() = ctx.packageManager

    @Suppress("DEPRECATION")
    fun installed(): List<AppRef> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (ai.packageName == ctx.packageName) return@mapNotNull null
            AppRef(ai.packageName, ri.loadLabel(pm).toString(), AppCategorizer.categorize(ai.packageName, hint(ai.category)))
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    fun label(pkg: String): String = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    fun icon(pkg: String): Drawable? = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()

    private fun hint(c: Int) = when (c) {
        ApplicationInfo.CATEGORY_GAME -> PlatformCategory.GAME
        ApplicationInfo.CATEGORY_VIDEO -> PlatformCategory.VIDEO
        ApplicationInfo.CATEGORY_AUDIO -> PlatformCategory.AUDIO
        ApplicationInfo.CATEGORY_SOCIAL -> PlatformCategory.SOCIAL
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> PlatformCategory.PRODUCTIVITY
        ApplicationInfo.CATEGORY_NEWS -> PlatformCategory.NEWS
        ApplicationInfo.CATEGORY_MAPS -> PlatformCategory.MAPS
        ApplicationInfo.CATEGORY_IMAGE -> PlatformCategory.IMAGE
        else -> PlatformCategory.UNDEFINED
    }
}

/**
 * Packages that must never be blocked (the user could not recover): this app, system UI, the home launcher(s), the default
 * dialer / emergency apps and the enabled keyboards. [neutral] windows neither block nor dismiss an active block screen.
 * Settings is NOT here: while strict protection is on it is partially guarded by SettingsGuard.
 */
class AlwaysAllowed(private val ctx: Context) {
    @Volatile private var cache: Set<String> = emptySet()
    @Volatile private var neutralCache: Set<String> = emptySet()
    @Volatile private var at = 0L

    fun get(): Set<String> { refreshIfNeeded(); return cache }
    /** System UI + keyboards: their windows appear on top of the blocked app and must not dismiss the block screen. */
    fun neutral(): Set<String> { refreshIfNeeded(); return neutralCache }

    @Suppress("DEPRECATION")
    private fun refreshIfNeeded() {
        val now = SystemClock.elapsedRealtime()
        if (cache.isNotEmpty() && now - at < 10 * 60_000L) return
        val neutral = mutableSetOf("com.android.systemui", "android")
        runCatching { ctx.service<InputMethodManager>().enabledInputMethodList.forEach { neutral += it.packageName } }
        val all = mutableSetOf<String>()
        all += listOf(ctx.packageName, "com.android.phone", "com.android.emergency", "com.android.server.telecom",
            "com.google.android.dialer", "com.samsung.android.dialer")
        all += neutral
        runCatching {
            ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .forEach { all += it.activityInfo.packageName }
        }
        runCatching { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { all += it } }
        neutralCache = neutral; cache = all; at = now
    }
}
