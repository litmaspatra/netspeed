package com.litmaspatra.netspeed

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var down: TextView
    private lateinit var up: TextView
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastT = 0L

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val dt = (now - lastT).coerceAtLeast(1)
            down.text = Speed.full((rx - lastRx).coerceAtLeast(0) * 1000 / dt)
            up.text = Speed.full((tx - lastTx).coerceAtLeast(0) * 1000 / dt)
            lastRx = rx; lastTx = tx; lastT = now
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(28).toFloat()
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun button(text: String, bg: Int, fg: Int) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 16f; setTextColor(fg)
        background = pill(bg)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(12) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0E1116")
        window.navigationBarColor = Color.parseColor("#0E1116")

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val svc = Intent(this, SpeedService::class.java)

        val title = label("NetSpeed", 28f, Color.WHITE, true)
        val status = label("", 14f, Color.parseColor("#8B949E"))
        down = label("0.0 KB/s", 40f, Color.parseColor("#3DDC84"), true)
        up = label("0.0 KB/s", 22f, Color.parseColor("#FFB300"))
        val dl = label("DOWNLOAD", 12f, Color.parseColor("#8B949E"))
        val ul = label("UPLOAD", 12f, Color.parseColor("#8B949E"))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161B22")); cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(24), dp(28), dp(24), dp(28))
            addView(dl); addView(down)
            addView(ul, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) })
            addView(up)
        }

        val toggle = button("", Color.parseColor("#3DDC84"), Color.parseColor("#0E1116"))
        val settings = button("Notification settings", Color.parseColor("#21262D"), Color.WHITE)

        fun render() {
            val on = prefs.getBoolean("on", true)
            status.text = if (on) "Running — speed shows in the status bar" else "Stopped"
            toggle.text = if (on) "Stop" else "Start"
        }

        toggle.setOnClickListener {
            val on = !prefs.getBoolean("on", true)
            prefs.edit().putBoolean("on", on).apply()
            if (on) startForegroundService(svc) else stopService(svc)
            render()
        }
        settings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, SpeedService.CH)
            })
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0E1116"))
            setPadding(dp(24), dp(64), dp(24), dp(24))
            addView(title)
            addView(status, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6); bottomMargin = dp(32) })
            addView(card, LinearLayout.LayoutParams(-1, -2))
            addView(toggle)
            addView(settings)
        })

        if (prefs.getBoolean("on", true)) startForegroundService(svc)
        render()
    }

    override fun onResume() {
        super.onResume()
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        lastT = SystemClock.elapsedRealtime()
        handler.postDelayed(tick, 1000)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }
}
