package ru.razrabozavr.bumpsense.data.local.mapper

import ru.razrabozavr.bumpsense.data.local.entity.TrackEntity
import ru.razrabozavr.bumpsense.data.local.entity.TrackPointEntity
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

fun TrackEntity.toDomain(points: List<TrackPointEntity> = emptyList()): Track {
    return Track(
        id = id,
        name = name,
        startTime = startTime,
        endTime = endTime,
        distance = distance,
        points = points.map { it.toDomain() }
    )
}

fun TrackPointEntity.toDomain(): TrackPoint {
    return TrackPoint(
        id = id,
        trackId = trackId,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        bumpIndex = bumpIndex,
        speed = speed
    )
}

fun Track.toEntity(): TrackEntity {
    return TrackEntity(
        id = id,
        name = name,
        startTime = startTime,
        endTime = endTime,
        distance = distance
    )
}

fun TrackPoint.toEntity(): TrackPointEntity {
    return TrackPointEntity(
        id = id,
        trackId = trackId,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        bumpIndex = bumpIndex,
        speed = speed
    )
}