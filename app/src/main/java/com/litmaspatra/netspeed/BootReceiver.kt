package com.litmaspatra.netspeed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.getSharedPreferences("p", Context.MODE_PRIVATE).getBoolean("on", true)) {
            context.startForegroundService(Intent(context, SpeedService::class.java))
        }
    }
}
