package ru.razrabozavr.bumpsense.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

interface TrackRepository {
    fun getAllTracks(): Flow<List<Track>>
    suspend fun getTrackById(id: Long): Track?
    suspend fun insertTrack(track: Track): Long

    // ✅ НОВЫЙ МЕТОД (Вариант З): Batch insert треков в одной транзакции
    suspend fun insertTracksBatch(tracks: List<Track>)

    suspend fun updateTrack(track: Track)
    suspend fun deleteTrack(id: Long)
    suspend fun updateNearbyPoints(
        latitude: Double,
        longitude: Double,
        bumpIndex: Int,
        radiusMeters: Double = 10.0
    )
    suspend fun clearDatabase()

    // Batch insert точек без удаления старых
    suspend fun insertPoints(trackId: Long, points: List<TrackPoint>)

    // Обновление метаданных трека без пересохранения точек
    suspend fun updateTrackMetadata(trackId: Long, endTime: Long?, distance: Double)
}