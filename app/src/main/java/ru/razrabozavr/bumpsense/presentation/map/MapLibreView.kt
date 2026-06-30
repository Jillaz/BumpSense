package ru.razrabozavr.bumpsense.presentation.map

import android.location.Location
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import org.maplibre.android.geometry.LatLngBounds
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
import org.maplibre.geojson.FeatureCollection
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    currentTrackPoints: List<TrackPoint>,
    historyTracks: List<List<TrackPoint>>,
    currentLocation: Location? = null,
    centerTrigger: Int = 0,
    autoFollow: Boolean = false,
    cameraBounds: CameraBounds? = null,
    onMapReady: (MapLibreMap) -> Unit = {},
    onCameraMove: (CameraBounds) -> Unit = {}
) {
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            Log.d("BumpSense", "🗺️ Создание MapView...")
            MapView(context).apply {
                mapView = this
                onCreate(null)
                getMapAsync { map ->
                    Log.d("BumpSense", "🗺️ MapView создан, загрузка стиля из assets...")

                    map.setStyle(Style.Builder().fromUri(MapConstants.STYLE_URI)) { loadedStyle ->
                        Log.d("BumpSense", "✅ Стиль OSM загружен успешно")
                        initializeMapLayers(loadedStyle)
                        enableLocationComponent(this, map, loadedStyle)
                        mapLibreMap = map
                        onMapReady(map)

                        // Listener движения камеры
                        map.addOnCameraMoveListener {
                            try {
                                val projection = map.projection
                                val visibleRegion = projection.visibleRegion

                                val nearLeft = visibleRegion.nearLeft ?: return@addOnCameraMoveListener
                                val nearRight = visibleRegion.nearRight ?: return@addOnCameraMoveListener
                                val farLeft = visibleRegion.farLeft ?: return@addOnCameraMoveListener
                                val farRight = visibleRegion.farRight ?: return@addOnCameraMoveListener

                                val bounds = CameraBounds(
                                    minLat = minOf(
                                        nearLeft.latitude,
                                        nearRight.latitude,
                                        farLeft.latitude,
                                        farRight.latitude
                                    ),
                                    maxLat = maxOf(
                                        nearLeft.latitude,
                                        nearRight.latitude,
                                        farLeft.latitude,
                                        farRight.latitude
                                    ),
                                    minLon = minOf(
                                        nearLeft.longitude,
                                        nearRight.longitude,
                                        farLeft.longitude,
                                        farRight.longitude
                                    ),
                                    maxLon = maxOf(
                                        nearLeft.longitude,
                                        nearRight.longitude,
                                        farLeft.longitude,
                                        farRight.longitude
                                    )
                                )

                                onCameraMove(bounds)
                            } catch (e: Exception) {
                                Log.e("BumpSense", "❌ Ошибка в camera move listener", e)
                            }
                        }
                    }
                }
            }
        },
        update = { view ->
            view.getMapAsync { map ->
                updateTrackLayers(map, currentTrackPoints, historyTracks)
            }
        }
    )

    // ✅ ИСПРАВЛЕНИЕ: Управление lifecycle MapView для предотвращения утечки памяти
    DisposableEffect(Unit) {
        onDispose {
            mapView?.let { view ->
                Log.d("BumpSense", "🗑️ MapView: onDestroy")
                view.onPause()
                view.onStop()
                view.onDestroy()
            }
        }
    }

    // ✅ Вызов onResume/onStart при создании
    LaunchedEffect(Unit) {
        mapView?.let { view ->
            Log.d("BumpSense", "🔄 MapView: onStart, onResume")
            view.onStart()
            view.onResume()
        }
    }

    // Авто-центрирование только когда autoFollow = true
    LaunchedEffect(autoFollow, currentLocation?.latitude, currentLocation?.longitude) {
        if (!autoFollow) return@LaunchedEffect

        val map = mapLibreMap
        if (map != null && currentLocation != null) {
            Log.d("BumpSense", "🔄 Авто-наведение: ${currentLocation.latitude}, ${currentLocation.longitude}")
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(currentLocation.latitude, currentLocation.longitude))
                .zoom(MapConstants.DEFAULT_ZOOM)
                .build()
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                MapConstants.ANIMATION_DURATION_MS
            )
        }
    }

    // Ручное центрирование по кнопке
    LaunchedEffect(centerTrigger) {
        if (centerTrigger > 0) {
            Log.d("BumpSense", "👆 Ручное центрирование (trigger=$centerTrigger)")

            val map = mapLibreMap
            if (map == null) {
                Log.e("BumpSense", "❌ Карта не инициализирована")
                return@LaunchedEffect
            }

            if (currentLocation == null) {
                Log.e("BumpSense", "❌ Местоположение не получено")
                return@LaunchedEffect
            }

            Log.d("BumpSense", "🎯 Центрирование: ${currentLocation.latitude}, ${currentLocation.longitude}")
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(currentLocation.latitude, currentLocation.longitude))
                .zoom(MapConstants.DEFAULT_ZOOM)
                .build()
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                MapConstants.ANIMATION_DURATION_MS
            )
        }
    }

    // Центрирование на области трека
    LaunchedEffect(cameraBounds) {
        val bounds = cameraBounds ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect

        Log.d("BumpSense", "🎯 Центрирование на bounds: $bounds")

        try {
            val latLngBounds = LatLngBounds.Builder()
                .include(LatLng(bounds.minLat, bounds.minLon))
                .include(LatLng(bounds.maxLat, bounds.maxLon))
                .build()

            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(latLngBounds, MapConstants.BOUNDS_PADDING),
                MapConstants.ANIMATION_DURATION_MS
            )
        } catch (e: Exception) {
            Log.e("BumpSense", "❌ Ошибка центрирования на bounds", e)
        }
    }
}

private fun initializeMapLayers(style: Style) {
    try {
        style.addSource(GeoJsonSource(MapConstants.CURRENT_TRACK_SOURCE_ID))
        style.addSource(GeoJsonSource(MapConstants.HISTORY_TRACK_SOURCE_ID))
        style.addLayer(
            LineLayer(MapConstants.HISTORY_TRACK_LAYER_ID, MapConstants.HISTORY_TRACK_SOURCE_ID).withProperties(
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineColor(Expression.get("color"))
            )
        )

        style.addLayer(
            LineLayer(MapConstants.CURRENT_TRACK_LAYER_ID, MapConstants.CURRENT_TRACK_SOURCE_ID).withProperties(
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
            val currentTrackSource = style.getSourceAs<GeoJsonSource>(MapConstants.CURRENT_TRACK_SOURCE_ID)
            if (currentTrackPoints.size > 1) {
                val features = TrackFeatureBuilder.createColoredLineFeatures(currentTrackPoints)
                currentTrackSource?.setGeoJson(FeatureCollection.fromFeatures(features))
            } else {
                currentTrackSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            }

            val historyTrackSource = style.getSourceAs<GeoJsonSource>(MapConstants.HISTORY_TRACK_SOURCE_ID)
            val historyFeatures = historyTracks
                .filter { it.size > 1 }
                .flatMap { TrackFeatureBuilder.createColoredLineFeatures(it) }
            historyTrackSource?.setGeoJson(FeatureCollection.fromFeatures(historyFeatures))
        } catch (e: Exception) {
            Log.e("BumpSense", "❌ Ошибка обновления слоев", e)
        }
    }
}