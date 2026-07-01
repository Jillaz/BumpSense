package ru.razrabozavr.bumpsense.data.location

import android.location.Location
import android.util.Log
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.data.settings.AppPreferences
import ru.razrabozavr.bumpsense.presentation.map.GpsStatus

/**
 * Менеджер для управления GPS-трекингом в UI.
 *
 * ✅ РЕФАКТОРИНГ (Этап 6): Вынесено из MapViewModel для улучшения структуры кода.
 * - Инкапсулирует работу с LocationClient
 * - Управляет lifecycle GPS (старт/стоп при foreground/background)
 * - Отключает GPS в UI во время записи (используется RecordingService)
 * - Может быть протестирован независимо от ViewModel
 */
class GpsTracker(
    private val locationClient: LocationClient,
    private val appPreferences: AppPreferences,
    private val scope: CoroutineScope
) {
    private val tag = "GpsTracker"

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _gpsStatus = MutableStateFlow(GpsStatus.SEARCHING)
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    private var locationJob: Job? = null

    /**
     * Запускает GPS-трекинг для UI.
     * Если запись идёт — GPS в UI отключён (работает через RecordingService).
     */
    fun startTracking(isRecording: Boolean) {
        if (isRecording) {
            Log.d(tag, "⏸️ GPS для UI отключён (работает через RecordingService)")
            return
        }

        if (locationJob?.isActive == true) {
            Log.d(tag, "⏸️ GPS уже работает")
            return
        }

        val interval = appPreferences.gpsIntervalMs

        locationClient.priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
        locationClient.minUpdateDistanceMeters = appPreferences.minUpdateDistanceMeters

        Log.d(
            tag,
            "🚀 Запуск GPS для UI: interval=${interval}мс, priority=BALANCED_POWER"
        )

        locationJob = scope.launch {
            try {
                locationClient.getLocationUpdates(interval).collect { location ->
                    Log.d(tag, "📍 GPS обновление: ${location.latitude}, ${location.longitude}")
                    _currentLocation.value = location
                    _gpsStatus.value = GpsStatus.FOUND
                }
            } catch (e: SecurityException) {
                Log.e(tag, "❌ Нет разрешения на GPS", e)
                _gpsStatus.value = GpsStatus.UNAVAILABLE
            } catch (e: Exception) {
                Log.e(tag, "❌ Ошибка GPS", e)
            }
        }
    }

    /**
     * Останавливает GPS-трекинг.
     */
    fun stopTracking() {
        locationJob?.cancel()
        locationJob = null
        Log.d(tag, "⏸️ GPS остановлен")
    }

    /**
     * Вызывается при возврате приложения в foreground.
     */
    fun onLifecycleStart(isRecording: Boolean) {
        Log.d(tag, "▶️ App foreground — запускаем GPS для карты")
        startTracking(isRecording)
    }

    /**
     * Вызывается при переходе приложения в background.
     */
    fun onLifecycleStop(isRecording: Boolean) {
        if (!isRecording) {
            stopTracking()
            Log.d(tag, "⏸️ GPS остановлен (приложение в фоне)")
        } else {
            Log.d(tag, "⏸️ Приложение в фоне, но запись идёт")
        }
    }

    /**
     * Обновляет минимальное смещение для GPS.
     */
    fun setMinUpdateDistance(meters: Float) {
        locationClient.minUpdateDistanceMeters = meters
    }

    /**
     * Перезапускает GPS с новыми настройками (например, после изменения интервала).
     */
    fun restart(isRecording: Boolean) {
        stopTracking()
        startTracking(isRecording)
    }

    /**
     * Принудительно обновляет текущую локацию (например, из Broadcast сервиса).
     */
    fun forceLocationUpdate(location: Location) {
        _currentLocation.value = location
        _gpsStatus.value = GpsStatus.FOUND
    }

    /**
     * Устанавливает статус GPS (например, UNAVAILABLE при отсутствии разрешений).
     */
    fun setStatus(status: GpsStatus) {
        _gpsStatus.value = status
    }

    /**
     * Освобождает ресурсы.
     */
    fun release() {
        stopTracking()
    }
}