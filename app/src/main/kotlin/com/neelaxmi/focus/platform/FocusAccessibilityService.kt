package com.neelaxmi.focus.platform

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.neelaxmi.focus.R
import com.neelaxmi.focus.core.BlockReason
import com.neelaxmi.focus.core.Decision
import com.neelaxmi.focus.core.Observation
import com.neelaxmi.focus.core.PolicyEngine
import com.neelaxmi.focus.core.SettingsGuard
import com.neelaxmi.focus.core.WindowInfo

/**
 * The ONLY enforcement path for blocking. Reads, per window change: the foreground package name and class name.
 * It reads window TEXT only when the foreground window is a system Settings / package-installer screen while strict
 * protection is on, in memory, to recognise this app's own pages; nothing is stored or transmitted. It never reads
 * keystrokes, messages, passwords or any other app's content. It cannot work unless the user enables it in system settings.
 */
class FocusAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var loc: ServiceLocator
    private lateinit var overlay: BlockOverlay
    private var shownFor: String? = null
    private val onPolicyChanged: (Any) -> Unit = { main.post { evaluateForeground() } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        loc = applicationContext as ServiceLocator
        overlay = BlockOverlay(this)
        connected = true
        loc.holder.addListener(onPolicyChanged)
        loc.io.execute { loc.holder.rebuild() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        handle(pkg, event.className?.toString())
    }

    /** A new policy may demand blocking an app that is ALREADY on screen: check it right away instead of waiting for a window change. */
    private fun evaluateForeground() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString()
        @Suppress("DEPRECATION") runCatching { root.recycle() }
        if (pkg == null) return
        if (!loc.holder.snapshot.assembled.demanded) { hideOverlay(); return }
        handle(pkg, null)
    }

    private fun handle(pkg: String, className: String?) {
        if (pkg == packageName) return                                     // our own windows (including the block screen) never trigger anything
        if (pkg in loc.alwaysAllowed.neutral()) return                     // status bar / keyboard on top of an app: keep current state
        val now = System.currentTimeMillis()
        if (loc.holder.isStale(now)) loc.io.execute { loc.holder.rebuild() }
        val snap = loc.holder.snapshot.assembled

        if (snap.strictActive && SettingsGuard.isCandidatePackage(pkg)) {
            val texts = collectTexts(rootInActiveWindow)
            if (SettingsGuard.classify(WindowInfo(pkg, className, texts), getString(R.string.app_name)) != null) {
                showBlock(pkg, getString(R.string.block_msg_protected), canEmergency = false, canEnd = false, emergencyLeft = 0)
                return
            }
        }
        when (val d = PolicyEngine.decide(Observation(pkg, nowWallMs = now), snap.config)) {
            Decision.Allow -> hideOverlay()
            is Decision.Block -> {
                val label = loc.catalog.label(pkg)
                val msg = when (d.reason) {
                    BlockReason.SCHEDULE_BLOCKED -> getString(R.string.block_msg_schedule, label)
                    else -> getString(R.string.block_msg_session, label)
                }
                val focus = snap.config.focus
                showBlock(pkg, msg, canEmergency = d.canEmergency, canEnd = focus != null && !focus.strict, emergencyLeft = focus?.emergencyExitsLeft ?: 0)
            }
        }
    }

    private fun showBlock(pkg: String, message: String, canEmergency: Boolean, canEnd: Boolean, emergencyLeft: Int) {
        if (shownFor == pkg && overlay.isShowing) return
        shownFor = pkg
        overlay.show(BlockOverlay.Model(
            title = getString(R.string.block_title), message = message, goHome = getString(R.string.block_go_home),
            emergency = if (canEmergency) getString(R.string.block_emergency, emergencyLeft) else null,
            endSession = if (canEnd) getString(R.string.block_end_session) else null,
            onGoHome = { hideOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) },
            onEmergency = { hideOverlay(); loc.io.execute { loc.controller.stop(emergency = true); loc.holder.rebuild() } },
            onEndSession = { hideOverlay(); loc.io.execute { loc.controller.stop(emergency = false); loc.holder.rebuild() } },
        ))
    }

    private fun hideOverlay() { shownFor = null; if (overlay.isShowing) overlay.hide() }

    /** Up to [limit] nodes, in memory only, skipping password fields. Used solely to recognise this app's own Settings pages. */
    private fun collectTexts(root: AccessibilityNodeInfo?, limit: Int = 80): List<String> {
        if (root == null) return emptyList()
        val out = ArrayList<String>(); val queue = ArrayDeque<AccessibilityNodeInfo>(); queue.add(root); var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val n = queue.removeFirst(); seen++
            if (!n.isPassword) { n.text?.let { out += it.toString() }; n.contentDescription?.let { out += it.toString() } }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return out
    }

    override fun onInterrupt() { hideOverlay() }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected = false
        if (::loc.isInitialized) loc.holder.removeListener(onPolicyChanged)
        if (::overlay.isInitialized) overlay.hide()
        return super.onUnbind(intent)
    }

    companion object {
        /** True only while the system has this service bound: the single source of truth for "can we really block?". */
        @Volatile var connected = false
    }
}
