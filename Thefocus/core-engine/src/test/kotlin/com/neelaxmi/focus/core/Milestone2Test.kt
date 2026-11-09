package com.neelaxmi.focus.core

import com.neelaxmi.focus.core.Harness.eq
import com.neelaxmi.focus.core.Harness.no
import com.neelaxmi.focus.core.Harness.test
import com.neelaxmi.focus.core.Harness.yes
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MIN = 60_000L
private val UTC = ZoneId.of("UTC")
private fun wall(y: Int, mo: Int, d: Int, h: Int, m: Int, z: ZoneId = UTC, s: Int = 0) = ZonedDateTime.of(y, mo, d, h, m, s, 0, z).toInstant().toEpochMilli()
// 2026-11-04 is a Wednesday
private fun wed(h: Int, m: Int, s: Int = 0) = wall(2026, 11, 4, h, m, UTC, s)
private fun rule(id: Long = 1, days: Int = ScheduleEngine.WEEKDAYS, start: Int = 9 * 60, end: Int = 11 * 60, enabled: Boolean = true, strict: Boolean = false,
                 auto: Boolean = true, groups: Set<Long> = emptySet()) = ScheduleRule(id, "r$id", days, start, end, enabled, strict, auto, groups)

// ---------------- schedule engine ----------------
fun scheduleEngineTests() {
    test("schedule: validation") {
        yes(rule(days = 0).validate() != null); yes(rule(start = 600, end = 600).validate() != null); yes(rule(start = -1).validate() != null)
        yes(rule(end = 1440).validate() != null); eq(null, rule().validate())
    }
    test("schedule: start inclusive, end exclusive, weekday mask respected") {
        val r = rule()
        yes(ScheduleEngine.isActive(r, wed(9, 0), UTC)); yes(ScheduleEngine.isActive(r, wed(10, 59, 59), UTC))
        no(ScheduleEngine.isActive(r, wed(11, 0), UTC)); no(ScheduleEngine.isActive(r, wed(8, 59, 59), UTC))
        no(ScheduleEngine.isActive(r, wall(2026, 11, 7, 10, 0), UTC))              // Saturday
        no(ScheduleEngine.isActive(r.copy(enabled = false), wed(10, 0), UTC))
    }
    test("schedule: overnight window belongs to the day it starts") {
        val r = rule(start = 22 * 60, end = 6 * 60)                                // Mon-Fri 22:00 -> 06:00
        eq("2026-11-04", ScheduleEngine.activeOccurrence(r, wed(23, 0), UTC)!!.dateKey)
        eq("2026-11-04", ScheduleEngine.activeOccurrence(r, wall(2026, 11, 5, 5, 0), UTC)!!.dateKey)   // Thu 05:00 still Wed's window
        yes(ScheduleEngine.isActive(r, wall(2026, 11, 7, 5, 0), UTC))              // Sat 05:00: Friday night
        no(ScheduleEngine.isActive(r, wall(2026, 11, 7, 23, 0), UTC))              // Sat night: Saturday not selected
        no(ScheduleEngine.isActive(r, wall(2026, 11, 9, 5, 0), UTC))               // Mon 05:00: would start Sunday (not selected)
    }
    test("schedule: DST spring-forward window is 1 real hour (01:30 EST -> 03:30 EDT)") {
        val ny = ZoneId.of("America/New_York")
        val r = rule(days = ScheduleEngine.SUN, start = 90, end = 3 * 60 + 30)
        val occ = ScheduleEngine.occurrences(r, wall(2026, 3, 8, 0, 5, ny), ny).first { it.dateKey == "2026-03-08" }
        eq(60 * MIN, occ.endWallMs - occ.startWallMs)
    }
    test("schedule: nextBoundary = next start / end / reminder, ignores disabled, null without rules") {
        val rs = listOf(rule(), rule(id = 2, enabled = false, start = 8 * 60 + 30))
        eq(wed(9, 0), ScheduleEngine.nextBoundary(rs, wed(8, 0), UTC)); eq(wed(8, 50), ScheduleEngine.nextBoundary(rs, wed(8, 0), UTC, reminderLeadMin = 10))
        eq(wed(11, 0), ScheduleEngine.nextBoundary(rs, wed(9, 0), UTC)); eq(wall(2026, 11, 5, 9, 0), ScheduleEngine.nextBoundary(rs, wed(11, 0), UTC))
        eq(null, ScheduleEngine.nextBoundary(emptyList(), wed(8, 0), UTC))
    }
    test("schedule: upcoming is ordered, only future, limited") {
        val up = ScheduleEngine.upcoming(listOf(rule(), rule(id = 2, start = 7 * 60, end = 8 * 60)), wed(8, 30), UTC, 3)
        eq(3, up.size); eq(listOf(wed(9, 0), wall(2026, 11, 5, 7, 0), wall(2026, 11, 5, 9, 0)), up.map { it.startWallMs })
    }
}

