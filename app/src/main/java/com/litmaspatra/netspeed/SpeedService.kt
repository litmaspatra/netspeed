package com.litmaspatra.netspeed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.util.Locale

class SpeedService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastT = 0L

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val dt = (now - lastT).coerceAtLeast(1)
            val down = (rx - lastRx).coerceAtLeast(0) * 1000 / dt
            val up = (tx - lastTx).coerceAtLeast(0) * 1000 / dt
            lastRx = rx; lastTx = tx; lastT = now
            nm.notify(ID, build(down, up))
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Internet speed", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build(0, 0)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        lastT = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun fmt(b: Long): Pair<String, String> = when {
        b >= 10_000_000 -> Pair(String.format(Locale.US, "%.0f", b / 1e6), "M")
        b >= 1_000_000 -> Pair(String.format(Locale.US, "%.1f", b / 1e6), "M")
        b >= 1000 -> Pair((b / 1000).toString(), "K")
        else -> Pair(b.toString(), "B")
    }

    private fun full(b: Long): String {
        val (n, u) = fmt(b)
        return "$n ${u}${if (u == "B") "" else "B"}/s"
    }

    private fun build(down: Long, up: Long): Notification {
        val (num, unit) = fmt(down)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH)
            .setSmallIcon(icon(num, unit))
            .setContentTitle("↓ ${full(down)}")
            .setContentText("↑ ${full(up)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    // Status bar icons are alpha masks, so draw white text on a transparent bitmap.
    private fun icon(num: String, unit: String): Icon {
        val s = 96
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = 60f
        }
        val w = p.measureText(num)
        if (w > s) p.textSize = 60f * s / w
        c.drawText(num, s / 2f, 52f, p)
        p.textSize = 36f
        c.drawText(unit, s / 2f, 90f, p)
        return Icon.createWithBitmap(bmp)
    }

    companion object {
        const val CH = "speed"
        const val ID = 1
    }
}
