package ru.razrabozavr.bumpsense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.razrabozavr.bumpsense.data.local.dao.TrackDao
import ru.razrabozavr.bumpsense.data.local.mapper.toDomain
import ru.razrabozavr.bumpsense.data.local.mapper.toEntity
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TrackRepositoryImpl(private val trackDao: TrackDao) : TrackRepository {

    override fun getAllTracks(): Flow<List<Track>> {
        return trackDao.getAllTracks().map { trackEntities ->
            trackEntities.map { trackEntity ->
                val points = trackDao.getTrackPoints(trackEntity.id)
                trackEntity.toDomain(points)
            }
        }
    }

    override suspend fun getTrackById(id: Long): Track? {
        val trackEntity = trackDao.getTrackById(id) ?: return null
        val points = trackDao.getTrackPoints(id)
        return trackEntity.toDomain(points)
    }

    override suspend fun insertTrack(track: Track): Long {
        val trackEntity = track.toEntity()
        val trackId = trackDao.insertTrack(trackEntity)

        if (track.points.isNotEmpty()) {
            val pointsWithTrackId = track.points.map {
                it.copy(trackId = trackId).toEntity()
            }
            trackDao.insertTrackPoints(pointsWithTrackId)
        }

        return trackId
    }

    override suspend fun updateTrack(track: Track) {
        trackDao.updateTrack(track.toEntity())
        trackDao.deletePointsByTrackId(track.id)

        if (track.points.isNotEmpty()) {
            val pointsWithTrackId = track.points.map {
                it.copy(trackId = track.id).toEntity()
            }
            trackDao.insertTrackPoints(pointsWithTrackId)
        }
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

    override suspend fun clearDatabase() {
        // Сначала удаляем точки (из-за FOREIGN KEY CASCADE)
        trackDao.deleteAllTrackPoints()
        // Затем удаляем треки
        trackDao.deleteAllTracks()
    }
}