// ---------------- coordinator ----------------
private class MemSchedules(var rules: List<ScheduleRule>) : ScheduleStore {
    val ran = mutableSetOf<String>()
    override fun rules() = rules
    override fun markRun(ruleId: Long, key: String, wallMs: Long) = ran.add("$ruleId:$key")
}
private class Notif : ScheduleNotifier {
    val started = mutableListOf<Pair<Long, Boolean>>(); val reminders = mutableListOf<Long>()
    override fun reminder(rule: ScheduleRule, startWallMs: Long) { reminders += rule.id }
    override fun started(rule: ScheduleRule, sessionStarted: Boolean) { started += rule.id to sessionStarted }
}
private class Waker : ScheduleWaker { var at: Long? = null; override fun scheduleAt(wallMs: Long) { at = wallMs }; override fun cancel() { at = null } }
private class SRig(rules: List<ScheduleRule>, startWall: Long, lead: Int = 0) {
    val store = MemStore(); val ticks = FakeTicks(wall = startWall); val wake = FakeWake(); val pres = FakePresenter()
    val controller = SessionController(store, ticks, wake, pres) { UTC }
    val sched = MemSchedules(rules); val waker = Waker(); val notif = Notif()
    val coord = ScheduleCoordinator(sched, ticks, waker, notif, controller, { 120_000L to 1 }, { lead }) { UTC }
}

fun scheduleCoordinatorTests() {
    test("coordinator: auto-start inside the window with the REMAINING window length, schedules the end") {
        val r = SRig(listOf(rule()), wed(9, 0, 30)); val res = r.coord.sync()
        val s = r.store.active()!!; eq(Mode.TIMER, s.mode); eq(119 * MIN + 30_000, s.plannedMs); eq(listOf(1L), res.startedSessionForRules)
        eq(listOf(1L to true), r.notif.started); eq(wed(11, 0), r.waker.at)
    }
    test("coordinator: duplicate wake-ups never start a second session or notification (idempotent)") {
        val r = SRig(listOf(rule()), wed(9, 0)); r.coord.sync(); r.ticks.advance(5_000); r.coord.sync(); r.coord.sync()
        eq(1, r.store.sessions.size); eq(1, r.notif.started.size)
    }
    test("coordinator: user already in a session -> no second session, occurrence is consumed (no restart after they finish)") {
        val r = SRig(listOf(rule()), wed(9, 0))
        r.controller.start(StartConfig(Mode.TIMER, plannedMs = 10 * MIN)); r.coord.sync()
        eq(1, r.store.sessions.size); eq(listOf(1L to false), r.notif.started)
        r.ticks.advance(20 * MIN); r.controller.sync(); r.coord.sync()
        eq(1, r.store.sessions.size, "must not auto-start later in the same occurrence")
    }
    test("coordinator: strict schedule creates a strict session with the configured cooldown") {
        val r = SRig(listOf(rule(strict = true)), wed(9, 0)); r.coord.sync()
        eq(StrictRules(120_000L, 1), r.store.active()!!.strict)
    }
    test("coordinator: window already over (missed) -> nothing started, next wake is tomorrow") {
        val r = SRig(listOf(rule()), wed(12, 0)); val res = r.coord.sync()
        eq(null, r.store.active()); eq(emptyList<Long>(), res.startedSessionForRules); eq(wall(2026, 11, 5, 9, 0), r.waker.at)
    }
    test("coordinator: phone booted mid-window -> starts late with the remaining time") {
        val r = SRig(listOf(rule()), wed(10, 0)); r.coord.sync(); eq(60 * MIN, r.store.active()!!.plannedMs)
    }
    test("coordinator: reminder sent once, N minutes before start; wake scheduled at the reminder time") {
        val r = SRig(listOf(rule()), wed(8, 40), lead = 10); r.coord.sync(); eq(wed(8, 50), r.waker.at); eq(emptyList<Long>(), r.notif.reminders)
        r.ticks.advance(16 * MIN); r.coord.sync(); r.coord.sync(); eq(listOf(1L), r.notif.reminders)
    }
    test("coordinator: autoStart=false only notifies; invalid/disabled rules are ignored") {
        val r = SRig(listOf(rule(auto = false), rule(id = 2, days = 0), rule(id = 3, enabled = false)), wed(9, 30)); r.coord.sync()
        eq(null, r.store.active()); eq(listOf(1L to false), r.notif.started)
    }
}

