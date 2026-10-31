package com.litmaspatra.netspeed

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Draws the speed as a small overlay on top of the status bar (like the clock).
 * Runs as an accessibility service so no notification is needed.
 */
class SpeedOverlayService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var num: TextView
    private lateinit var unit: TextView
    private var lastRx = 0L
    private var lastT = 0L

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes()
            val dt = (now - lastT).coerceAtLeast(1)
            val (n, u) = Speed.fmt((rx - lastRx).coerceAtLeast(0) * 1000 / dt)
            num.text = n
            unit.text = u
            lastRx = rx; lastT = now
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label() = TextView(this).apply {
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        setShadowLayer(3f, 0f, 0f, 0x99000000.toInt())
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        statusBarHeight(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(prefs.getInt("pos", 250))
        y = 0
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun applySize() {
        val s = prefs.getInt("size", 100) / 100f
        num.textSize = 13f * s
        unit.textSize = 9f * s
    }

    override fun onServiceConnected() {
        prefs = getSharedPreferences("p", MODE_PRIVATE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        num = label()
        unit = label()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(num)
            addView(unit)
        }
        applySize()
        wm.addView(root, params())
        prefs.registerOnSharedPreferenceChangeListener(this)
        lastRx = TrafficStats.getTotalRxBytes()
        lastT = SystemClock.elapsedRealtime()
        handler.post(tick)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        applySize()
        wm.updateViewLayout(root, params())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::root.isInitialized) wm.updateViewLayout(root, params())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (::root.isInitialized) wm.removeView(root)
        super.onDestroy()
    }
}
