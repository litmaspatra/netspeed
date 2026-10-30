package com.litmaspatra.netspeed

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        val svc = Intent(this, SpeedService::class.java)
        val tv = TextView(this).apply { textSize = 18f; setPadding(48, 96, 48, 48) }
        val btn = Button(this)

        fun render() {
            val on = prefs.getBoolean("on", true)
            tv.text = if (on) "Speed meter is running." else "Speed meter is stopped."
            btn.text = if (on) "Stop" else "Start"
        }

        btn.setOnClickListener {
            val on = !prefs.getBoolean("on", true)
            prefs.edit().putBoolean("on", on).apply()
            if (on) startForegroundService(svc) else stopService(svc)
            render()
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv)
            addView(btn)
        })

        if (prefs.getBoolean("on", true)) startForegroundService(svc)
        render()
    }
}