// ---------------- assembler / enforcement / guard / categories ----------------
fun blockingTests() {
    val social = BlockGroup(1, "Social", GroupKind.SOCIAL, true, setOf("a", "b"))
    val games = BlockGroup(2, "Games", GroupKind.GAMES, false, setOf("c"))
    val t = Tick(wed(10, 0), 0)
    fun running(strict: Boolean = false): Session = SessionMachine.startTimer(1, 60 * MIN, t, strict = if (strict) StrictRules(60_000, 0) else null)

    test("assembler: focus session blocks apps of ENABLED groups only") {
        val a = PolicyAssembler.assemble(t.wallMs, UTC, running(), listOf(social, games), emptyList(), setOf("own"))
        eq(setOf("a", "b"), a.blockedApps); yes(a.demanded); no(a.strictActive)
        eq(BlockReason.SESSION_APP_BLOCKED, (PolicyEngine.decide(Observation("a", nowWallMs = t.wallMs), a.config) as Decision.Block).reason)
        eq(Decision.Allow, PolicyEngine.decide(Observation("c", nowWallMs = t.wallMs), a.config)); eq(Decision.Allow, PolicyEngine.decide(Observation("own", nowWallMs = t.wallMs), a.config))
    }
    test("assembler: paused session or no session blocks nothing") {
        val paused = (SessionMachine.pause(running(), Tick(t.wallMs + MIN, MIN)) as Step.Ok).session
        no(PolicyAssembler.assemble(t.wallMs, UTC, paused, listOf(social), emptyList(), emptySet()).demanded)
        no(PolicyAssembler.assemble(t.wallMs, UTC, null, listOf(social), emptyList(), emptySet()).demanded)
    }
    test("assembler: schedule uses ITS groups even if the group is not enabled for sessions; validUntil = next boundary") {
        val r = rule(groups = setOf(2L), strict = true)
        val a = PolicyAssembler.assemble(wed(10, 0), UTC, null, listOf(social, games), listOf(r), emptySet())
        eq(setOf("c"), a.blockedApps); yes(a.strictActive); eq(wed(11, 0), a.validUntilWallMs)
        eq(BlockReason.SCHEDULE_BLOCKED, (PolicyEngine.decide(Observation("c", nowWallMs = wed(10, 0)), a.config) as Decision.Block).reason)
        val outside = PolicyAssembler.assemble(wed(12, 0), UTC, null, listOf(social, games), listOf(r), emptySet())
        no(outside.demanded); eq(Decision.Allow, PolicyEngine.decide(Observation("c", nowWallMs = wed(12, 0)), outside.config))
    }
    test("assembler: strict session marks strictActive and strict decisions cannot be snoozed") {
        val a = PolicyAssembler.assemble(t.wallMs, UTC, running(strict = true), listOf(social), emptyList(), emptySet())
        yes(a.strictActive); no((PolicyEngine.decide(Observation("a", nowWallMs = t.wallMs), a.config) as Decision.Block).canSnooze)
    }
    test("config lock: strict session or strict schedule locks group/schedule edits; non-strict never does") {
        val locked = PolicyAssembler.assemble(t.wallMs, UTC, running(strict = true), listOf(social), emptyList(), emptySet())
        yes(locked.configLocked)
        no(PolicyAssembler.assemble(t.wallMs, UTC, running(strict = false), listOf(social), emptyList(), emptySet()).configLocked)
        no(PolicyAssembler.assemble(t.wallMs, UTC, null, listOf(social), emptyList(), emptySet()).configLocked)
        val strictRule = rule(strict = true, groups = setOf(1L))
        yes(PolicyAssembler.assemble(wed(10, 0), UTC, null, listOf(social), listOf(strictRule), emptySet()).configLocked)
        no(PolicyAssembler.assemble(wed(12, 0), UTC, null, listOf(social), listOf(strictRule), emptySet()).configLocked)       // window over: editable again
        no(PolicyAssembler.assemble(wed(10, 0), UTC, null, listOf(social), listOf(rule(strict = false, groups = setOf(1L))), emptySet()).configLocked)
    }
    test("enforcement status never claims enforcement without the accessibility service") {
        eq(Enforcement.UNAVAILABLE_NEEDS_ACCESSIBILITY, EnforcementStatus.of(false, true, 5)); eq(Enforcement.OFF_NEEDS_ACCESSIBILITY, EnforcementStatus.of(false, false, 5))
        eq(Enforcement.NOTHING_SELECTED, EnforcementStatus.of(true, true, 0)); eq(Enforcement.ENFORCING, EnforcementStatus.of(true, true, 2)); eq(Enforcement.READY, EnforcementStatus.of(true, false, 0))
    }
    test("settings guard: protects OUR pages only, never other apps' pages") {
        val label = "NEELAXMI Focus"
        eq(ProtectedKind.OWN_APP_INFO, SettingsGuard.classify(WindowInfo("com.android.settings", "com.android.settings.applications.InstalledAppDetailsTop", listOf("App info", "NEELAXMI Focus")), label))
        eq(null, SettingsGuard.classify(WindowInfo("com.android.settings", "com.android.settings.applications.InstalledAppDetailsTop", listOf("App info", "Instagram")), label))
        eq(ProtectedKind.ACCESSIBILITY_PAGE, SettingsGuard.classify(WindowInfo("com.android.settings", "com.android.settings.accessibility.ToggleAccessibilityServicePreferenceFragment", listOf("neelaxmi focus")), label))
        eq(ProtectedKind.DEVICE_ADMIN, SettingsGuard.classify(WindowInfo("com.android.settings", "com.android.settings.DeviceAdminAdd", listOf("NEELAXMI Focus")), label))
        eq(ProtectedKind.UNINSTALL_DIALOG, SettingsGuard.classify(WindowInfo("com.google.android.packageinstaller", "com.android.packageinstaller.UninstallerActivity", listOf("Uninstall NEELAXMI Focus?")), label))
        eq(null, SettingsGuard.classify(WindowInfo("com.android.settings", "com.android.settings.Settings\$WifiSettingsActivity", listOf("Wi-Fi", "NEELAXMI Focus")), label))
        no(SettingsGuard.isCandidatePackage("com.instagram.android")); yes(SettingsGuard.isCandidatePackage("com.android.settings"))
    }
    test("categoriser: known packages beat the SDK hint; hint is used only for unknown packages") {
        eq(Category.SOCIAL, AppCategorizer.categorize("com.instagram.android", PlatformCategory.GAME)); eq(Category.GAMES, AppCategorizer.categorize("com.unknown.x", PlatformCategory.GAME))
        eq(Category.OTHER, AppCategorizer.categorize("com.unknown.y")); eq(Category.BROWSERS, AppCategorizer.categorize("com.android.chrome"))
    }
    test("default groups: suggestions come from what is installed; browsers off by default; distracting set merges groups") {
        val installed = listOf(AppRef("com.instagram.android", "Insta", Category.SOCIAL), AppRef("com.android.chrome", "Chrome", Category.BROWSERS), AppRef("com.notes", "Notes", Category.OTHER))
        val s = DefaultGroups.suggest(installed)
        eq(setOf("com.instagram.android"), s[GroupKind.SOCIAL]); eq(setOf("com.android.chrome"), s[GroupKind.BROWSERS]); eq(emptySet<String>(), s[GroupKind.CUSTOM])
        no(DefaultGroups.enabledByDefault(GroupKind.BROWSERS)); yes(DefaultGroups.enabledByDefault(GroupKind.SOCIAL))
        eq(setOf("com.instagram.android", "com.notes"), DefaultGroups.distractingPackages(installed, listOf(BlockGroup(9, "c", GroupKind.CUSTOM, true, setOf("com.notes")))))
    }
}

