package ru.razrabozavr.bumpsense.presentation.map

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository

/**
 * Менеджер управления списком треков.
 * Отвечает за:
 * - Загрузку треков из репозитория
 * - Удаление треков
 * - Экспорт/импорт треков в GeoJSON формат
 * - Хранение состояния загрузки
 */
class TrackListManager(
    private val trackRepository: TrackRepository,
    private val geoJsonMapper: GeoJsonMapper
) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var trackLoadingJob: Job? = null

    /**
     * Загружает все треки из репозитория.
     * Подписывается на Flow и автоматически обновляет состояние при изменениях в БД.
     */
    fun loadTracks(scope: CoroutineScope) {
        Log.d("TrackListManager", "📥 loadTracks")

        trackLoadingJob?.cancel()
        trackLoadingJob = scope.launch {
            _isLoading.value = true

            try {
                trackRepository.getAllTracks().collect { tracks ->
                    Log.d("TrackListManager", "✅ Загружено треков: ${tracks.size}")
                    _tracks.value = tracks
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("TrackListManager", "❌ Ошибка загрузки треков", e)
                _isLoading.value = false
            }
        }
    }

    /**
     * Удаляет трек по ID.
     */
    fun deleteTrack(trackId: Long, scope: CoroutineScope) {
        Log.d("TrackListManager", "🗑️ deleteTrack: $trackId")
        scope.launch {
            try {
                trackRepository.deleteTrack(trackId)
                Log.d("TrackListManager", "✅ Трек удалён: $trackId")
            } catch (e: Exception) {
                Log.e("TrackListManager", "❌ Ошибка удаления трека", e)
            }
        }
    }

    /**
     * Экспортирует трек в GeoJSON формат.
     * @return JSON строка или пустая строка при ошибке
     */
    fun exportTrack(track: Track): String {
        Log.d("TrackListManager", "📤 exportTrack: ${track.name}")
        return try {
            geoJsonMapper.trackToGeoJson(track)
        } catch (e: Exception) {
            Log.e("TrackListManager", "❌ Ошибка экспорта трека", e)
            ""
        }
    }

    /**
     * Импортирует трек из GeoJSON строки.
     * @return true если импорт успешен, false иначе
     */
    fun importTrack(jsonString: String, scope: CoroutineScope): Boolean {
        Log.d("TrackListManager", "📥 importTrack")
        return try {
            val track: Track? = geoJsonMapper.geoJsonToTrack(jsonString)
            if (track != null) {
                scope.launch {
                    trackRepository.insertTrack(track)
                    Log.d("TrackListManager", "✅ Трек импортирован: ${track.name}")
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("TrackListManager", "❌ Ошибка импорта трека", e)
            false
        }
    }

    /**
     * Очищает ресурсы (отменяет job загрузки).
     */
    fun cleanup() {
        Log.d("TrackListManager", "🧹 cleanup")
        trackLoadingJob?.cancel()
        trackLoadingJob = null
    }
}