package com.neelaxmi.focus.platform

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen block screen drawn as an ACCESSIBILITY OVERLAY window owned by the accessibility service: it needs no
 * "draw over other apps" permission. Plain Views (no Compose) because a service has no Activity lifecycle.
 */
class BlockOverlay(private val service: AccessibilityService) {
    class Model(
        val title: String, val message: String, val goHome: String,
        val emergency: String?, val endSession: String?,
        val onGoHome: () -> Unit, val onEmergency: () -> Unit, val onEndSession: () -> Unit,
    )

    private val wm = service.service<WindowManager>()
    private var view: View? = null
    val isShowing: Boolean get() = view != null

    private fun dp(v: Int) = (v * service.resources.displayMetrics.density).toInt()

    private fun button(text: String, filled: Boolean, onClick: () -> Unit) = Button(service).apply {
        this.text = text; isAllCaps = false; textSize = 16f
        setTextColor(if (filled) INK else TEXT)
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            if (filled) setColor(AURORA) else { setColor(Color.TRANSPARENT); setStroke(dp(1), OUTLINE) }
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) }
        setOnClickListener { onClick() }
    }

    fun show(m: Model) {
        hide()
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(INK)
            setPadding(dp(32), dp(48), dp(32), dp(48))
            isClickable = true                                       // swallow touches so the blocked app below cannot be used
        }
        root.addView(TextView(service).apply { text = m.title; textSize = 26f; setTextColor(TEXT); gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) })
        root.addView(TextView(service).apply { text = m.message; textSize = 16f; setTextColor(MUTED); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(28)) })
        root.addView(button(m.goHome, true) { m.onGoHome() })
        m.endSession?.let { root.addView(button(it, false) { m.onEndSession() }) }
        m.emergency?.let { root.addView(button(it, false) { m.onEmergency() }) }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )
        wm.addView(root, lp)
        view = root
    }

    fun hide() {
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null
    }

    private companion object {
        val INK = Color.parseColor("#0B1020"); val AURORA = Color.parseColor("#3DDBC0")
        val TEXT = Color.parseColor("#E8ECFF"); val MUTED = Color.parseColor("#9AA6D0"); val OUTLINE = Color.parseColor("#3A4672")
    }
}
