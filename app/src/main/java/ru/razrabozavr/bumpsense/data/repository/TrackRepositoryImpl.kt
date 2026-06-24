package ru.razrabozavr.bumpsense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.razrabozavr.bumpsense.data.local.dao.TrackDao
import ru.razrabozavr.bumpsense.data.local.entity.TrackEntity
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
        // Обновляем метаданные трека
        val trackEntity = track.toEntity()
        trackDao.updateTrack(trackEntity)

        // ✅ ПРАВИЛЬНО: Удаляем ТОЛЬКО точки, а не весь трек
        trackDao.deletePointsByTrackId(track.id)

        // Вставляем обновленные точки
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
        val allTracks = getAllTracks().first()

        val updatedTracks = allTracks.map { track ->
            val updatedPoints = track.points.map { point ->
                val distance = calculateDistance(
                    point.latitude, point.longitude,
                    latitude, longitude
                )

                if (distance <= radiusMeters) {
                    point.copy(bumpIndex = bumpIndex)
                } else {
                    point
                }
            }
            track.copy(points = updatedPoints)
        }

        updatedTracks.forEach { track ->
            updateTrack(track)
        }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}