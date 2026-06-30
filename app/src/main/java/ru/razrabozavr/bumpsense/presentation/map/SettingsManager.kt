package ru.razrabozavr.bumpsense.presentation.map

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.razrabozavr.bumpsense.BumpSenseApp

data class SettingsState(
    val isDarkTheme: Boolean = false,
    val gpsIntervalSeconds: Int = 2,
    val updateRadiusMeters: Double = 10.0,
    val accelerometerThreshold: Float = 2.0f,
    val autoSaveIntervalMinutes: Int = 5,
    val minUpdateDistanceMeters: Float = 5.0f
)

/**
 * Менеджер настроек приложения.
 */
class SettingsManager(context: Context) {
    private val appPreferences = (context.applicationContext as BumpSenseApp).appPreferences

    private val _settingsState = MutableStateFlow(loadSettings())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _isSettingsMode = MutableStateFlow(false)
    val isSettingsMode: StateFlow<Boolean> = _isSettingsMode.asStateFlow()

    private fun loadSettings(): SettingsState {
        return SettingsState(
            isDarkTheme = appPreferences.isDarkTheme,
            gpsIntervalSeconds = (appPreferences.gpsUpdateIntervalMs / 1000).toInt(),
            updateRadiusMeters = appPreferences.updateRadiusMeters,
            accelerometerThreshold = appPreferences.accelerometerThreshold,
            autoSaveIntervalMinutes = appPreferences.autoSaveIntervalMinutes,
            minUpdateDistanceMeters = appPreferences.minUpdateDistanceMeters
        )
    }

    fun enterSettingsMode() {
        _isSettingsMode.value = true
    }

    fun exitSettingsMode() {
        _isSettingsMode.value = false
    }

    fun updateDarkTheme(isDark: Boolean) {
        appPreferences.isDarkTheme = isDark
        _settingsState.value = _settingsState.value.copy(isDarkTheme = isDark)
    }

    fun updateGpsInterval(seconds: Int) {
        appPreferences.gpsUpdateIntervalMs = seconds * 1000L
        _settingsState.value = _settingsState.value.copy(gpsIntervalSeconds = seconds)
    }

    fun updateRadius(meters: Double) {
        appPreferences.updateRadiusMeters = meters
        _settingsState.value = _settingsState.value.copy(updateRadiusMeters = meters)
    }

    fun updateAccelerometerThreshold(threshold: Float) {
        appPreferences.accelerometerThreshold = threshold
        _settingsState.value = _settingsState.value.copy(accelerometerThreshold = threshold)
    }

    fun updateAutoSaveInterval(minutes: Int) {
        appPreferences.autoSaveIntervalMinutes = minutes
        _settingsState.value = _settingsState.value.copy(autoSaveIntervalMinutes = minutes)
    }

    fun updateMinUpdateDistance(meters: Float) {
        appPreferences.minUpdateDistanceMeters = meters
        _settingsState.value = _settingsState.value.copy(minUpdateDistanceMeters = meters)
    }
}