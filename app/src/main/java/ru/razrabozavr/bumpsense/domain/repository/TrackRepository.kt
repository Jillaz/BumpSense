package ru.razrabozavr.bumpsense.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

interface TrackRepository {
    fun getAllTracks(): Flow<List<Track>>
    suspend fun getTrackById(id: Long): Track?
    suspend fun insertTrack(track: Track): Long
    suspend fun updateTrack(track: Track)
    suspend fun deleteTrack(id: Long)

    // Новый метод: обновить bumpIndex ближайших точек
    suspend fun updateNearbyPoints(latitude: Double, longitude: Double, bumpIndex: Int, radiusMeters: Double = 10.0)
}