// ---------------- analytics ----------------
fun analyticsTests() {
    fun ev(d: Int, h: Int, m: Int, pkg: String?, type: EventType) = UsageEvent(wall(2026, 11, d, h, m), pkg, type)
    val events = listOf(
        ev(3, 9, 0, "a", EventType.FOREGROUND), ev(3, 9, 30, "b", EventType.FOREGROUND), ev(3, 9, 45, null, EventType.SCREEN_OFF),
        ev(4, 8, 0, null, EventType.UNLOCK), ev(4, 8, 1, "launcher", EventType.FOREGROUND), ev(4, 8, 2, "a", EventType.FOREGROUND), ev(4, 8, 12, "a", EventType.BACKGROUND),
        ev(4, 12, 0, null, EventType.UNLOCK), ev(4, 12, 1, "b", EventType.FOREGROUND), ev(4, 12, 6, "b", EventType.BACKGROUND),
    )
    test("rollup: per-day, per-app totals from real events; ignored packages excluded; unlocks counted") {
        val r = UsageRollup.compute(events, wall(2026, 11, 3, 0, 0), wall(2026, 11, 4, 23, 0), UTC, setOf("launcher"))
        eq(2, r.size); eq(mapOf("a" to 30 * MIN, "b" to 15 * MIN), r[0].perApp); eq(45 * MIN, r[0].totalMs); eq(0, r[0].unlocks)
        eq(mapOf("a" to 10 * MIN, "b" to 5 * MIN), r[1].perApp); eq(2, r[1].unlocks)
    }
    test("rollup: a day only partly covered by the retention window is NOT returned (never store incomplete days)") {
        val r = UsageRollup.compute(events, wall(2026, 11, 3, 9, 0), wall(2026, 11, 4, 23, 0), UTC, emptySet())
        eq(listOf(LocalDate.of(2026, 11, 4)), r.map { it.date })
    }
    test("rollup: no events -> no rows (nothing fabricated)") { eq(emptyList<DailyUsage>(), UsageRollup.compute(emptyList(), 0, wall(2026, 11, 4, 0, 0), UTC, emptySet())) }

    val d0 = LocalDate.of(2026, 11, 14)
    fun row(back: Long, a: Long, b: Long, unlocks: Int = 5) = DailyUsage(d0.minusDays(back), mapOf("insta" to a * MIN, "notes" to b * MIN), (a + b) * MIN, unlocks)
    test("report: totals, averages over days WITH data, top apps, distracting ranking, unlocks") {
        val rows = listOf(row(0, 60, 30), row(1, 90, 30), row(3, 30, 60))
        val rep = UsageReports.build(rows, d0.minusDays(4), d0, setOf("insta"))
        eq(5, rep.days.size); eq(3, rep.daysWithData); eq(false, rep.days.first().hasData); eq(300 * MIN, rep.totalMs); eq(100 * MIN, rep.avgPerDayMs)
        eq("insta", rep.topApps.first().pkg); eq(180 * MIN, rep.topApps.first().ms); eq(listOf("insta"), rep.topDistracting.map { it.pkg }); eq(15, rep.unlocks)
        eq(60 * MIN, rep.days.last().distractingMs)
    }
    test("report: missing days are reported as 'no data', not zero usage") {
        val rep = UsageReports.build(emptyList(), d0.minusDays(2), d0, emptySet()); eq(0, rep.daysWithData); eq(0L, rep.avgPerDayMs); eq(null, rep.trendPct)
        yes(rep.days.none { it.hasData })
    }
    test("trend: last 7 days vs previous 7; null when a side has no data") {
        val rows = (0L..6L).map { row(it, 50, 50) } + (7L..13L).map { row(it, 100, 100) }
        eq(-50, UsageReports.trendPct(rows, d0)); eq(null, UsageReports.trendPct((0L..6L).map { row(it, 50, 50) }, d0))
    }
    test("focus vs distraction: distraction is null (unknown) on days without usage data") {
        val days = listOf(DayPoint(d0, 0, 45 * MIN, 3, true), DayPoint(d0.minusDays(1), 0, 0, 0, false))
        val f = UsageReports.focusVsDistraction(days, mapOf(d0 to 70))
        eq(UsageReports.FocusVsDistraction(d0, 70, 45), f[0]); eq(null, f[1].distractingMin)
    }
    test("productivity score: formula, clamping, and NO score without usage data") {
        eq(70, Productivity.score(60, 120, 0)); eq(60, Productivity.score(120, 120, 120)); eq(100, Productivity.score(500, 120, 0)); eq(0, Productivity.score(0, 120, 999))
        eq(null, Productivity.score(60, 120, null))
    }
}

