package ru.razrabozavr.bumpsense.data.sensor

import android.location.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.razrabozavr.bumpsense.data.location.LocationClient
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

/**
 * Собирает данные из GPS и акселерометра, вычисляет индекс неровности
 * и создает TrackPoint для каждой GPS-точки.
 */
class RecordingDataCollector(
    private val locationClient: LocationClient,
    private val accelerometerClient: AccelerometerClient,
    private val bumpIndexCalculator: BumpIndexCalculator
) {

    /**
     * Запускает сбор данных из GPS и акселерометра.
     * Для каждой GPS-точки вычисляется текущий индекс неровности на основе
     * данных акселерометра за последний интервал.
     */
    fun collectTrackPoints(
        locationIntervalMs: Long = 2000L
    ): Flow<TrackPoint> {
        var currentBumpIndex = 0
        var lastTrackId = 0L

        val locationFlow = locationClient.getLocationUpdates(locationIntervalMs)
        val accelerationFlow = accelerometerClient.getAccelerationUpdates()

        return combine(locationFlow, accelerationFlow) { location, acceleration ->
            // Обновляем индекс неровности на основе нового сэмпла акселерометра
            currentBumpIndex = bumpIndexCalculator.addSample(acceleration)

            // Создаем TrackPoint для каждой GPS-точки
            TrackPoint(
                id = 0,
                trackId = lastTrackId,
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = location.time,
                bumpIndex = currentBumpIndex,
                speed = location.speed
            )
        }
    }

    fun setTrackId(trackId: Long) {
        lastTrackId = trackId
    }

    fun reset() {
        bumpIndexCalculator.reset()
    }

    private var lastTrackId: Long = 0L
}