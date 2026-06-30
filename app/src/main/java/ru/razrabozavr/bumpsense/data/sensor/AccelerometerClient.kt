package ru.razrabozavr.bumpsense.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

class AccelerometerClient(context: Context) {
    // ✅ ИСПРАВЛЕНИЕ: Используем applicationContext для предотвращения утечек памяти
    private val sensorManager = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    // ✅ ИСПРАВЛЕНИЕ: Оптимальная частота опроса 15Hz (66666 мкс)
    // SENSOR_DELAY_FASTEST (~200Hz) избыточен для расчёта неровностей
    // 15Hz достаточно для детекции ям и неровностей на скорости до 120 км/ч
    private val sampleRateUs: Int = 1_000_000 / 15  // 66666 мкс ≈ 15Hz

    fun getAccelerationUpdates(): Flow<Float> = callbackFlow {
        if (accelerometer == null) {
            close(IllegalStateException("Accelerometer not available"))
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                        val x = it.values[0]
                        val y = it.values[1]
                        val z = it.values[2]
                        val magnitude = sqrt(x * x + y * y + z * z)
                        trySend(magnitude)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // ✅ ИСПРАВЛЕНИЕ: Используем оптимальную частоту 15Hz вместо SENSOR_DELAY_FASTEST
        sensorManager.registerListener(listener, accelerometer, sampleRateUs)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}