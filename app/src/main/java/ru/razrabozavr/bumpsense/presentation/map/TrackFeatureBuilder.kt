package ru.razrabozavr.bumpsense.presentation.map

import android.graphics.Color
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ru.razrabozavr.bumpsense.domain.model.BumpLevel
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

/**
 * Builder для создания GeoJSON features из точек трека.
 *
 * ✅ ОПТИМИЗАЦИЯ:
 * - Поддержка упрощения геометрии для длинных треков
 * - Сохранение ключевых точек с высоким bumpIndex
 */
object TrackFeatureBuilder {

    // Порог для применения упрощения (треки с меньшим количеством точек не упрощаются)
    private const val SIMPLIFICATION_THRESHOLD = 500

    /**
     * Создаёт список цветных линий из точек трека.
     * Каждая линия соединяет две последовательные точки.
     * Цвет линии определяется уровнем неровности (bumpIndex).
     *
     * @param points Точки трека
     * @param simplify Применять ли упрощение геометрии (для истории треков)
     */
    fun createColoredLineFeatures(
        points: List<TrackPoint>,
        simplify: Boolean = false
    ): List<Feature> {
        if (points.size < 2) return emptyList()

        // ✅ ОПТИМИЗАЦИЯ: Применяем упрощение только для длинных треков
        val processedPoints = if (simplify && points.size > SIMPLIFICATION_THRESHOLD) {
            val simplified = GeometrySimplifier.simplify(points)
            // Если упрощение не дало эффекта — возвращаем исходные точки
            if (simplified.size >= points.size * 0.8) points else simplified
        } else {
            points
        }

        if (processedPoints.size < 2) return emptyList()

        return (0 until processedPoints.size - 1).map { i ->
            val startPoint = processedPoints[i]
            val endPoint = processedPoints[i + 1]
            createLineFeature(startPoint, endPoint)
        }
    }

    /**
     * Создаёт одну линию между двумя точками.
     */
    private fun createLineFeature(start: TrackPoint, end: TrackPoint): Feature {
        val lineString = LineString.fromLngLats(
            listOf(
                Point.fromLngLat(start.longitude, start.latitude),
                Point.fromLngLat(end.longitude, end.latitude)
            )
        )
        val colorHex = getColorHex(start.bumpIndex)
        return Feature.fromGeometry(lineString).apply {
            addStringProperty("color", colorHex)
        }
    }

    /**
     * Преобразует bumpIndex в HEX цвет.
     */
    private fun getColorHex(bumpIndex: Int): String {
        val bumpLevel = BumpLevel.fromIndex(bumpIndex)
        val colorInt = Color.rgb(
            (bumpLevel.color.red * 255).toInt(),
            (bumpLevel.color.green * 255).toInt(),
            (bumpLevel.color.blue * 255).toInt()
        )
        return String.format("#%06X", (0xFFFFFF and colorInt))
    }
}