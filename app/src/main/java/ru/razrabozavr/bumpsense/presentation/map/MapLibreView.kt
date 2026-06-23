package ru.razrabozavr.bumpsense.presentation.map

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import ru.razrabozavr.bumpsense.domain.model.BumpLevel
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    currentTrackPoints: List<TrackPoint>,
    historyTracks: List<List<TrackPoint>>,
    onMapReady: (MapLibreMap) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                onCreate(null)
                getMapAsync { mapLibreMap ->
                    mapLibreMap.setStyle(Style.Builder().fromUri(getOsmStyleUrl())) { style ->
                        initializeMapLayers(style)
                        enableLocationComponent(this, mapLibreMap, style)
                        onMapReady(mapLibreMap)
                    }
                }
            }
        },
        update = { mapView ->
            mapView.getMapAsync { mapLibreMap ->
                updateTrackLayers(mapLibreMap, currentTrackPoints, historyTracks)
            }
        }
    )
}

private fun getOsmStyleUrl(): String {
    // Бесплатный стиль MapLibre на основе OpenStreetMap
    return "https://demotiles.maplibre.org/style.json"
}

private fun initializeMapLayers(style: Style) {
    // Создаем источники для треков
    style.addSource(GeoJsonSource(CURRENT_TRACK_SOURCE_ID))
    style.addSource(GeoJsonSource(HISTORY_TRACK_SOURCE_ID))

    // Слой для исторических треков (полупрозрачный)
    style.addLayer(
        LineLayer(HISTORY_TRACK_LAYER_ID, HISTORY_TRACK_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(Color.GRAY),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineOpacity(0.6f)
        )
    )

    // Слой для текущего трека (яркий)
    style.addLayer(
        LineLayer(CURRENT_TRACK_LAYER_ID, CURRENT_TRACK_SOURCE_ID).withProperties(
            PropertyFactory.lineWidth(8f),
            PropertyFactory.lineOpacity(1f)
        )
    )
}

private fun enableLocationComponent(
    mapView: MapView,
    mapLibreMap: MapLibreMap,
    style: Style
) {
    try {
        val locationComponent = mapLibreMap.locationComponent

        // Активация LocationComponent
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(mapView.context, style)
                .useDefaultLocationEngine(true)
                .build()
        )

        // Включаем отображение местоположения
        locationComponent.isLocationComponentEnabled = true

        // Настройка камеры - следование за пользователем
        locationComponent.cameraMode = CameraMode.TRACKING

        // Настройка отображения маркера местоположения
        locationComponent.renderMode = RenderMode.COMPASS

        // Настройки UI - используем синтаксис свойств Kotlin
        mapLibreMap.uiSettings.apply {
            isCompassEnabled = true
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
    }
}

private fun updateTrackLayers(
    mapLibreMap: MapLibreMap,
    currentTrackPoints: List<TrackPoint>,
    historyTracks: List<List<TrackPoint>>
) {
    mapLibreMap.getStyle { style ->
        // Обновляем текущий трек
        val currentTrackSource = style.getSourceAs<GeoJsonSource>(CURRENT_TRACK_SOURCE_ID)
        if (currentTrackPoints.size > 1) {
            val features = createColoredLineFeatures(currentTrackPoints)
            currentTrackSource?.setGeoJson(FeatureCollection.fromFeatures(features))
        } else {
            currentTrackSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }

        // Обновляем исторические треки
        val historyTrackSource = style.getSourceAs<GeoJsonSource>(HISTORY_TRACK_SOURCE_ID)
        val historyFeatures = historyTracks.flatMap { points ->
            if (points.size > 1) createColoredLineFeatures(points) else emptyList()
        }
        historyTrackSource?.setGeoJson(FeatureCollection.fromFeatures(historyFeatures))
    }
}

private fun createColoredLineFeatures(points: List<TrackPoint>): List<Feature> {
    val features = mutableListOf<Feature>()

    for (i in 0 until points.size - 1) {
        val start = points[i]
        val end = points[i + 1]

        val lineString = LineString.fromLngLats(
            listOf(
                Point.fromLngLat(start.longitude, start.latitude),
                Point.fromLngLat(end.longitude, end.latitude)
            )
        )

        val bumpLevel = BumpLevel.fromIndex(start.bumpIndex)
        val color = Color.rgb(
            (bumpLevel.color.red * 255).toInt(),
            (bumpLevel.color.green * 255).toInt(),
            (bumpLevel.color.blue * 255).toInt()
        )

        val feature = Feature.fromGeometry(lineString)
        feature.addStringProperty("color", String.format("#%06X", (0xFFFFFF and color)))
        features.add(feature)
    }

    return features
}

// Константы для идентификаторов источников и слоёв
private const val CURRENT_TRACK_SOURCE_ID = "current-track-source"
private const val HISTORY_TRACK_SOURCE_ID = "history-track-source"
private const val CURRENT_TRACK_LAYER_ID = "current-track-layer"
private const val HISTORY_TRACK_LAYER_ID = "history-track-layer"