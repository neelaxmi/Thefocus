package com.neelaxmi.focus.core

import com.neelaxmi.focus.core.Harness.eq
import com.neelaxmi.focus.core.Harness.test
import com.neelaxmi.focus.core.Harness.yes
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val NOW = 1_800_000_000_000L
private fun obs(pkg: String, section: Section = Section.NONE, domain: String? = null) = Observation(pkg, section, domain, NOW)
private fun blocked(d: Decision) = d as? Decision.Block ?: throw AssertionError("expected Block, got $d")

fun policyTests() {
    val own = "com.neelaxmi.focus"
    test("always-allowed apps are never blocked") {
        val cfg = PolicyConfig(alwaysAllowed = setOf(own, "com.android.dialer"), focus = FocusPolicy(true, ListMode.ALLOWLIST, emptySet()))
        eq(Decision.Allow, PolicyEngine.decide(obs(own), cfg)); eq(Decision.Allow, PolicyEngine.decide(obs("com.android.dialer"), cfg))
    }
    test("focus allowlist blocks everything else; blocklist blocks only listed") {
        val allow = PolicyConfig(focus = FocusPolicy(false, ListMode.ALLOWLIST, setOf("com.notes")))
        eq(Decision.Allow, PolicyEngine.decide(obs("com.notes"), allow))
        eq(BlockReason.SESSION_APP_NOT_ALLOWED, blocked(PolicyEngine.decide(obs("com.insta"), allow)).reason)
        val bl = PolicyConfig(focus = FocusPolicy(false, ListMode.BLOCKLIST, setOf("com.insta")))
        eq(Decision.Allow, PolicyEngine.decide(obs("com.notes"), bl))
        eq(BlockReason.SESSION_APP_BLOCKED, blocked(PolicyEngine.decide(obs("com.insta"), bl)).reason)
    }
    test("no focus session and no rules = allow") { eq(Decision.Allow, PolicyEngine.decide(obs("com.insta"), PolicyConfig())) }
    test("section rule: ALWAYS vs DURING_FOCUS") {
        val rules = listOf(SectionRule("com.yt", Section.SHORTS, Scope.ALWAYS), SectionRule("com.insta", Section.REELS, Scope.DURING_FOCUS))
        val idle = PolicyConfig(sectionRules = rules)
        eq(BlockReason.SECTION_BLOCKED, blocked(PolicyEngine.decide(obs("com.yt", Section.SHORTS), idle)).reason)
        eq(Decision.Allow, PolicyEngine.decide(obs("com.insta", Section.REELS), idle))
        eq(Decision.Allow, PolicyEngine.decide(obs("com.yt", Section.NONE), idle))
        val focusing = idle.copy(focus = FocusPolicy(false, ListMode.BLOCKLIST, emptySet()))
        eq(BlockReason.SECTION_BLOCKED, blocked(PolicyEngine.decide(obs("com.insta", Section.REELS), focusing)).reason)
    }
    test("domain matching respects label boundaries") {
        yes(PolicyEngine.domainMatches("m.facebook.com", "facebook.com")); yes(PolicyEngine.domainMatches("https://www.facebook.com/x?y=1", "facebook.com"))
        yes(!PolicyEngine.domainMatches("notfacebook.com", "facebook.com")); yes(!PolicyEngine.domainMatches("facebook.com.evil.io", "facebook.com"))
    }
    test("website blocklist and study allow-only mode") {
        val cfg = PolicyConfig(domainRules = DomainRules(blocked = setOf("reddit.com"), allowedOnlyDuringFocus = setOf("khanacademy.org")),
            focus = FocusPolicy(false, ListMode.BLOCKLIST, emptySet()))
        eq(BlockReason.WEBSITE_BLOCKED, blocked(PolicyEngine.decide(obs("com.chrome", domain = "old.reddit.com"), cfg)).reason)
        eq(BlockReason.WEBSITE_NOT_ALLOWED, blocked(PolicyEngine.decide(obs("com.chrome", domain = "example.com"), cfg)).reason)
        eq(Decision.Allow, PolicyEngine.decide(obs("com.chrome", domain = "www.khanacademy.org"), cfg))
        eq(Decision.Allow, PolicyEngine.decide(obs("com.chrome", domain = "example.com"), cfg.copy(focus = null)))
    }
    test("app limits: block at limit, snooze honoured, strict limit not snoozable") {
        val cfg = PolicyConfig(limits = mapOf("com.insta" to AppLimitState(30 * 60_000, 29 * 60_000)))
        eq(Decision.Allow, PolicyEngine.decide(obs("com.insta"), cfg))
        val over = PolicyConfig(limits = mapOf("com.insta" to AppLimitState(30 * 60_000, 31 * 60_000)))
        val b = blocked(PolicyEngine.decide(obs("com.insta"), over)); eq(BlockReason.LIMIT_REACHED, b.reason); yes(b.canSnooze)
        val snoozed = PolicyConfig(limits = mapOf("com.insta" to AppLimitState(30 * 60_000, 31 * 60_000, snoozedUntilWallMs = NOW + 1000)))
        eq(Decision.Allow, PolicyEngine.decide(obs("com.insta"), snoozed))
        val strict = PolicyConfig(limits = mapOf("com.insta" to AppLimitState(1, 2, strict = true)))
        yes(!blocked(PolicyEngine.decide(obs("com.insta"), strict)).canSnooze)
    }
    test("strict focus: no snooze, emergency only if exits left") {
        val cfg = PolicyConfig(focus = FocusPolicy(true, ListMode.ALLOWLIST, emptySet(), emergencyExitsLeft = 1))
        val b = blocked(PolicyEngine.decide(obs("com.insta"), cfg)); yes(!b.canSnooze); yes(b.canEmergency)
        yes(!blocked(PolicyEngine.decide(obs("com.insta"), cfg.copy(focus = cfg.focus!!.copy(emergencyExitsLeft = 0)))).canEmergency)
    }
    test("schedule window blocks only when active") {
        val s = ScheduleWindow(active = true, strict = false, mode = ListMode.BLOCKLIST, apps = setOf("com.insta"))
        eq(BlockReason.SCHEDULE_BLOCKED, blocked(PolicyEngine.decide(obs("com.insta"), PolicyConfig(schedules = listOf(s)))).reason)
        eq(Decision.Allow, PolicyEngine.decide(obs("com.insta"), PolicyConfig(schedules = listOf(s.copy(active = false)))))
    }
    test("focusPolicyFor: only enforces while RUNNING in FOCUS phase") {
        val t = Tick(NOW, 0)
        val s = SessionMachine.startTimer(1, 60 * 60_000L, t, breaks = BreakRules(1, 5 * 60_000L))
        yes(PolicyEngine.focusPolicyFor(s, ListMode.BLOCKLIST, emptySet()) != null)
        val onBreak = (SessionMachine.startBreak(s, Tick(NOW + 60_000, 60_000)) as Step.Ok).session
        yes(PolicyEngine.focusPolicyFor(onBreak, ListMode.BLOCKLIST, emptySet()) == null)
    }
}

