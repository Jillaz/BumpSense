package ru.razrabozavr.bumpsense.data.mapper

import org.json.JSONArray
import org.json.JSONObject
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

object GeoJsonMapper {

    fun trackToGeoJson(track: Track): String {
        val geoJson = JSONObject()
        geoJson.put("type", "FeatureCollection")

        val features = JSONArray()
        val feature = JSONObject()
        feature.put("type", "Feature")

        // Свойства трека
        val properties = JSONObject().apply {
            put("name", track.name)
            put("startTime", track.startTime)
            if (track.endTime != null) put("endTime", track.endTime)
        }
        feature.put("properties", properties)

        // Геометрия (LineString)
        val geometry = JSONObject().apply {
            put("type", "LineString")
            val coordinates = JSONArray()
            track.points.forEach { point ->
                // Формат GeoJSON: [longitude, latitude, altitude/bumpIndex]
                coordinates.put(JSONArray().apply {
                    put(point.longitude)
                    put(point.latitude)
                    put(point.bumpIndex)
                })
            }
            put("coordinates", coordinates)
        }
        feature.put("geometry", geometry)

        features.put(feature)
        geoJson.put("features", features)

        return geoJson.toString(2) // Pretty print с отступами
    }

    fun geoJsonToTrack(jsonString: String): Track? {
        return try {
            val geoJson = JSONObject(jsonString)
            val features = geoJson.getJSONArray("features")
            if (features.length() == 0) return null

            val feature = features.getJSONObject(0)
            val properties = feature.getJSONObject("properties")
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")

            val points = mutableListOf<TrackPoint>()
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                points.add(
                    TrackPoint(
                        id = 0,
                        trackId = 0,
                        latitude = coord.getDouble(1),
                        longitude = coord.getDouble(0),
                        timestamp = 0,
                        bumpIndex = if (coord.length() > 2) coord.getInt(2) else 0,
                        speed = 0f
                    )
                )
            }

            Track(
                id = 0,
                name = properties.optString("name", "Импортированный трек"),
                startTime = properties.optLong("startTime", System.currentTimeMillis()),
                endTime = properties.opt("endTime") as? Long,
                distance = 0.0,
                points = points
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}