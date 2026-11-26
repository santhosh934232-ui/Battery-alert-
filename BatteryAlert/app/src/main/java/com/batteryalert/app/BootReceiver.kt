package com.batteryalert.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restarts monitoring after a reboot or app update if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!AlertSettings(context).enabled) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, BatteryMonitorService::class.java))
        } catch (e: Exception) {
            // The system refused to start the service; it will be restarted when the app is opened.
        }
    }
}
