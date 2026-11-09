package com.neelaxmi.focus.core

import java.time.ZoneId

/** Result of combining the active session, schedules and groups into what must be enforced RIGHT NOW. */
data class Assembled(
    val config: PolicyConfig,
    /** Re-assemble no later than this instant (next schedule start/end); null when no schedule exists. */
    val validUntilWallMs: Long?,
    val blockedApps: Set<String>,
    val demanded: Boolean,
    val strictActive: Boolean,
) {
    /**
     * While a strict session or strict schedule window is active, blocking configuration (groups, their apps, schedules) cannot be
     * edited: otherwise the user could simply open this app and remove the blocked apps, which would make strict mode meaningless.
     */
    val configLocked: Boolean get() = strictActive
}

object PolicyAssembler {
    fun assemble(
        nowWall: Long, zone: ZoneId, session: Session?, groups: List<BlockGroup>, rules: List<ScheduleRule>, alwaysAllowed: Set<String>,
    ): Assembled {
        val sessionApps = groups.filter { it.enabled }.flatMap { it.apps }.toSet()
        val focus = session?.let { PolicyEngine.focusPolicyFor(it, ListMode.BLOCKLIST, sessionApps) }

        val activeRules = rules.filter { it.enabled && ScheduleEngine.isActive(it, nowWall, zone) }
        val windows = activeRules.map { r ->
            ScheduleWindow(active = true, strict = r.strict, mode = ListMode.BLOCKLIST, apps = groups.filter { it.id in r.groupIds }.flatMap { it.apps }.toSet())
        }
        val blocked = (focus?.apps ?: emptySet()) + windows.flatMap { it.apps }
        return Assembled(
            config = PolicyConfig(alwaysAllowed = alwaysAllowed, focus = focus, schedules = windows),
            validUntilWallMs = ScheduleEngine.nextBoundary(rules, nowWall, zone),
            blockedApps = blocked,
            demanded = focus != null || windows.isNotEmpty(),
            strictActive = (focus?.strict == true) || windows.any { it.strict },
        )
    }
}

/** The honest answer to "is blocking actually working?" - never claims enforcement without the Accessibility service. */
enum class Enforcement { ENFORCING, READY, NOTHING_SELECTED, UNAVAILABLE_NEEDS_ACCESSIBILITY, OFF_NEEDS_ACCESSIBILITY }

object EnforcementStatus {
    fun of(accessibilityConnected: Boolean, demanded: Boolean, blockedCount: Int): Enforcement = when {
        !accessibilityConnected && demanded -> Enforcement.UNAVAILABLE_NEEDS_ACCESSIBILITY
        !accessibilityConnected -> Enforcement.OFF_NEEDS_ACCESSIBILITY
        demanded && blockedCount == 0 -> Enforcement.NOTHING_SELECTED
        demanded -> Enforcement.ENFORCING
        else -> Enforcement.READY
    }
}

enum class ProtectedKind { OWN_APP_INFO, ACCESSIBILITY_PAGE, DEVICE_ADMIN, UNINSTALL_DIALOG }
data class WindowInfo(val pkg: String, val className: String?, val texts: List<String>)

/**
 * Recognises the system screens through which a user could switch protection off (this app's info/uninstall/accessibility
 * pages). Only consulted while a strict session/schedule is active. This is a SPEED BUMP, not a lock: vendor settings apps,
 * ADB, safe mode and other routes are not covered (see KNOWN_LIMITATIONS).
 */
object SettingsGuard {
    val settingsPackages = setOf("com.android.settings", "com.google.android.settings", "com.android.permissioncontroller")
    val installerPackages = setOf("com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.permissioncontroller")

    fun isCandidatePackage(pkg: String) = pkg in settingsPackages || pkg in installerPackages

    fun classify(w: WindowInfo, ownLabel: String): ProtectedKind? {
        val cls = w.className?.lowercase() ?: ""
        val mentionsUs = w.texts.any { it.contains(ownLabel, ignoreCase = true) }
        if (!mentionsUs) return null
        return when {
            w.pkg in installerPackages && "uninstall" in cls -> ProtectedKind.UNINSTALL_DIALOG
            w.pkg in settingsPackages && "deviceadmin" in cls -> ProtectedKind.DEVICE_ADMIN
            w.pkg in settingsPackages && "accessib" in cls -> ProtectedKind.ACCESSIBILITY_PAGE
            w.pkg in settingsPackages && ("installedappdetails" in cls || "appinfo" in cls || "applicationdetails" in cls) -> ProtectedKind.OWN_APP_INFO
            else -> null
        }
    }
}
