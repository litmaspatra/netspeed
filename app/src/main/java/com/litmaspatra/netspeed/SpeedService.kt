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
            NotificationChannel(CH, "Internet speed", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
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

    // One compact line in the shade; the real display is the status bar icon.
    private fun build(down: Long, up: Long): Notification {
        val (num, unit) = Speed.fmt(down)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH)
            .setSmallIcon(icon(num, unit))
            .setContentTitle("↓ ${Speed.full(down)}   ↑ ${Speed.full(up)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setContentIntent(open)
            .build()
    }

    // Wide white-on-transparent bitmap: big number on top, big unit below.
    // Both lines are scaled to fill the full width so "KB/s" stays readable.
    private fun icon(num: String, unit: String): Icon {
        val w = 160
        val h = 96
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        p.textSize = 100f
        p.textSize = minOf(100f * w / p.measureText(num), 66f)
        c.drawText(num, w / 2f, 54f, p)
        p.textSize = 100f
        p.textSize = minOf(100f * w / p.measureText(unit), 42f)
        c.drawText(unit, w / 2f, 94f, p)
        return Icon.createWithBitmap(bmp)
    }

    companion object {
        const val CH = "speed"
        const val ID = 1
    }
}
