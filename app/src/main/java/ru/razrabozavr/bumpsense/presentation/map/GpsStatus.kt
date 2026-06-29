package ru.razrabozavr.bumpsense.presentation.map

/**
 * Статус GPS для отображения в UI.
 */
enum class GpsStatus {
    /** GPS доступен и есть сигнал */
    AVAILABLE,

    /** GPS ищет спутники */
    SEARCHING,

    /** GPS выключен или недоступен */
    UNAVAILABLE
}