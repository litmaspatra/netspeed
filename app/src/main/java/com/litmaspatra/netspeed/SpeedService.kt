package com.litmaspatra.netspeed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
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

    /** Returns a pair of (formatted number with one decimal when needed, unit string "KB/s" or "MB/s") */
    private fun fmt(b: Long): Pair<String, String> = when {
        b >= 1_000_000 -> Pair(String.format(Locale.US, "%.1f", b / 1e6), "MB/s")
        b >= 1000 -> Pair(String.format(Locale.US, "%.1f", b / 1e3), "KB/s")
        else -> Pair(b.toString(), "B/s")
    }

    private fun build(down: Long, up: Long): Notification {
        val (downNum, downUnit) = fmt(down)
        val (upNum, upUnit) = fmt(up)

        // Build RemoteViews for a compact custom layout
        val rv = RemoteViews(packageName, R.layout.notification_speed)
        rv.setTextViewText(R.id.tvSpeed, "↓ $downNum  ↑ $upNum")
        rv.setTextViewText(R.id.tvUnit, "$downUnit / $upUnit")

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.mipmap.ic_launcher) // app icon (pixelated logo)
            .setCustomContentView(rv)           // only this view appears in the shade
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    companion object {
        const val CH = "speed"
        const val ID = 1
    }
}