// ---------------- achievements ----------------
fun achievementTests() {
    val today = LocalDate.of(2026, 11, 4)   // Wednesday
    fun sess(id: Long, daysAgo: Long, min: Long, state: SessionState = SessionState.COMPLETED, adj: Boolean = false): Session {
        val start = today.minusDays(daysAgo).atTime(10, 0).atZone(UTC).toInstant().toEpochMilli()
        return SessionMachine.startTimer(id, 60 * MIN, Tick(start, 0)).copy(state = state, focusMs = min * MIN, endedAtWallMs = start + min * MIN, clockAdjusted = adj)
    }
    fun eval(ss: List<Session>, goal: Int = 60, facts: Map<LocalDate, DayFacts> = emptyMap()) =
        Achievements.evaluate(AchievementInput(ss, goal, today, UTC, facts)).associateBy { it.id }

    test("achievements: nothing earned with no data; progress is zero, never fake") {
        val e = eval(emptyList()); yes(e.values.none { it.unlocked }); eq(0L, e.getValue(AchievementId.FOCUS_1H).current); eq(9, e.size)
    }
    test("achievements: first session + 1 hour from a real completed 60-minute session; 5h progress 60/300") {
        val e = eval(listOf(sess(1, 0, 60)))
        yes(e.getValue(AchievementId.FIRST_SESSION).unlocked); yes(e.getValue(AchievementId.FOCUS_1H).unlocked)
        no(e.getValue(AchievementId.FOCUS_5H).unlocked); eq(60L, e.getValue(AchievementId.FOCUS_5H).current); eq(0.2f, e.getValue(AchievementId.FOCUS_5H).fraction)
    }
    test("achievements: an early-ended session counts minutes but is not a 'completed' first session; tampered sessions count for nothing") {
        val e = eval(listOf(sess(1, 0, 30, SessionState.ABANDONED), sess(2, 1, 600, adj = true)))
        no(e.getValue(AchievementId.FIRST_SESSION).unlocked); eq(30L, e.getValue(AchievementId.FOCUS_1H).current); no(e.getValue(AchievementId.FOCUS_10H).unlocked)
    }
    test("achievements: hours accumulate across sessions (5h and 10h)") {
        val ss = (1L..10L).map { sess(it, it, 60) }
        val e = eval(ss); yes(e.getValue(AchievementId.FOCUS_10H).unlocked)
        no(eval((1L..4L).map { sess(it, it, 60) }).getValue(AchievementId.FOCUS_5H).unlocked)
    }
    test("achievements: streak 3 unlocked by 3 consecutive goal days; 7-day shows 3/7; goal not met = no streak") {
        val e = eval(listOf(sess(1, 0, 60), sess(2, 1, 60), sess(3, 2, 60)))
        yes(e.getValue(AchievementId.STREAK_3).unlocked); no(e.getValue(AchievementId.STREAK_7).unlocked); eq(3L, e.getValue(AchievementId.STREAK_7).current)
        no(eval(listOf(sess(1, 0, 59), sess(2, 1, 59), sess(3, 2, 59))).getValue(AchievementId.STREAK_3).unlocked)
    }
    test("achievements: 7 and 30 day streaks") {
        yes(eval((0L..6L).map { sess(it + 1, it, 60) }).getValue(AchievementId.STREAK_7).unlocked)
        yes(eval((0L..29L).map { sess(it + 1, it, 60) }).getValue(AchievementId.STREAK_30).unlocked)
    }
    test("achievements: distraction-free day needs a session AND real usage data under the threshold") {
        val ss = listOf(sess(1, 0, 60))
        yes(eval(ss, facts = mapOf(today to DayFacts(5 * MIN))).getValue(AchievementId.DISTRACTION_FREE_DAY).unlocked)
        no(eval(ss, facts = mapOf(today to DayFacts(20 * MIN))).getValue(AchievementId.DISTRACTION_FREE_DAY).unlocked)
        no(eval(ss).getValue(AchievementId.DISTRACTION_FREE_DAY).unlocked)                                             // no usage data -> cannot be earned
        no(eval(emptyList(), facts = mapOf(today to DayFacts(0))).getValue(AchievementId.DISTRACTION_FREE_DAY).unlocked)  // no session that day
    }
    test("achievements: weekly champion = goal on >= 5 days of ONE Mon-Sun week (4 days, or 5 days split over two weeks, is not enough)") {
        // today = Wed 2026-11-04. daysAgo 9..5 = Mon 26 Oct .. Fri 30 Oct (one calendar week)
        val five = eval((5L..9L).map { sess(it, it, 60) })
        yes(five.getValue(AchievementId.WEEKLY_CHAMPION).unlocked); eq(5L, five.getValue(AchievementId.WEEKLY_CHAMPION).current)
        val four = eval((5L..8L).map { sess(it, it, 60) }); no(four.getValue(AchievementId.WEEKLY_CHAMPION).unlocked); eq(4L, four.getValue(AchievementId.WEEKLY_CHAMPION).current)
        // daysAgo 9..13 = Mon 26 Oct, Sun 25, Sat 24, Fri 23, Thu 22 -> split over two calendar weeks (1 + 4)
        val split = eval((9L..13L).map { sess(it, it, 60) }); no(split.getValue(AchievementId.WEEKLY_CHAMPION).unlocked); eq(4L, split.getValue(AchievementId.WEEKLY_CHAMPION).current)
    }
    test("achievements: newlyUnlocked excludes already stored ids (unlocks are permanent, notified once)") {
        val ev = Achievements.evaluate(AchievementInput(listOf(sess(1, 0, 60)), 60, today, UTC, emptyMap()))
        eq(setOf(AchievementId.FIRST_SESSION, AchievementId.FOCUS_1H), Achievements.newlyUnlocked(emptySet(), ev).toSet())
        eq(listOf(AchievementId.FOCUS_1H), Achievements.newlyUnlocked(setOf(AchievementId.FIRST_SESSION), ev)); eq(emptyList<AchievementId>(), Achievements.newlyUnlocked(ev.filter { it.unlocked }.map { it.id }.toSet(), ev))
    }
}

