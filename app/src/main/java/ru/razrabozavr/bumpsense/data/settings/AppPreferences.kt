package ru.razrabozavr.bumpsense.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bumpsense_settings", Context.MODE_PRIVATE)

    var isDarkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, false)
        set(value) = prefs.edit { putBoolean(KEY_DARK_THEME, value) }

    var gpsIntervalMs: Long
        get() = prefs.getLong(KEY_GPS_INTERVAL, 2000L)
        set(value) = prefs.edit { putLong(KEY_GPS_INTERVAL, value) }

    // ✅ Исправлено: Double хранится как Long через toBits/fromBits
    var updateRadiusMeters: Double
        get() = Double.fromBits(prefs.getLong(KEY_UPDATE_RADIUS, 10.0.toBits()))
        set(value) = prefs.edit { putLong(KEY_UPDATE_RADIUS, value.toBits()) }

    var accelerometerThreshold: Float
        get() = prefs.getFloat(KEY_ACCEL_THRESHOLD, 5.0f)
        set(value) = prefs.edit { putFloat(KEY_ACCEL_THRESHOLD, value) }

    companion object {
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_GPS_INTERVAL = "gps_interval"
        private const val KEY_UPDATE_RADIUS = "update_radius"
        private const val KEY_ACCEL_THRESHOLD = "accelerometer_threshold"
    }
}