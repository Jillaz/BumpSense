package ru.razrabozavr.bumpsense.data.settings

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.razrabozavr.bumpsense.presentation.settings.SettingsState

/**
 * Менеджер для управления настройками приложения.
 *
 * ✅ РЕФАКТОРИНГ (Этап 4): Вынесено из MapViewModel для улучшения структуры кода.
 * - Инкапсулирует работу с AppPreferences
 * - Управляет состоянием настроек (SettingsState)
 * - Может быть протестирован независимо от ViewModel
 */
class SettingsManager(private val appPreferences: AppPreferences) {

    private val _settingsState = MutableStateFlow(
        SettingsState(
            isDarkTheme = appPreferences.isDarkTheme,
            gpsIntervalMs = appPreferences.gpsIntervalMs,
            updateRadiusMeters = appPreferences.updateRadiusMeters,
            accelerometerThreshold = appPreferences.accelerometerThreshold,
            autoSaveIntervalMinutes = appPreferences.autoSaveIntervalMinutes,
            minUpdateDistanceMeters = appPreferences.minUpdateDistanceMeters
        )
    )
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(appPreferences.isDarkTheme)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun updateDarkTheme(isDark: Boolean) {
        appPreferences.isDarkTheme = isDark
        _settingsState.update { it.copy(isDarkTheme = isDark) }
        _isDarkTheme.value = isDark
        Log.d("SettingsManager", "🌓 Тема изменена: ${if (isDark) "тёмная" else "светлая"}")
    }

    fun updateMinUpdateDistance(meters: Float) {
        appPreferences.minUpdateDistanceMeters = meters
        _settingsState.update { it.copy(minUpdateDistanceMeters = meters) }
        Log.d("SettingsManager", "📏 Мин. смещение изменено на $meters м")
    }

    fun updateGpsInterval(intervalMs: Long) {
        appPreferences.gpsIntervalMs = intervalMs
        _settingsState.update { it.copy(gpsIntervalMs = intervalMs) }
        Log.d("SettingsManager", "⏱️ Интервал GPS изменён на $intervalMs мс")
    }

    fun updateRadius(radius: Double) {
        appPreferences.updateRadiusMeters = radius
        _settingsState.update { it.copy(updateRadiusMeters = radius) }
        Log.d("SettingsManager", "🎯 Радиус обновления изменён на $radius м")
    }

    fun updateAccelerometerThreshold(threshold: Float) {
        appPreferences.accelerometerThreshold = threshold
        _settingsState.update { it.copy(accelerometerThreshold = threshold) }
        Log.d("SettingsManager", "📊 Порог акселерометра изменён на $threshold")
    }

    fun updateAutoSaveInterval(minutes: Int) {
        appPreferences.autoSaveIntervalMinutes = minutes
        _settingsState.update { it.copy(autoSaveIntervalMinutes = minutes) }
        Log.d("SettingsManager", "⏰ Интервал автосохранения изменён на $minutes мин")
    }
}