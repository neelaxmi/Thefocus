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
private fun w(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, UTC).toInstant().toEpochMilli()

private class FakeSource(var access: Boolean = true, var events: List<UsageEvent> = emptyList()) : UsageEventSource {
    var lastFrom = -1L; var reads = 0
    override fun hasAccess() = access
    override fun read(fromMs: Long, toMs: Long): List<UsageEvent> { lastFrom = fromMs; reads++; return events.filter { it.tsMs in fromMs..toMs } }
}
private class MemUsage : DailyUsageStore {
    val days = HashMap<LocalDate, DailyUsage>(); var writes = 0
    override fun saveDays(days: List<DailyUsage>, nowWallMs: Long) { writes++; days.forEach { this.days[it.date] = it } }
    override fun rows(from: LocalDate, to: LocalDate) = days.values.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.sortedBy { it.date }
    override fun firstDay() = days.keys.minOrNull()
}
private class MemAch : AchievementStore { val m = HashMap<AchievementId, Long>()
    override fun unlockedAchievements() = m.keys.toSet(); override fun unlock(id: AchievementId, wallMs: Long) = m.putIfAbsent(id, wallMs) == null
    override fun unlockTimes(): Map<AchievementId, Long> = m }
private class Notes : AchievementNotifier { val got = mutableListOf<AchievementId>(); override fun unlocked(id: AchievementId) { got += id } }

fun servicesTests() {
    fun ev(d: Int, h: Int, m: Int, pkg: String?, t: EventType) = UsageEvent(w(d, h, m), pkg, t)

    test("rollup service: without usage access NOTHING is read or written (no fabricated analytics)") {
        val src = FakeSource(access = false); val store = MemUsage()
        val r = RollupService(src, store, FakeTicks(wall = w(5, 12, 0)), { emptySet() }) { UTC }.sync()
        eq(RollupStatus.NO_ACCESS, r.status); eq(0, src.reads); eq(0, store.writes)
    }
    test("rollup service: first run looks back 5 days, later runs 3; stores only fully covered days") {
        val src = FakeSource(events = listOf(ev(1, 9, 0, "a", EventType.FOREGROUND), ev(1, 9, 30, "a", EventType.BACKGROUND), ev(4, 8, 0, "b", EventType.FOREGROUND), ev(4, 8, 20, "b", EventType.BACKGROUND)))
        val store = MemUsage(); val svc = RollupService(src, store, FakeTicks(wall = w(5, 12, 0)), { emptySet() }) { UTC }
        val r = svc.sync(); eq(RollupStatus.OK, r.status); eq(ZonedDateTime.of(2026, 10, 31, 0, 0, 0, 0, UTC).toInstant().toEpochMilli(), src.lastFrom, "first run: 5 days back")
        eq(setOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4)), store.days.keys); eq(30 * MIN, store.days.getValue(LocalDate.of(2026, 11, 1)).totalMs)
        svc.sync(); eq(ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, UTC).toInstant().toEpochMilli(), src.lastFrom, "later runs: 3 days back")
        yes(store.days.containsKey(LocalDate.of(2026, 11, 1)), "an already stored day is never erased by a later run with a shorter window")
    }
    test("rollup service: no events -> nothing written; recompute replaces today's partial value; launcher excluded") {
        val store = MemUsage(); val src = FakeSource(events = emptyList()); val t = FakeTicks(wall = w(5, 10, 0))
        val svc = RollupService(src, store, t, { setOf("launcher") }) { UTC }
        eq(RollupStatus.NO_EVENTS, svc.sync().status); eq(0, store.writes)
        src.events = listOf(ev(5, 9, 0, "a", EventType.FOREGROUND), ev(5, 9, 10, "launcher", EventType.FOREGROUND), ev(5, 9, 15, "launcher", EventType.BACKGROUND))
        svc.sync(); eq(10 * MIN, store.days.getValue(LocalDate.of(2026, 11, 5)).totalMs, "launcher time ignored")
        src.events = src.events + listOf(ev(5, 9, 40, "a", EventType.FOREGROUND), ev(5, 9, 50, "a", EventType.BACKGROUND)); t.advance(40 * MIN); svc.sync()
        eq(20 * MIN, store.days.getValue(LocalDate.of(2026, 11, 5)).totalMs, "recompute replaces, not accumulates")
    }

    // ---- achievements service ----
    fun finished(id: Long, day: Int, min: Long): Session {
        val start = w(day, 10, 0)
        return SessionMachine.startTimer(id, 60 * MIN, Tick(start, 0)).copy(state = SessionState.COMPLETED, focusMs = min * MIN, endedAtWallMs = start + min * MIN)
    }
    test("achievement service: unlocks from real stored sessions, notifies exactly once, never re-notifies") {
        val ss = MemStore(); ss.save(finished(1, 4, 60), emptyList())
        val ach = MemAch(); val n = Notes()
        val svc = AchievementService(ss, MemUsage(), ach, n, FakeTicks(wall = w(5, 12, 0)), { 60 }, { emptySet() }) { UTC }
        eq(setOf(AchievementId.FIRST_SESSION, AchievementId.FOCUS_1H), svc.sync().toSet()); eq(2, n.got.size)
        eq(emptyList<AchievementId>(), svc.sync()); eq(2, n.got.size)
        ss.save(finished(2, 5, 60), emptyList()); ss.save(finished(3, 3, 200), emptyList())
        // days 3,4,5 each meet the 60-min goal -> 3-day streak is ALSO legitimately earned now
        eq(setOf(AchievementId.FOCUS_5H, AchievementId.STREAK_3), svc.sync().toSet()); eq(4, n.got.size)
        eq(emptyList<AchievementId>(), svc.sync()); eq(4, n.got.size)
        eq(ach.m.keys, svc.unlockTimes().keys)
    }
    test("achievement service: distraction-free day uses persisted usage rollups and the distracting set") {
        val ss = MemStore(); ss.save(finished(1, 5, 60), emptyList()); val usage = MemUsage()
        usage.days[LocalDate.of(2026, 11, 5)] = DailyUsage(LocalDate.of(2026, 11, 5), mapOf("insta" to 4 * MIN, "notes" to 500 * MIN), 504 * MIN, 3)
        val n = Notes(); val svc = AchievementService(ss, usage, MemAch(), n, FakeTicks(wall = w(5, 20, 0)), { 60 }, { setOf("insta") }) { UTC }
        yes(svc.sync().contains(AchievementId.DISTRACTION_FREE_DAY))
        val usage2 = MemUsage(); usage2.days[LocalDate.of(2026, 11, 5)] = DailyUsage(LocalDate.of(2026, 11, 5), mapOf("insta" to 40 * MIN), 40 * MIN, 3)
        no(AchievementService(ss, usage2, MemAch(), Notes(), FakeTicks(wall = w(5, 20, 0)), { 60 }, { setOf("insta") }) { UTC }.sync().contains(AchievementId.DISTRACTION_FREE_DAY))
    }
    test("home insights: most distracting app today, else last 7 days, else null (no data is not zero)") {
        val today = LocalDate.of(2026, 11, 5)
        val rows = listOf(DailyUsage(today, mapOf("a" to 10 * MIN, "x" to 99 * MIN), 109 * MIN, 1), DailyUsage(today.minusDays(2), mapOf("b" to 50 * MIN), 50 * MIN, 1))
        eq("a" to true, HomeInsights.mostDistracting(rows, today, setOf("a", "b"))!!.let { it.first.pkg to it.second })
        eq("b" to false, HomeInsights.mostDistracting(rows.drop(1), today, setOf("a", "b"))!!.let { it.first.pkg to it.second })
        eq(null, HomeInsights.mostDistracting(emptyList(), today, setOf("a")))
    }
}

