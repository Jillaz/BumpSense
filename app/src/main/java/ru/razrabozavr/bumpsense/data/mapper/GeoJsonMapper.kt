package ru.razrabozavr.bumpsense.data.mapper

import android.util.Log
import java.io.Writer
import org.json.JSONArray
import org.json.JSONObject
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

/**
 * Маппер для конвертации треков в GeoJSON и обратно.
 *
 * ✅ ОПТИМИЗАЦИЯ (Вариант З):
 * - Потоковая запись через Writer (не формирует всю строку в памяти)
 * - Оптимизированный парсинг без избыточных логов
 * - Batch операции для импорта
 */
object GeoJsonMapper {

    // ✅ ОПТИМИЗАЦИЯ: Потоковая запись GeoJSON напрямую в Writer
    // Не формирует огромную строку в памяти
    fun tracksToGeoJson(tracks: List<Track>, writer: Writer) {
        writer.write("{\n")
        writer.write("  \"type\": \"FeatureCollection\",\n")
        writer.write("  \"features\": [\n")

        tracks.forEachIndexed { trackIndex, track ->
            writer.write("    {\n")
            writer.write("      \"type\": \"Feature\",\n")
            writer.write("      \"properties\": {\n")
            writer.write("        \"trackName\": ${escapeJsonString(track.name)},\n")
            writer.write("        \"startTime\": ${track.startTime},\n")
            if (track.endTime != null) {
                writer.write("        \"endTime\": ${track.endTime},\n")
            }
            writer.write("        \"distance\": ${track.distance}\n")
            writer.write("      },\n")
            writer.write("      \"geometry\": {\n")
            writer.write("        \"type\": \"LineString\",\n")
            writer.write("        \"coordinates\": [\n")

            track.points.forEachIndexed { pointIndex, point ->
                val comma = if (pointIndex < track.points.size - 1) "," else ""
                writer.write("          [${point.longitude}, ${point.latitude}, ${point.bumpIndex}]$comma\n")
            }

            writer.write("        ]\n")
            writer.write("      }\n")

            val trackComma = if (trackIndex < tracks.size - 1) "," else ""
            writer.write("    }$trackComma\n")
        }

        writer.write("  ]\n")
        writer.write("}\n")
        writer.flush()
    }

    // ✅ Совместимость: старый метод возвращает строку (через StringWriter)
    fun tracksToGeoJson(tracks: List<Track>): String {
        val stringWriter = java.io.StringWriter()
        tracksToGeoJson(tracks, stringWriter)
        return stringWriter.toString()
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

                    // ✅ ОПТИМИЗАЦИЯ: Предвыделение размера списка
                    val tracks = ArrayList<Track>(features.length())
                    var parsedCount = 0
                    var failedCount = 0

                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val track = parseFeatureToTrack(feature)
                        if (track != null) {
                            tracks.add(track)
                            parsedCount++
                        } else {
                            failedCount++
                        }
                    }

                    // ✅ ОПТИМИЗАЦИЯ: Один лог вместо N
                    Log.d(
                        "GeoJsonMapper",
                        "✅ Распаршено треков: $parsedCount, ошибок: $failedCount"
                    )
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

            // ✅ ОПТИМИЗАЦИЯ: Предвыделение размера списка точек
            val points = ArrayList<TrackPoint>(coordinates.length())
            var skippedPointsCount = 0

            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)

                if (coord.length() < 2) {
                    skippedPointsCount++
                    continue
                }

                val longitude = coord.getDouble(0)
                val latitude = coord.getDouble(1)
                val bumpIndex = if (coord.length() > 2) coord.getInt(2) else 0

                if (!isValidCoordinate(latitude, longitude)) {
                    skippedPointsCount++
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
                Log.w("GeoJsonMapper", "⚠️ Трек '$trackName' не содержит валидных точек")
                return null
            }

            if (skippedPointsCount > 0) {
                Log.w(
                    "GeoJsonMapper",
                    "⚠️ Трек '$trackName': пропущено $skippedPointsCount некорректных точек"
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
            Log.e("GeoJsonMapper", "❌ Ошибка парсинга Feature: ${e.message}", e)
            null
        }
    }

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    // ✅ НОВЫЙ МЕТОД: Экранирование строк для JSON
    private fun escapeJsonString(value: String): String {
        val sb = StringBuilder("\"")
        for (char in value) {
            when (char) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        sb.append("\\u${String.format("%04x", char.code)}")
                    } else {
                        sb.append(char)
                    }
                }
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    fun geoJsonToTrack(jsonString: String): Track? {
        return geoJsonToTracks(jsonString).firstOrNull()
    }
}