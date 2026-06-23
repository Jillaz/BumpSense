package ru.razrabozavr.bumpsense.domain.model

data class TrackPoint(
    val id: Long = 0,
    val trackId: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val bumpIndex: Int,
    val speed: Float = 0f
)