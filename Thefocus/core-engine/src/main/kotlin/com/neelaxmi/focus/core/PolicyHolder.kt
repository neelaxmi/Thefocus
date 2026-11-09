package com.neelaxmi.focus.core

import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The currently enforced policy, published as an immutable snapshot that the accessibility service reads on every window
 * event (cheap, lock-free). [rebuild] runs on the io thread whenever something that affects blocking changes: session
 * state, schedules, group edits, or when [isStale] says a schedule boundary has passed.
 */
class PolicyHolder(
    private val sessions: SessionStore, private val groups: BlockConfigSource, private val schedules: ScheduleStore,
    private val alwaysAllowed: () -> Set<String>, private val ticks: TickSource,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    data class Snapshot(val assembled: Assembled, val builtAtWallMs: Long)

    @Volatile var snapshot: Snapshot = Snapshot(
        Assembled(PolicyConfig(), null, emptySet(), demanded = false, strictActive = false), 0L,
    )
        private set

    private val listeners = CopyOnWriteArrayList<(Snapshot) -> Unit>()
    fun addListener(l: (Snapshot) -> Unit) { listeners += l }
    fun removeListener(l: (Snapshot) -> Unit) { listeners -= l }

    fun rebuild(): Snapshot {
        val now = ticks.now().wallMs
        val a = PolicyAssembler.assemble(now, zone(), sessions.active(), groups.groups(), schedules.rules(), alwaysAllowed())
        val s = Snapshot(a, now)
        snapshot = s
        listeners.forEach { it(s) }
        return s
    }

    /** True once the next schedule boundary the snapshot was built for has passed: the caller should trigger [rebuild]. */
    fun isStale(nowWallMs: Long): Boolean = snapshot.assembled.validUntilWallMs?.let { nowWallMs >= it } == true
}