// ---------------- controller restart ----------------
fun restartTests() {
    test("restart: ends the current session (kept in history) and starts an identical fresh one") {
        val store = MemStore(); val ticks = FakeTicks(); val wake = FakeWake(); val pres = FakePresenter()
        val c = SessionController(store, ticks, wake, pres) { UTC }
        c.start(StartConfig(Mode.TIMER, plannedMs = 25 * MIN)); ticks.advance(7 * MIN)
        val out = c.restart() as Outcome.Applied
        eq(2L, out.session.id); eq(25 * MIN, out.session.plannedMs); eq(0L, out.session.focusTotalMs); eq(25 * MIN, wake.delay)
        eq(SessionState.ABANDONED, store.sessions.getValue(1).state); eq(7 * MIN, store.sessions.getValue(1).focusMs)
    }
    test("restart: refused in strict mode (would bypass the end cooldown); NoActive when idle") {
        val store = MemStore(); val c = SessionController(store, FakeTicks(), FakeWake(), FakePresenter()) { UTC }
        eq(Outcome.NoActive, c.restart())
        c.start(StartConfig(Mode.TIMER, plannedMs = 25 * MIN, strictEndCooldownMs = 60_000))
        eq(Outcome.Rejected(Reason.STRICT_LOCKED), c.restart()); eq(1, store.sessions.size)
    }
    test("restart: pomodoro keeps its configuration; stopwatch's old session is saved as completed") {
        val store = MemStore(); val ticks = FakeTicks(); val c = SessionController(store, ticks, FakeWake(), FakePresenter()) { UTC }
        val cfg = PomodoroConfig(25 * MIN, 5 * MIN, 15 * MIN, 4, 2)
        c.start(StartConfig(Mode.POMODORO, pomodoro = cfg)); eq(cfg, (c.restart() as Outcome.Applied).session.pomodoro)
        c.stop(); c.start(StartConfig(Mode.STOPWATCH)); ticks.advance(5 * MIN); c.restart(); eq(SessionState.COMPLETED, store.sessions.values.filter { it.mode == Mode.STOPWATCH }.minByOrNull { it.id }!!.state)
    }
}

