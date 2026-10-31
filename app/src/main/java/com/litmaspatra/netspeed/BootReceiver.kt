package com.litmaspatra.netspeed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val on = context.getSharedPreferences("p", Context.MODE_PRIVATE).getBoolean("on", true)
        if (on && Settings.canDrawOverlays(context)) {
            context.startForegroundService(Intent(context, SpeedService::class.java))
        }
    }
}
