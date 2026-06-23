package ru.razrabozavr.bumpsense.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.razrabozavr.bumpsense.data.local.dao.TrackDao
import ru.razrabozavr.bumpsense.data.local.mapper.toDomain
import ru.razrabozavr.bumpsense.data.local.mapper.toEntity
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository

class TrackRepositoryImpl(
    private val trackDao: TrackDao
) : TrackRepository {

    override fun getAllTracks(): Flow<List<Track>> {
        return trackDao.getAllTracks().map { entities ->
            entities.map { entity ->
                val points = trackDao.getTrackPoints(entity.id)
                entity.toDomain(points)
            }
        }
    }

    override suspend fun getTrackById(id: Long): Track? {
        val entity = trackDao.getTrackById(id) ?: return null
        val points = trackDao.getTrackPoints(id)
        return entity.toDomain(points)
    }

    override suspend fun insertTrack(track: Track): Long {
        return trackDao.insertTrackWithPoints(track.toEntity(), track.points.map { it.toEntity() })
    }

    override suspend fun updateTrack(track: Track) {
        trackDao.updateTrack(track.toEntity())
    }

    override suspend fun deleteTrack(track: Track) {
        trackDao.deleteTrack(track.toEntity())
    }

    override suspend fun deleteTrackById(id: Long) {
        trackDao.deleteTrackById(id)
    }
}