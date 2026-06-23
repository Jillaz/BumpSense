package ru.razrabozavr.bumpsense.domain.model

data class Track(
    val id: Long = 0,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val distance: Double = 0.0,
    val points: List<TrackPoint> = emptyList()
)