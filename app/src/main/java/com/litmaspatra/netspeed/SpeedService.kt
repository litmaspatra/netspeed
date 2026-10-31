package com.litmaspatra.netspeed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Draws the download speed as a small overlay at the top of the screen, in the
 * status bar area. The foreground-service notification is required by Android
 * but is created at minimum importance (and is not shown at all when
 * notifications are not allowed for the app).
 */
class SpeedService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

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
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(prefs.getInt("pos", 250))
        y = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun applySize() {
        val s = prefs.getInt("size", 100) / 100f
        num.textSize = 13f * s
        unit.textSize = 9f * s
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Speed meter", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle("NetSpeed is running")
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, notification())
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!::root.isInitialized) {
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
            try {
                wm.addView(root, params())
            } catch (e: RuntimeException) {
                stopSelf()
                return START_NOT_STICKY
            }
            prefs.registerOnSharedPreferenceChangeListener(this)
        }
        lastRx = TrafficStats.getTotalRxBytes()
        lastT = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        applySize()
        wm.updateViewLayout(root, params())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::root.isInitialized) wm.updateViewLayout(root, params())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (::root.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            try { wm.removeView(root) } catch (_: RuntimeException) {}
        }
        super.onDestroy()
    }

    companion object {
        const val CH = "speed"
        const val ID = 1
    }
}