fun scheduleFormatTests() {
    test("schedule format: day kinds, indexes and toggling") {
        eq(DaysKind.EVERY_DAY, ScheduleFormat.daysKind(ScheduleEngine.EVERY_DAY)); eq(DaysKind.WEEKDAYS, ScheduleFormat.daysKind(ScheduleEngine.WEEKDAYS))
        eq(DaysKind.WEEKEND, ScheduleFormat.daysKind(ScheduleEngine.SAT or ScheduleEngine.SUN)); eq(DaysKind.CUSTOM, ScheduleFormat.daysKind(ScheduleEngine.MON or ScheduleEngine.WED))
        eq(listOf(0, 2), ScheduleFormat.dayIndexes(ScheduleEngine.MON or ScheduleEngine.WED))
        eq(ScheduleEngine.MON or ScheduleEngine.TUE, ScheduleFormat.toggleDay(ScheduleEngine.MON, 1)); eq(ScheduleEngine.MON, ScheduleFormat.toggleDay(ScheduleEngine.MON or ScheduleEngine.TUE, 1))
    }
    test("schedule format: time text and overnight detection") {
        eq("09:05", ScheduleFormat.time(545)); eq("00:00", ScheduleFormat.time(0)); eq("23:59", ScheduleFormat.time(1439))
        yes(ScheduleFormat.crossesMidnight(rule(start = 22 * 60, end = 6 * 60))); no(ScheduleFormat.crossesMidnight(rule()))
    }
}
