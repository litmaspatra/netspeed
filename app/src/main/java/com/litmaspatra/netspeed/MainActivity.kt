package com.litmaspatra.netspeed

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var down: TextView
    private lateinit var up: TextView
    private lateinit var status: TextView
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

    private fun slider(title: String, key: String, min: Int, max: Int, def: Int, unitText: String): LinearLayout {
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val head = label("", 14f, Color.WHITE).apply { gravity = Gravity.START }
        fun show(v: Int) { head.text = "$title: $v$unitText" }
        val sb = SeekBar(this).apply {
            this.min = min; this.max = max
            progress = prefs.getInt(key, def)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    show(p)
                    if (fromUser) prefs.edit().putInt(key, p).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        show(sb.progress)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)
            addView(head); addView(sb)
        }
    }

    private lateinit var grant: Button
    private lateinit var toggle: Button
    private val prefs get() = getSharedPreferences("p", MODE_PRIVATE)

    private fun refresh() {
        val ok = Settings.canDrawOverlays(this)
        val on = prefs.getBoolean("on", true)
        grant.visibility = if (ok) View.GONE else View.VISIBLE
        toggle.visibility = if (ok) View.VISIBLE else View.GONE
        toggle.text = if (on) "Stop" else "Start"
        status.text = when {
            !ok -> "Needs the Display over other apps permission"
            on -> "Running - speed is shown over the status bar"
            else -> "Stopped"
        }
        val svc = Intent(this, SpeedService::class.java)
        if (ok && on) startForegroundService(svc) else stopService(svc)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0E1116")
        window.navigationBarColor = Color.parseColor("#0E1116")

        val title = label("NetSpeed", 28f, Color.WHITE, true)
        status = label("", 14f, Color.parseColor("#8B949E"))
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
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(dl); addView(down)
            addView(ul, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
            addView(up)
        }

        grant = button("Allow display over other apps", Color.parseColor("#3DDC84"), Color.parseColor("#0E1116"))
        grant.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        toggle = button("", Color.parseColor("#3DDC84"), Color.parseColor("#0E1116"))
        toggle.setOnClickListener {
            prefs.edit().putBoolean("on", !prefs.getBoolean("on", true)).apply()
            refresh()
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            addView(title)
            addView(status, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6); bottomMargin = dp(24) })
            addView(card, LinearLayout.LayoutParams(-1, -2))
            addView(slider("Position from right edge", "pos", 0, 600, 250, " dp"))
            addView(slider("Size", "size", 50, 140, 100, " %"))
            addView(grant)
            addView(toggle)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0E1116"))
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
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
