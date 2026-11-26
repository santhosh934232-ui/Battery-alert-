package com.batteryalert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that listens for battery changes (ACTION_BATTERY_CHANGED can only be
 * received by a running component, never by a manifest receiver) and plays the alert once per
 * discharge cycle.
 */
class BatteryMonitorService : Service() {

    private lateinit var settings: AlertSettings
    private lateinit var player: AlertPlayer
    private lateinit var notificationManager: NotificationManager

    private var receiverRegistered = false
    private var lastPercent = -1

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleBattery(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = AlertSettings(this)
        player = AlertPlayer(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called every time the service is started through startForegroundService().
        startInForeground()

        if (intent?.action == ACTION_STOP_ALERT) {
            player.stop()
            notificationManager.cancel(ID_ALERT)
        }

        if (!settings.enabled) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky: Intent? = if (!receiverRegistered) {
            receiverRegistered = true
            ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(null, filter)
        }
        sticky?.let { handleBattery(it) }

        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
        player.stop()
        super.onDestroy()
    }

    private fun handleBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return

        val percent = level * 100 / scale
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val threshold = settings.threshold

        if (percent != lastPercent) {
            lastPercent = percent
            notificationManager.notify(ID_ONGOING, buildOngoingNotification(percent))
        }

        if (percent > threshold) {
            // Charged back above the threshold: re-arm for the next discharge cycle.
            if (!settings.armed) {
                settings.armed = true
                notificationManager.cancel(ID_ALERT)
            }
        } else if (settings.armed && !plugged) {
            settings.armed = false
            triggerAlert(percent)
        }
    }

    private fun triggerAlert(percent: Int) {
        player.play(settings.soundUri, settings.durationSeconds)

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, BatteryMonitorService::class.java).setAction(ACTION_STOP_ALERT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(getString(R.string.alert_title))
            .setContentText(getString(R.string.alert_text, percent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .addAction(0, getString(R.string.alert_dismiss), stopIntent)
            .build()
        notificationManager.notify(ID_ALERT, notification)
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ID_ONGOING, buildOngoingNotification(lastPercent), type)
    }

    private fun buildOngoingNotification(percent: Int): Notification {
        val text = if (percent >= 0) {
            getString(R.string.ongoing_text_with_level, settings.threshold, percent)
        } else {
            getString(R.string.ongoing_text, settings.threshold)
        }
        return NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(getString(R.string.ongoing_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createChannels() {
        val monitor = NotificationChannel(
            CHANNEL_MONITOR, getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }

        // The sound is played by AlertPlayer, so the channel itself stays silent.
        val alert = NotificationChannel(
            CHANNEL_ALERT, getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(monitor)
        notificationManager.createNotificationChannel(alert)
    }

    companion object {
        const val ACTION_STOP_ALERT = "com.batteryalert.app.action.STOP_ALERT"
        private const val CHANNEL_MONITOR = "battery_monitor"
        private const val CHANNEL_ALERT = "battery_alert"
        private const val ID_ONGOING = 1
        private const val ID_ALERT = 2
    }
}
