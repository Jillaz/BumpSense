package ru.razrabozavr.bumpsense.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.razrabozavr.bumpsense.data.local.entity.TrackEntity
import ru.razrabozavr.bumpsense.data.local.entity.TrackPointEntity

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY id DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    suspend fun getTrackPoints(trackId: Long): List<TrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoint(point: TrackPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Delete
    suspend fun deleteTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    // ===== МЕТОДЫ ДЛЯ УПРАВЛЕНИЯ ТОЧКАМИ =====

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deletePointsByTrackId(trackId: Long)

    @Query("SELECT * FROM track_points WHERE latitude = :lat AND longitude = :lon AND trackId = :trackId")
    suspend fun getPointByCoords(lat: Double, lon: Double, trackId: Long): TrackPointEntity?

    @Query("UPDATE track_points SET bumpIndex = :bumpIndex WHERE id = :pointId")
    suspend fun updatePointBumpIndex(pointId: Long, bumpIndex: Int)

    // ===== МЕТОДЫ ДЛЯ ОБНОВЛЕНИЯ БЛИЖАЙШИХ ТОЧЕК =====

    @Query("""
        SELECT * FROM track_points 
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLon AND :maxLon
    """)
    suspend fun getPointsInBoundingBox(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): List<TrackPointEntity>

    // ===== ОЧИСТКА БАЗЫ ДАННЫХ =====

    @Query("DELETE FROM track_points")
    suspend fun deleteAllTrackPoints()

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()

    @Transaction
    suspend fun insertTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>): Long {
        val trackId = insertTrack(track)
        val pointsWithTrackId = points.map { it.copy(trackId = trackId) }
        insertTrackPoints(pointsWithTrackId)
        return trackId
    }
}