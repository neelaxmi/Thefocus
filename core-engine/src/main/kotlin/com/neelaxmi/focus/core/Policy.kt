package com.neelaxmi.focus.core

enum class Section { NONE, REELS, SHORTS, SPOTLIGHT }
enum class ListMode { ALLOWLIST, BLOCKLIST }
enum class Scope { ALWAYS, DURING_FOCUS }

enum class BlockReason {
    SECTION_BLOCKED, WEBSITE_BLOCKED, WEBSITE_NOT_ALLOWED,
    SESSION_APP_NOT_ALLOWED, SESSION_APP_BLOCKED, SCHEDULE_BLOCKED, LIMIT_REACHED,
}

sealed interface Decision {
    data object Allow : Decision
    data class Block(
        val reason: BlockReason,
        val canSnooze: Boolean,
        val canEmergency: Boolean,
    ) : Decision
}

/** What detectors report. Detectors never decide; they only supply facts. */
data class Observation(
    val pkg: String,
    val section: Section = Section.NONE,
    val domain: String? = null,
    val nowWallMs: Long,
)

data class FocusPolicy(val strict: Boolean, val mode: ListMode, val apps: Set<String>, val emergencyExitsLeft: Int = 0)
data class ScheduleWindow(val active: Boolean, val strict: Boolean, val mode: ListMode, val apps: Set<String>)
data class SectionRule(val pkg: String, val section: Section, val scope: Scope)
data class DomainRules(
    val blocked: Set<String> = emptySet(),
    /** If non-null, during focus only these domains (and subdomains) are allowed (study browsing). */
    val allowedOnlyDuringFocus: Set<String>? = null,
    val blockScope: Scope = Scope.ALWAYS,
)
data class AppLimitState(val limitMs: Long, val usedTodayMs: Long, val snoozedUntilWallMs: Long? = null, val strict: Boolean = false)

data class PolicyConfig(
    val alwaysAllowed: Set<String> = emptySet(),
    val focus: FocusPolicy? = null,
    val schedules: List<ScheduleWindow> = emptyList(),
    val sectionRules: List<SectionRule> = emptyList(),
    val domainRules: DomainRules = DomainRules(),
    val limits: Map<String, AppLimitState> = emptyMap(),
)

object PolicyEngine {

    /** Builds the active-focus policy from the persisted session; null when nothing should be enforced right now. */
    fun focusPolicyFor(s: Session, mode: ListMode, apps: Set<String>): FocusPolicy? {
        val enforcing = s.state == SessionState.RUNNING && s.phase == Phase.FOCUS
        if (!enforcing) return null
        return FocusPolicy(strict = s.strict != null, mode = mode, apps = apps, emergencyExitsLeft = s.strict?.emergencyExitsLeft ?: 0)
    }

    fun normalizeDomain(raw: String): String {
        var d = raw.trim().lowercase()
        d = d.removePrefix("https://").removePrefix("http://")
        d = d.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        return d.removePrefix("www.").trimEnd('.')
    }

    fun domainMatches(domain: String, rule: String): Boolean {
        val d = normalizeDomain(domain); val r = normalizeDomain(rule)
        return r.isNotEmpty() && (d == r || d.endsWith(".$r"))
    }

    fun decide(obs: Observation, cfg: PolicyConfig): Decision {
        if (obs.pkg in cfg.alwaysAllowed) return Decision.Allow

        val focus = cfg.focus
        val activeSchedules = cfg.schedules.filter { it.active }
        val inFocus = focus != null
        val anyStrict = (focus?.strict == true) || activeSchedules.any { it.strict }
        val emergency = focus?.strict == true && focus.emergencyExitsLeft > 0

        fun block(r: BlockReason, snoozable: Boolean = !anyStrict) = Decision.Block(r, canSnooze = snoozable, canEmergency = emergency)
        val enforcingNow = inFocus || activeSchedules.isNotEmpty()

        // 1. In-app section (Reels / Shorts / Spotlight)
        if (obs.section != Section.NONE) {
            val hit = cfg.sectionRules.any {
                it.pkg == obs.pkg && it.section == obs.section && (it.scope == Scope.ALWAYS || enforcingNow)
            }
            if (hit) return block(BlockReason.SECTION_BLOCKED)
        }

        // 2. Websites
        obs.domain?.let { dom ->
            val rules = cfg.domainRules
            val blockActive = rules.blockScope == Scope.ALWAYS || enforcingNow
            if (blockActive && rules.blocked.any { domainMatches(dom, it) }) return block(BlockReason.WEBSITE_BLOCKED)
            val only = rules.allowedOnlyDuringFocus
            if (inFocus && only != null && only.none { domainMatches(dom, it) }) return block(BlockReason.WEBSITE_NOT_ALLOWED)
        }

        // 3. Focus session app list
        if (focus != null) {
            val denied = when (focus.mode) {
                ListMode.ALLOWLIST -> obs.pkg !in focus.apps
                ListMode.BLOCKLIST -> obs.pkg in focus.apps
            }
            if (denied) return block(
                if (focus.mode == ListMode.ALLOWLIST) BlockReason.SESSION_APP_NOT_ALLOWED else BlockReason.SESSION_APP_BLOCKED,
            )
        }

        // 4. Active schedules
        for (sch in activeSchedules) {
            val denied = when (sch.mode) {
                ListMode.ALLOWLIST -> obs.pkg !in sch.apps
                ListMode.BLOCKLIST -> obs.pkg in sch.apps
            }
            if (denied) return block(BlockReason.SCHEDULE_BLOCKED)
        }

        // 5. Daily limits
        cfg.limits[obs.pkg]?.let { lim ->
            val snoozed = lim.snoozedUntilWallMs?.let { it > obs.nowWallMs } == true
            if (lim.usedTodayMs >= lim.limitMs && !snoozed) {
                return Decision.Block(BlockReason.LIMIT_REACHED, canSnooze = !lim.strict && !anyStrict, canEmergency = emergency)
            }
        }
        return Decision.Allow
    }
}