private class MemGroups(var list: List<BlockGroup>) : BlockConfigSource { override fun groups() = list }

fun policyHolderTests() {
    val social = BlockGroup(1, "Social", GroupKind.SOCIAL, true, setOf("insta"))
    fun rig(rules: List<ScheduleRule> = emptyList(), wallMs: Long = w(4, 10, 0)): Triple<PolicyHolder, SessionController, FakeTicks> {
        val store = MemStore(); val ticks = FakeTicks(wall = wallMs)
        val ctl = SessionController(store, ticks, FakeWake(), FakePresenter()) { UTC }
        val holder = PolicyHolder(store, MemGroups(listOf(social)), object : ScheduleStore {
            override fun rules() = rules; override fun markRun(ruleId: Long, key: String, wallMs: Long) = true }, { setOf("own") }, ticks) { UTC }
        return Triple(holder, ctl, ticks)
    }
    test("policy holder: nothing blocked before a session; blocks the group while a focus session runs; releases when it ends") {
        val (h, c, t) = rig()
        no(h.rebuild().assembled.demanded)
        c.start(StartConfig(Mode.TIMER, plannedMs = 30 * MIN)); val s = h.rebuild()
        yes(s.assembled.demanded); eq(Decision.Allow, PolicyEngine.decide(Observation("own", nowWallMs = 0), s.assembled.config))
        yes(PolicyEngine.decide(Observation("insta", nowWallMs = 0), s.assembled.config) is Decision.Block)
        t.advance(31 * MIN); c.sync(); no(h.rebuild().assembled.demanded)
    }
    test("policy holder: paused session and break phase do not block") {
        val (h, c, _) = rig()
        c.start(StartConfig(Mode.TIMER, plannedMs = 60 * MIN, breaks = BreakRules(1, 5 * MIN))); c.startBreak()
        no(h.rebuild().assembled.demanded)
        c.endBreakEarly(); c.pause(); no(h.rebuild().assembled.demanded); c.resume(); yes(h.rebuild().assembled.demanded)
    }
    test("policy holder: schedule window blocks, goes stale at its boundary, and rebuilding ends the block") {
        val r = ScheduleRule(1, "Study", ScheduleEngine.WEEKDAYS, 9 * 60, 11 * 60, true, false, false, setOf(1L))
        val (h, _, t) = rig(listOf(r), wallMs = w(4, 10, 0))
        val s = h.rebuild(); yes(s.assembled.demanded); eq(w(4, 11, 0), s.assembled.validUntilWallMs)
        no(h.isStale(t.wall)); t.advance(61 * MIN); yes(h.isStale(t.wall)); no(h.rebuild().assembled.demanded)
    }
    test("policy holder: listeners are told about every rebuild (accessibility service re-checks the foreground app)") {
        val (h, _, _) = rig(); var n = 0; h.addListener { n++ }; h.rebuild(); h.rebuild(); eq(2, n)
    }
}
