package ru.razrabozavr.bumpsense.presentation.map

/**
 * Константы для работы с картой MapLibre
 */
object MapConstants {
    // Источники данных GeoJSON
    const val CURRENT_TRACK_SOURCE_ID = "current-track-source"
    const val HISTORY_TRACK_SOURCE_ID = "history-track-source"

    // Слои для отображения треков
    const val CURRENT_TRACK_LAYER_ID = "current-track-layer"
    const val HISTORY_TRACK_LAYER_ID = "history-track-layer"

    // Параметры стиля
    const val STYLE_URI = "asset://osm_style.json"
    const val DEFAULT_ZOOM = 16.0
    const val BOUNDS_PADDING = 50
    const val ANIMATION_DURATION_MS = 1000
}