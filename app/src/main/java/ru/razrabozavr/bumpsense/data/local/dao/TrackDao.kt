package ru.razrabozavr.bumpsense.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.razrabozavr.bumpsense.data.local.entity.TrackEntity
import ru.razrabozavr.bumpsense.data.local.entity.TrackPointEntity
import ru.razrabozavr.bumpsense.data.local.mapper.toDomain
import ru.razrabozavr.bumpsense.domain.model.Track

@Dao
interface TrackDao {
    // ✅ НОВОЕ: Загрузка всех треков с точками одним запросом (решение N+1)
    @Transaction
    @Query("SELECT * FROM tracks ORDER BY id DESC")
    fun getAllTracksWithPoints(): Flow<List<TrackWithPoints>>

    // ✅ НОВОЕ: Загрузка одного трека с точками одним запросом
    @Transaction
    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackByIdWithPoints(id: Long): TrackWithPoints?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deletePointsByTrackId(trackId: Long)

    @Query("UPDATE track_points SET bumpIndex = :bumpIndex WHERE id = :pointId")
    suspend fun updatePointBumpIndex(pointId: Long, bumpIndex: Int)

    @Query("""
        SELECT * FROM track_points
        WHERE latitude BETWEEN :minLat AND :maxLat
        AND longitude BETWEEN :minLon AND :maxLon
    """)
    suspend fun getPointsInBoundingBox(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): List<TrackPointEntity>

    @Query("DELETE FROM track_points")
    suspend fun deleteAllTrackPoints()

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()

    // ✅ ИСПРАВЛЕНИЕ (Вариант Б): Обновление метаданных трека без пересохранения точек
    @Query("UPDATE tracks SET endTime = :endTime, distance = :distance WHERE id = :trackId")
    suspend fun updateTrackMetadata(trackId: Long, endTime: Long?, distance: Double)

    // ✅ НОВОЕ: Атомарная вставка трека с точками
    @Transaction
    suspend fun insertTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>): Long {
        val trackId = insertTrack(track)
        val pointsWithTrackId = points.map { it.copy(trackId = trackId) }
        insertTrackPoints(pointsWithTrackId)
        return trackId
    }

    // ✅ НОВОЕ: Атомарное обновление трека с точками
    @Transaction
    suspend fun updateTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>) {
        updateTrack(track)
        deletePointsByTrackId(track.id)
        if (points.isNotEmpty()) {
            val pointsWithTrackId = points.map { it.copy(trackId = track.id) }
            insertTrackPoints(pointsWithTrackId)
        }
    }
}

// ✅ НОВЫЙ КЛАСС: Для загрузки трека с точками одним запросом
data class TrackWithPoints(
    @Embedded val track: TrackEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "trackId"
    )
    val points: List<TrackPointEntity>
) {
    fun toDomain(): Track {
        return track.toDomain(points)
    }
}