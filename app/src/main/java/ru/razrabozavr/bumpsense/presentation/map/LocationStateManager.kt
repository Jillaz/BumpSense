package ru.razrabozavr.bumpsense.presentation.map

import android.content.Context
import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.razrabozavr.bumpsense.data.location.LocationClient

/**
 * Менеджер состояния локации пользователя.
 * Отвечает за:
 * - Получение обновлений GPS от LocationClient
 * - Хранение текущего местоположения
 * - Управление lifecycle подписки на обновления
 */
class LocationStateManager(
    private val context: Context
) {
    private var locationClient: LocationClient? = null
    private var locationJob: Job? = null

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _isLocationAvailable = MutableStateFlow(false)
    val isLocationAvailable: StateFlow<Boolean> = _isLocationAvailable.asStateFlow()

    /**
     * Запускает подписку на обновления локации.
     * Вызывать при старте ViewModel или при необходимости получать локацию.
     */
    fun start(scope: CoroutineScope) {
        if (locationJob?.isActive == true) {
            Log.d("LocationStateManager", "⚠️ Обновления локации уже запущены")
            return
        }

        Log.d("LocationStateManager", "🚀 Запуск обновлений локации")

        locationClient = LocationClient(context)

        locationJob = locationClient?.getLocationUpdates()
            ?.onEach { location ->
                Log.d(
                    "LocationStateManager",
                    "📍 Новая локация: ${location.latitude}, ${location.longitude}"
                )
                _currentLocation.value = location
                _isLocationAvailable.value = true
            }
            ?.catch { e ->
                Log.e("LocationStateManager", "❌ Ошибка получения локации", e)
                _isLocationAvailable.value = false
            }
            ?.launchIn(scope)
    }

    /**
     * Останавливает подписку на обновления локации.
     * Вызывать при очистке ViewModel.
     */
    fun stop() {
        Log.d("LocationStateManager", "⏹️ Остановка обновлений локации")

        locationJob?.cancel()
        locationJob = null

        locationClient = null
        _isLocationAvailable.value = false
    }

    /**
     * Получает последнюю известную локацию (без ожидания новых обновлений).
     */
    fun getLastKnownLocation(): Location? {
        return _currentLocation.value
    }
}