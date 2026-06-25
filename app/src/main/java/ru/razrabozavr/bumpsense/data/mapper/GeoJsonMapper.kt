package ru.razrabozavr.bumpsense.data.mapper

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

object GeoJsonMapper {

    /**
     * Конвертирует список треков в GeoJSON FeatureCollection.
     * Каждый трек — отдельная Feature с метаданными в properties.
     * Координаты сохраняются как [longitude, latitude, bumpIndex].
     */
    fun tracksToGeoJson(tracks: List<Track>): String {
        val featureCollection = JSONObject()
        featureCollection.put("type", "FeatureCollection")

        val features = JSONArray()
        tracks.forEach { track ->
            val feature = JSONObject()
            feature.put("type", "Feature")

            // Метаданные трека
            val properties = JSONObject()
            properties.put("trackName", track.name)
            properties.put("startTime", track.startTime)
            properties.put("endTime", track.endTime)
            properties.put("distance", track.distance)
            feature.put("properties", properties)

            // Геометрия: LineString с координатами [lon, lat, bumpIndex]
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

    /**
     * Конвертирует один трек в GeoJSON FeatureCollection с одной Feature.
     * Для обратной совместимости.
     */
    fun trackToGeoJson(track: Track): String {
        return tracksToGeoJson(listOf(track))
    }

    /**
     * Парсит GeoJSON и возвращает список треков.
     * Поддерживает как FeatureCollection (несколько треков),
     * так и одиночную Feature (один трек).
     */
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
                            Log.d("GeoJsonMapper", "✅ Расаршен трек #${i+1}: ${track.points.size} точек")
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

    /**
     * Парсит одну Feature в Track.
     */
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
                // bumpIndex хранится третьим значением в массиве координат
                val bumpIndex = if (coord.length() > 2) coord.getInt(2) else 0

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

            Track(
                id = 0,
                name = trackName,
                startTime = startTime,
                endTime = endTime,
                distance = distance,
                points = points
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Устаревший метод для обратной совместимости (возвращает только первый трек).
     */
    fun geoJsonToTrack(jsonString: String): Track? {
        return geoJsonToTracks(jsonString).firstOrNull()
    }
}