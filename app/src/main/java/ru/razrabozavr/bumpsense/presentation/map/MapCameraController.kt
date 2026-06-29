package ru.razrabozavr.bumpsense.presentation.map

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.domain.model.Track

/**
 * Контроллер управления камерой карты.
 * Отвечает за:
 * - Центрирование камеры на текущей локации
 * - Авто-следование за пользователем
 * - Debounce для движения камеры
 * - Фильтрацию видимых треков на основе camera bounds
 */
class MapCameraController {

    private val _centerTrigger = MutableStateFlow(0)
    val centerTrigger: StateFlow<Int> = _centerTrigger.asStateFlow()

    private val _autoFollow = MutableStateFlow(true)
    val autoFollow: StateFlow<Boolean> = _autoFollow.asStateFlow()

    private val _cameraBounds = MutableStateFlow<CameraBounds?>(null)
    val cameraBounds: StateFlow<CameraBounds?> = _cameraBounds.asStateFlow()

    private val _cameraBoundsForTracks = MutableStateFlow<CameraBounds?>(null)
    val cameraBoundsForTracks: StateFlow<CameraBounds?> = _cameraBoundsForTracks.asStateFlow()

    private var cameraDebounceJob: Job? = null

    /**
     * Центрирует камеру на текущей локации пользователя.
     * Инкрементирует триггер, на который реагирует UI.
     */
    fun centerOnCurrentLocation() {
        Log.d("MapCameraController", "🎯 centerOnCurrentLocation")
        _centerTrigger.value += 1
    }

    /**
     * Устанавливает режим авто-следования за пользователем.
     */
    fun setAutoFollow(enabled: Boolean) {
        Log.d("MapCameraController", "🔄 setAutoFollow: $enabled")
        _autoFollow.value = enabled
    }

    /**
     * Обрабатывает движение камеры.
     * Обновляет cameraBounds и запускает debounce для обновления видимых треков.
     */
    fun onCameraMove(bounds: CameraBounds, scope: CoroutineScope) {
        _cameraBounds.value = bounds

        // Debounce для обновления видимых треков
        cameraDebounceJob?.cancel()
        cameraDebounceJob = scope.launch {
            delay(300)
            _cameraBoundsForTracks.value = bounds
            Log.d("MapCameraController", "📷 Camera bounds обновлены после debounce")
        }
    }

    /**
     * Фильтрует треки по видимой области карты.
     * Возвращает список треков, у которых хотя бы одна точка попадает в bounds.
     */
    fun filterVisibleTracks(tracks: List<Track>, bounds: CameraBounds): List<Track> {
        val visibleTracks = tracks.filter { track ->
            track.points.any { point ->
                point.latitude in bounds.minLat..bounds.maxLat &&
                        point.longitude in bounds.minLon..bounds.maxLon
            }
        }

        Log.d("MapCameraController", "👁️ Видимых треков: ${visibleTracks.size} из ${tracks.size}")

        return visibleTracks
    }

    /**
     * Очищает ресурсы (отменяет debounce job).
     * Вызывать при очистке ViewModel.
     */
    fun cleanup() {
        Log.d("MapCameraController", "🧹 cleanup")
        cameraDebounceJob?.cancel()
        cameraDebounceJob = null
    }
}