package ru.razrabozavr.bumpsense.presentation.map

import android.location.Location
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

/**
 * Состояние UI для экрана карты.
 *
 * ✅ РЕФАКТОРИНГ (Этап 2): Вынесено из MapViewModel.kt для улучшения структуры кода.
 */
data class MapUiState(
    val isRecording: Boolean = false,
    val isHistoryVisible: Boolean = true,
    val currentLocation: Location? = null,
    val currentTrackPoints: List<TrackPoint> = emptyList(),
    val historyTracks: List<List<TrackPoint>> = emptyList(),
    val locationPermissionGranted: Boolean = false,
    val gpsStatus: GpsStatus = GpsStatus.SEARCHING,
    val snackbarMessage: String? = null,
    // ✅ ИСПРАВЛЕНИЕ (Вариант К): Состояния для индикатора прогресса
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val progressMessage: String? = null
)

/**
 * Статус GPS-приёмника.
 */
enum class GpsStatus {
    SEARCHING,
    FOUND,
    UNAVAILABLE
}

/**
 * Границы видимой области карты.
 * Используется для фильтрации треков в режиме редактирования.
 */
data class CameraBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)