package ru.razrabozavr.bumpsense.data.edit

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository
import ru.razrabozavr.bumpsense.presentation.map.CameraBounds
import ru.razrabozavr.bumpsense.presentation.track.TrackEditUiState
import ru.razrabozavr.bumpsense.presentation.track.TrackListTab

/**
 * Менеджер для управления редактированием треков.
 *
 * ✅ РЕФАКТОРИНГ (Этап 5): Вынесено из MapViewModel для улучшения структуры кода.
 * ✅ ИСПРАВЛЕНИЕ: Добавлена подписка на изменения БД для автоматического обновления UI
 */
class TrackEditManager(
    private val trackRepository: TrackRepository,
    private val scope: CoroutineScope
) {
    private val _trackEditState = MutableStateFlow(TrackEditUiState())
    val trackEditState: StateFlow<TrackEditUiState> = _trackEditState.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _cameraBounds = MutableStateFlow<CameraBounds?>(null)
    val cameraBounds: StateFlow<CameraBounds?> = _cameraBounds.asStateFlow()

    private val _currentMapBounds = MutableStateFlow<CameraBounds?>(null)

    // ✅ ИСПРАВЛЕНИЕ: Подписка на изменения БД
    init {
        observeDatabaseChanges()
    }

    /**
     * ✅ ИСПРАВЛЕНИЕ: Подписываемся на все изменения треков в БД.
     * При любом изменении (добавление, удаление, импорт) автоматически обновляем UI.
     */
    private fun observeDatabaseChanges() {
        trackRepository.getAllTracks()
            .onEach { tracks ->
                Log.d("TrackEditManager", "📥 БД изменена, загружено треков: ${tracks.size}")
                _trackEditState.update { current ->
                    current.copy(
                        allTracks = tracks,
                        visibleTracks = filterTracksByVisibleArea(tracks, _currentMapBounds.value)
                    )
                }
            }
            .launchIn(scope)
    }

    fun enterEditMode() {
        _isEditMode.value = true
        Log.d("TrackEditManager", "▶️ Вход в режим редактирования")
    }

    fun exitEditMode() {
        _isEditMode.value = false
        _trackEditState.update { it.copy(focusedTrackId = null) }
        _cameraBounds.value = null
        Log.d("TrackEditManager", "◀️ Выход из режима редактирования")
    }

    fun selectTrackTab(tab: TrackListTab) {
        _trackEditState.update { it.copy(currentTab = tab) }
    }

    fun updateVisibleArea(bounds: CameraBounds?) {
        _currentMapBounds.value = bounds
        // ✅ ИСПРАВЛЕНИЕ: При изменении bounds пересчитываем видимые треки
        _trackEditState.update { current ->
            current.copy(
                visibleTracks = filterTracksByVisibleArea(current.allTracks, bounds)
            )
        }
    }

    fun focusOnTrack(trackId: Long) {
        Log.d("TrackEditManager", "🎯 Фокус на треке #$trackId")
        _trackEditState.update { it.copy(focusedTrackId = trackId) }
    }

    fun clearTrackFocus() {
        _trackEditState.update { it.copy(focusedTrackId = null) }
        _cameraBounds.value = null
    }

    /**
     * Удаляет трек по ID.
     */
    suspend fun deleteTrack(trackId: Long) {
        trackRepository.deleteTrack(trackId)
        // ✅ Состояние обновится автоматически через observeDatabaseChanges()
        Log.d("TrackEditManager", "🗑️ Трек #$trackId удалён")
    }

    /**
     * Вычисляет границы трека для центрирования карты.
     */
    fun calculateTrackBounds(points: List<TrackPoint>): CameraBounds {
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        points.forEach { point ->
            if (point.latitude < minLat) minLat = point.latitude
            if (point.latitude > maxLat) maxLat = point.latitude
            if (point.longitude < minLon) minLon = point.longitude
            if (point.longitude > maxLon) maxLon = point.longitude
        }

        val latPadding = (maxLat - minLat) * 0.1
        val lonPadding = (maxLon - minLon) * 0.1

        return CameraBounds(
            minLat = minLat - latPadding,
            maxLat = maxLat + latPadding,
            minLon = minLon - lonPadding,
            maxLon = maxLon + lonPadding
        )
    }

    private fun filterTracksByVisibleArea(
        allTracks: List<Track>,
        bounds: CameraBounds?
    ): List<Track> {
        if (bounds == null) return emptyList()

        return allTracks.filter { track ->
            track.points.any { point ->
                point.latitude in bounds.minLat..bounds.maxLat &&
                        point.longitude in bounds.minLon..bounds.maxLon
            }
        }
    }
}