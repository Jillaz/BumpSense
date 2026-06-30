package ru.razrabozavr.bumpsense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.razrabozavr.bumpsense.data.local.dao.TrackDao
import ru.razrabozavr.bumpsense.data.local.mapper.toEntity
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TrackRepositoryImpl(private val trackDao: TrackDao) : TrackRepository {
    // ✅ ИСПРАВЛЕНИЕ: Загружаем треки с точками одним запросом (нет N+1)
    override fun getAllTracks(): Flow<List<Track>> {
        return trackDao.getAllTracksWithPoints().map { tracksWithPoints ->
            tracksWithPoints.map { it.toDomain() }
        }
    }

    // ✅ ИСПРАВЛЕНИЕ: Загружаем трек с точками одним запросом
    override suspend fun getTrackById(id: Long): Track? {
        return trackDao.getTrackByIdWithPoints(id)?.toDomain()
    }

    // ✅ ИСПРАВЛЕНИЕ: Вставляем трек и точки в одной транзакции
    override suspend fun insertTrack(track: Track): Long {
        val trackEntity = track.toEntity()
        val pointsWithTrackId = track.points.map {
            it.copy(trackId = track.id).toEntity()
        }

        return trackDao.insertTrackWithPoints(trackEntity, pointsWithTrackId)
    }

    // ✅ ИСПРАВЛЕНИЕ: Обновляем трек и точки в одной транзакции (атомарно)
    override suspend fun updateTrack(track: Track) {
        trackDao.updateTrackWithPoints(
            track.toEntity(),
            track.points.map { it.copy(trackId = track.id).toEntity() }
        )
    }

    override suspend fun deleteTrack(id: Long) {
        trackDao.deleteTrackById(id)
    }

    override suspend fun updateNearbyPoints(
        latitude: Double,
        longitude: Double,
        bumpIndex: Int,
        radiusMeters: Double
    ) {
        val latDelta = radiusMeters / 111_000.0
        val lonDelta = radiusMeters / (111_000.0 * cos(Math.toRadians(latitude)))

        val minLat = latitude - latDelta
        val maxLat = latitude + latDelta
        val minLon = longitude - lonDelta
        val maxLon = longitude + lonDelta

        val candidates = trackDao.getPointsInBoundingBox(minLat, maxLat, minLon, maxLon)

        candidates.forEach { pointEntity ->
            val distance = calculateDistance(
                pointEntity.latitude, pointEntity.longitude,
                latitude, longitude
            )

            if (distance <= radiusMeters) {
                trackDao.updatePointBumpIndex(pointEntity.id, bumpIndex)
            }
        }
    }

    override suspend fun clearDatabase() {
        trackDao.deleteAllTrackPoints()
        trackDao.deleteAllTracks()
    }

    // ✅ ИСПРАВЛЕНИЕ (Вариант Б): Batch insert точек без удаления старых
    override suspend fun insertPoints(trackId: Long, points: List<TrackPoint>) {
        if (points.isEmpty()) return

        val pointsWithTrackId = points.map {
            it.copy(trackId = trackId).toEntity()
        }
        trackDao.insertTrackPoints(pointsWithTrackId)
    }

    // ✅ ИСПРАВЛЕНИЕ (Вариант Б): Обновление метаданных трека без пересохранения точек
    override suspend fun updateTrackMetadata(trackId: Long, endTime: Long?, distance: Double) {
        trackDao.updateTrackMetadata(trackId, endTime, distance)
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}