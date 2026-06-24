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

    // ===== ТРЕКИ =====

    @Query("SELECT * FROM tracks ORDER BY startTime DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Delete
    suspend fun deleteTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    // ===== ТОЧКИ ТРЕКА =====

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    suspend fun getTrackPoints(trackId: Long): List<TrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoint(point: TrackPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deletePointsByTrackId(trackId: Long)

    // ===== ОПТИМИЗИРОВАННЫЕ МЕТОДЫ ДЛЯ ОБНОВЛЕНИЯ БЛИЖАЙШИХ ТОЧЕК =====

    /**
     * Быстрая фильтрация точек по bounding box (прямоугольнику).
     * Отбирает только кандидатов в заданном радиусе, чтобы не загружать все точки из БД.
     *
     * 1 градус широты ≈ 111 км
     * 1 градус долготы ≈ 111 км * cos(широта)
     */
    @Query("""
        SELECT * FROM track_points 
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLon AND :maxLon
    """)
    suspend fun getPointsInBoundingBox(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): List<TrackPointEntity>

    /**
     * Обновляет только bumpIndex для конкретной точки по ID.
     * Используется после точного расчета расстояния гаверсинусами.
     */
    @Query("UPDATE track_points SET bumpIndex = :bumpIndex WHERE id = :pointId")
    suspend fun updatePointBumpIndex(pointId: Long, bumpIndex: Int)

    // ===== ТРАНЗАКЦИИ =====

    @Transaction
    suspend fun insertTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>): Long {
        val trackId = insertTrack(track)
        val pointsWithTrackId = points.map { it.copy(trackId = trackId) }
        insertTrackPoints(pointsWithTrackId)
        return trackId
    }

    // ===== ОЧИСТКА БАЗЫ ДАННЫХ =====

    /**
     * Удаляет все точки треков.
     * Вызывается ПЕРЕД deleteAllTracks() из-за внешнего ключа с CASCADE.
     */
    @Query("DELETE FROM track_points")
    suspend fun deleteAllTrackPoints()

    /**
     * Удаляет все треки.
     */
    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
}