fun usageTests() {
    val zone = ZoneId.of("Asia/Kolkata")
    fun ms(h: Int, m: Int, day: Int = 4) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    val MIN = 60_000L

    test("usage: basic foreground/background, implicit close by next foreground") {
        val ev = listOf(UsageEvent(ms(9, 0), "a", EventType.FOREGROUND), UsageEvent(ms(9, 10), "b", EventType.FOREGROUND),
            UsageEvent(ms(9, 25), "b", EventType.BACKGROUND))
        val iv = UsageSessionizer.intervals(ev, ms(10, 0))
        eq(listOf(UsageInterval("a", ms(9, 0), ms(9, 10)), UsageInterval("b", ms(9, 10), ms(9, 25))), iv)
    }
    test("usage: screen off closes the interval") {
        val ev = listOf(UsageEvent(ms(9, 0), "a", EventType.FOREGROUND), UsageEvent(ms(9, 5), null, EventType.SCREEN_OFF))
        eq(5 * MIN, UsageSessionizer.intervals(ev, ms(12, 0)).single().durationMs)
    }
    test("usage: lost event is capped, not inflated to end of window") {
        val ev = listOf(UsageEvent(ms(1, 0), "a", EventType.FOREGROUND))
        eq(2 * 60 * MIN, UsageSessionizer.intervals(ev, ms(23, 0)).single().durationMs)
    }
    test("usage: unsorted input and near-adjacent same-app intervals are merged") {
        val ev = listOf(UsageEvent(ms(9, 5), "a", EventType.BACKGROUND), UsageEvent(ms(9, 0), "a", EventType.FOREGROUND),
            UsageEvent(ms(9, 5) + 1000, "a", EventType.FOREGROUND), UsageEvent(ms(9, 10), "a", EventType.BACKGROUND))
        val iv = UsageSessionizer.intervals(ev, ms(10, 0))
        eq(1, iv.size); eq(10 * MIN, iv.single().durationMs)
    }
    test("usage: midnight split per day (local zone)") {
        val iv = listOf(UsageInterval("a", ms(23, 30), ms(0, 30, day = 5)))
        val d = UsageSessionizer.dailyTotals(iv, zone)
        eq(30 * MIN, d[LocalDate.of(2026, 11, 4)]!!["a"]); eq(30 * MIN, d[LocalDate.of(2026, 11, 5)]!!["a"])
    }
    test("usage: hourly split") {
        val h = UsageSessionizer.hourlyTotals(listOf(UsageInterval("a", ms(9, 50), ms(10, 20))), zone)
        eq(10 * MIN, h[java.time.LocalDateTime.of(2026, 11, 4, 9, 0)]); eq(20 * MIN, h[java.time.LocalDateTime.of(2026, 11, 4, 10, 0)])
    }
}

fun streakTests() {
    val today = LocalDate.of(2026, 11, 4)
    fun d(n: Long) = today.minusDays(n)
    test("streak: today pending does not break streak") {
        val r = Streaks.compute(mapOf(d(1) to 60, d(2) to 90, d(3) to 70, d(0) to 10), goalMinutes = 60, today = today)
        eq(3, r.current); eq(3, r.longest)
    }
    test("streak: today met extends; gap resets; longest tracked") {
        val r = Streaks.compute(mapOf(d(0) to 60, d(1) to 60, d(3) to 60, d(4) to 60, d(5) to 60, d(6) to 60), 60, today)
        eq(2, r.current); eq(4, r.longest)
    }
    test("streak: nothing met") { eq(StreakResult(0, 0), Streaks.compute(emptyMap(), 30, today)) }
}

fun timeFormatTests() {
    test("TimeFormat.clock") {
        eq("01:23", TimeFormat.clock(83_000)); eq("1:02:05", TimeFormat.clock(3_725_000)); eq("00:00", TimeFormat.clock(-5))
        eq("00:01", TimeFormat.clock(1)); eq("25:00", TimeFormat.clock(25 * 60_000L)); eq("00:00", TimeFormat.clock(0))
    }
    test("TimeFormat.minutes") { eq("45m", TimeFormat.minutes(45)); eq("1h 20m", TimeFormat.minutes(80)); eq("2h", TimeFormat.minutes(120)); eq("0m", TimeFormat.minutes(-3)) }
}
