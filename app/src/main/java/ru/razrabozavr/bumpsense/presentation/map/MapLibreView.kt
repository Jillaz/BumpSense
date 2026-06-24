package ru.razrabozavr.bumpsense.presentation.map

import android.graphics.Color
import android.location.Location
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
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
    currentLocation: Location? = null,
    centerTrigger: Int = 0,
    onMapReady: (MapLibreMap) -> Unit = {}
) {
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            Log.d("BumpSense", "️ Создание MapView...")
            MapView(context).apply {
                onCreate(null)
                getMapAsync { map ->
                    Log.d("BumpSense", "🗺️ MapView создан, загрузка стиля из assets...")

                    map.setStyle(Style.Builder().fromUri("asset://osm_style.json")) { loadedStyle ->
                        Log.d("BumpSense", "✅ Стиль OSM загружен успешно")
                        initializeMapLayers(loadedStyle)
                        enableLocationComponent(this, map, loadedStyle)
                        mapLibreMap = map
                        onMapReady(map)

                        Log.d("BumpSense", "✅ Карта готова к использованию")
                    }
                }
            }
        },
        update = { mapView ->
            mapView.getMapAsync { map ->
                updateTrackLayers(map, currentTrackPoints, historyTracks)
            }
        }
    )

    // Автоматическое центрирование при получении локации
    LaunchedEffect(currentLocation?.latitude, currentLocation?.longitude) {
        val map = mapLibreMap
        if (map != null && currentLocation != null) {
            Log.d("BumpSense", " Авто-центрирование: ${currentLocation.latitude}, ${currentLocation.longitude}")
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(currentLocation.latitude, currentLocation.longitude))
                .zoom(16.0)
                .build()
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                1000
            )
        } else {
            Log.d("BumpSense", "⏸️ Авто-центрирование пропущено: map=$map, location=$currentLocation")
        }
    }

    // Ручное центрирование по кнопке
    LaunchedEffect(centerTrigger) {
        if (centerTrigger > 0) {
            Log.d("BumpSense", "👆 Кнопка центрирования нажата (trigger=$centerTrigger)")

            val map = mapLibreMap
            if (map == null) {
                Log.e("BumpSense", "❌ Карта еще не инициализирована")
                return@LaunchedEffect
            }

            if (currentLocation == null) {
                Log.e("BumpSense", " Местоположение еще не получено (GPS ищет...)")
                return@LaunchedEffect
            }

            Log.d("BumpSense", "🎯 Ручное центрирование: ${currentLocation.latitude}, ${currentLocation.longitude}")
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(currentLocation.latitude, currentLocation.longitude))
                .zoom(16.0)
                .build()
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                1000
            )
        }
    }
}

private fun initializeMapLayers(style: Style) {
    try {
        style.addSource(GeoJsonSource(CURRENT_TRACK_SOURCE_ID))
        style.addSource(GeoJsonSource(HISTORY_TRACK_SOURCE_ID))

        style.addLayer(
            LineLayer(HISTORY_TRACK_LAYER_ID, HISTORY_TRACK_SOURCE_ID).withProperties(
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineColor(Expression.get("color"))
            )
        )

        style.addLayer(
            LineLayer(CURRENT_TRACK_LAYER_ID, CURRENT_TRACK_SOURCE_ID).withProperties(
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineOpacity(1f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineColor(Expression.get("color"))
            )
        )

        Log.d("BumpSense", "✅ Слои треков инициализированы")
    } catch (e: Exception) {
        Log.e("BumpSense", "❌ Ошибка инициализации слоев", e)
    }
}

private fun enableLocationComponent(
    mapView: MapView,
    mapLibreMap: MapLibreMap,
    style: Style
) {
    try {
        val locationComponent = mapLibreMap.locationComponent

        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(mapView.context, style)
                .useDefaultLocationEngine(true)
                .build()
        )

        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.NONE
        locationComponent.renderMode = RenderMode.COMPASS

        mapLibreMap.uiSettings.apply {
            isCompassEnabled = true
            isZoomGesturesEnabled = true
            isScrollGesturesEnabled = true
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = true
        }

        Log.d("BumpSense", "✅ LocationComponent активирован")
    } catch (e: SecurityException) {
        Log.e("BumpSense", "❌ Нет разрешения на местоположение", e)
    } catch (e: Exception) {
        Log.e("BumpSense", "❌ Ошибка активации LocationComponent", e)
    }
}

private fun updateTrackLayers(
    mapLibreMap: MapLibreMap,
    currentTrackPoints: List<TrackPoint>,
    historyTracks: List<List<TrackPoint>>
) {
    mapLibreMap.getStyle { style ->
        try {
            val currentTrackSource = style.getSourceAs<GeoJsonSource>(CURRENT_TRACK_SOURCE_ID)
            if (currentTrackPoints.size > 1) {
                val features = createColoredLineFeatures(currentTrackPoints)
                currentTrackSource?.setGeoJson(FeatureCollection.fromFeatures(features))
            } else {
                currentTrackSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            }

            val historyTrackSource = style.getSourceAs<GeoJsonSource>(HISTORY_TRACK_SOURCE_ID)
            val historyFeatures = historyTracks.flatMap { points ->
                if (points.size > 1) createColoredLineFeatures(points) else emptyList()
            }
            historyTrackSource?.setGeoJson(FeatureCollection.fromFeatures(historyFeatures))
        } catch (e: Exception) {
            Log.e("BumpSense", "❌ Ошибка обновления слоев", e)
        }
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
        val colorInt = Color.rgb(
            (bumpLevel.color.red * 255).toInt(),
            (bumpLevel.color.green * 255).toInt(),
            (bumpLevel.color.blue * 255).toInt()
        )
        val colorHex = String.format("#%06X", (0xFFFFFF and colorInt))

        val feature = Feature.fromGeometry(lineString)
        feature.addStringProperty("color", colorHex)
        features.add(feature)
    }

    return features
}

private const val CURRENT_TRACK_SOURCE_ID = "current-track-source"
private const val HISTORY_TRACK_SOURCE_ID = "history-track-source"
private const val CURRENT_TRACK_LAYER_ID = "current-track-layer"
private const val HISTORY_TRACK_LAYER_ID = "history-track-layer"