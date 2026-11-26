package com.batteryalert.app

import android.content.Context

/** Persistent user settings, stored in SharedPreferences so they survive reboots. */
class AlertSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("battery_alert_prefs", Context.MODE_PRIVATE)

    /** True when the user has switched monitoring on. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Battery percentage (1..99) at or below which the alert fires. */
    var threshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, 20).coerceIn(1, 99)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value.coerceIn(1, 99)).apply()

    /** Alert duration in seconds: 5 or 10. */
    var durationSeconds: Int
        get() = if (prefs.getInt(KEY_DURATION, 5) == 10) 10 else 5
        set(value) = prefs.edit().putInt(KEY_DURATION, if (value == 10) 10 else 5).apply()

    /** URI of the chosen sound, or null for the default alarm sound. */
    var soundUri: String?
        get() = prefs.getString(KEY_SOUND, null)
        set(value) = prefs.edit().putString(KEY_SOUND, value).apply()

    /**
     * True when the alert may fire (battery has been above the threshold since the last alert).
     * Set to false after an alert, set back to true once the battery rises above the threshold.
     */
    var armed: Boolean
        get() = prefs.getBoolean(KEY_ARMED, true)
        set(value) = prefs.edit().putBoolean(KEY_ARMED, value).apply()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_THRESHOLD = "threshold"
        const val KEY_DURATION = "duration"
        const val KEY_SOUND = "sound"
        const val KEY_ARMED = "armed"
    }
}
