package ru.razrabozavr.bumpsense.presentation.map

import android.graphics.Color
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ru.razrabozavr.bumpsense.domain.model.BumpLevel
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

/**
 * Builder для создания GeoJSON features из точек трека
 */
object TrackFeatureBuilder {

    /**
     * Создает список цветных линий из точек трека
     * Каждая линия соединяет две последовательные точки
     * Цвет линии определяется уровнем неровности (bumpIndex)
     */
    fun createColoredLineFeatures(points: List<TrackPoint>): List<Feature> {
        if (points.size < 2) return emptyList()

        return (0 until points.size - 1).map { i ->
            val startPoint = points[i]
            val endPoint = points[i + 1]

            createLineFeature(startPoint, endPoint)
        }
    }

    /**
     * Создает одну линию между двумя точками
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
     * Преобразует bumpIndex в HEX цвет
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