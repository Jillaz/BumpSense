package ru.razrabozavr.bumpsense.data.sensor

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.data.location.LocationClient
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

/**
 * Собирает данные из GPS и акселерометра.
 * Эмитирует TrackPoint только при получении новой GPS-точки,
 * используя усредненное значение акселерометра за интервал.
 */
class RecordingDataCollector(
    private val locationClient: LocationClient,
    private val accelerometerClient: AccelerometerClient,
    private val bumpIndexCalculator: BumpIndexCalculator
) {

    private var lastTrackId: Long = 0L

    fun setTrackId(trackId: Long) {
        lastTrackId = trackId
    }

    /**
     * Запускает сбор данных из GPS и акселерометра.
     * Для каждой GPS-точки вычисляется текущий индекс неровности на основе
     * данных акселерометра за последний интервал.
     */
    fun collectTrackPoints(
        locationIntervalMs: Long = 2000L
    ): Flow<TrackPoint> = channelFlow {
        var currentBumpIndex = 0
        var accelerationSum = 0.0
        var accelerationCount = 0

        // Запускаем сбор данных акселерометра в фоне
        val accelerometerJob = launch {
            accelerometerClient.getAccelerationUpdates().collect { acceleration ->
                // Добавляем сэмпл в калькулятор
                currentBumpIndex = bumpIndexCalculator.addSample(acceleration)

                // Накапливаем для усреднения
                accelerationSum += acceleration
                accelerationCount++

                Log.d("BumpSense", "📊 Акселерометр: magnitude=$acceleration, bumpIndex=$currentBumpIndex")
            }
        }

        // Собираем GPS точки
        try {
            locationClient.getLocationUpdates(locationIntervalMs).collect { location ->
                // Вычисляем среднее ускорение за интервал
                val avgAcceleration = if (accelerationCount > 0) {
                    accelerationSum / accelerationCount
                } else {
                    0f
                }

                // Сбрасываем накопители
                accelerationSum = 0.0
                accelerationCount = 0

                Log.d("BumpSense", "📍 GPS точка: lat=${location.latitude}, lon=${location.longitude}, bumpIndex=$currentBumpIndex")

                // Создаем TrackPoint
                val trackPoint = TrackPoint(
                    id = 0,
                    trackId = lastTrackId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = location.time,
                    bumpIndex = currentBumpIndex,
                    speed = location.speed
                )

                // Эмитируем точку
                send(trackPoint)
                Log.d("BumpSense", "✅ TrackPoint отправлен: bumpIndex=${trackPoint.bumpIndex}")
            }
        } catch (e: Exception) {
            Log.e("BumpSense", "❌ Ошибка в collectTrackPoints", e)
            throw e
        } finally {
            accelerometerJob.cancel()
        }
    }
}