package ru.razrabozavr.bumpsense.data.mapper

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

object GeoJsonMapper {

    fun tracksToGeoJson(tracks: List<Track>): String {
        val featureCollection = JSONObject()
        featureCollection.put("type", "FeatureCollection")

        val features = JSONArray()
        tracks.forEach { track ->
            val feature = JSONObject()
            feature.put("type", "Feature")

            val properties = JSONObject()
            properties.put("trackName", track.name)
            properties.put("startTime", track.startTime)
            properties.put("endTime", track.endTime)
            properties.put("distance", track.distance)
            feature.put("properties", properties)

            val geometry = JSONObject()
            geometry.put("type", "LineString")

            val coordinates = JSONArray()
            track.points.forEach { point ->
                val coord = JSONArray()
                coord.put(point.longitude)
                coord.put(point.latitude)
                coord.put(point.bumpIndex)
                coordinates.put(coord)
            }
            geometry.put("coordinates", coordinates)
            feature.put("geometry", geometry)

            features.put(feature)
        }

        featureCollection.put("features", features)
        return featureCollection.toString(2)
    }

    fun trackToGeoJson(track: Track): String {
        return tracksToGeoJson(listOf(track))
    }

    fun geoJsonToTracks(jsonString: String): List<Track> {
        try {
            val root = JSONObject(jsonString)
            val type = root.optString("type", "")

            Log.d("GeoJsonMapper", "📥 Тип JSON: $type")

            return when (type) {
                "FeatureCollection" -> {
                    val features = root.getJSONArray("features")
                    Log.d("GeoJsonMapper", "📥 FeatureCollection с ${features.length()} features")

                    val tracks = mutableListOf<Track>()
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val track = parseFeatureToTrack(feature)
                        if (track != null) {
                            tracks.add(track)
                            Log.d("GeoJsonMapper", "✅ Распаршен трек #${i+1}: ${track.points.size} точек")
                        } else {
                            Log.w("GeoJsonMapper", "⚠️ Не удалось распарсить трек #${i+1}")
                        }
                    }
                    tracks
                }
                "Feature" -> {
                    Log.d("GeoJsonMapper", "📥 Одиночная Feature")
                    val track = parseFeatureToTrack(root)
                    if (track != null) listOf(track) else emptyList()
                }
                else -> {
                    Log.e("GeoJsonMapper", "❌ Неизвестный тип: $type")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("GeoJsonMapper", "❌ Ошибка парсинга JSON: ${e.message}", e)
            return emptyList()
        }
    }

    private fun parseFeatureToTrack(feature: JSONObject): Track? {
        return try {
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val geometry = feature.getJSONObject("geometry")

            val trackName = properties.optString("trackName", "Imported_Track")
            val startTime = properties.optLong("startTime", System.currentTimeMillis())
            val endTime = if (properties.has("endTime")) properties.optLong("endTime") else null
            val distance = properties.optDouble("distance", 0.0)

            val coordinates = geometry.getJSONArray("coordinates")
            val points = mutableListOf<TrackPoint>()

            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                val longitude = coord.getDouble(0)
                val latitude = coord.getDouble(1)
                val bumpIndex = if (coord.length() > 2) coord.getInt(2) else 0

                // ✅ ИСПРАВЛЕНИЕ: Валидация координат
                if (!isValidCoordinate(latitude, longitude)) {
                    Log.w("GeoJsonMapper", "⚠️ Некорректные координаты в точке #$i: lat=$latitude, lon=$longitude")
                    continue
                }

                points.add(
                    TrackPoint(
                        id = 0,
                        trackId = 0,
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = startTime + (i * 2000L),
                        bumpIndex = bumpIndex,
                        speed = 0f
                    )
                )
            }

            if (points.isEmpty()) {
                Log.w("GeoJsonMapper", "⚠️ Трек не содержит валидных точек")
                return null
            }

            Track(
                id = 0,
                name = trackName,
                startTime = startTime,
                endTime = endTime,
                distance = distance,
                points = points
            )
        } catch (e: Exception) {
            Log.e("GeoJsonMapper", "❌ Ошибка парсинга Feature: ${e.message}", e)
            null
        }
    }

    // ✅ НОВЫЙ МЕТОД: Валидация координат
    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    fun geoJsonToTrack(jsonString: String): Track? {
        return geoJsonToTracks(jsonString).firstOrNull()
    }
}