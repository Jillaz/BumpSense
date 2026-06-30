package ru.razrabozavr.bumpsense.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Клиент для получения обновлений геолокации.
 * Поддерживает динамическое переключение приоритета GPS для экономии батареи.
 *
 * ✅ ИСПРАВЛЕНИЕ: Явная проверка разрешений перед запросом GPS
 */
class LocationClient(
    private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // ✅ Настраиваемый приоритет GPS
    var priority: Int = Priority.PRIORITY_HIGH_ACCURACY

    // Минимальное расстояние для обновления
    var minUpdateDistanceMeters: Float = 0f

    /**
     * ✅ ИСПРАВЛЕНИЕ: Проверяем разрешения перед запросом GPS
     */
    fun getLocationUpdates(intervalMs: Long): Flow<Location> = callbackFlow {
        // ✅ ИСПРАВЛЕНИЕ: Явная проверка разрешений
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            Log.e("LocationClient", "❌ Нет разрешений на локацию (FINE или COARSE)")
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(minUpdateDistanceMeters)
            .build()

        Log.d(
            "LocationClient",
            "🛰️ Запуск GPS: interval=${intervalMs}ms, priority=${priorityToString(priority)}, minDistance=${minUpdateDistanceMeters}m"
        )

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location).isSuccess
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("LocationClient", "❌ SecurityException при запросе обновлений", e)
            close(e)
        } catch (e: Exception) {
            Log.e("LocationClient", "❌ Неизвестная ошибка при запросе обновлений", e)
            close(e)
        }

        awaitClose {
            Log.d("LocationClient", "🛑 Остановка GPS")
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    /**
     * ✅ ИСПРАВЛЕНИЕ: Проверяем разрешения и улучшена обработка ошибок
     */
    fun getLastKnownLocation(): Flow<Location?> = callbackFlow {
        // ✅ ИСПРАВЛЕНИЕ: Явная проверка разрешений
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            Log.e("LocationClient", "❌ Нет разрешений на локацию для getLastKnownLocation")
            trySend(null).isSuccess
            close()
            return@callbackFlow
        }

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    trySend(location).isSuccess
                    close()
                }
                .addOnFailureListener { e ->
                    Log.e("LocationClient", "❌ Ошибка получения последней локации", e)
                    trySend(null).isSuccess
                    close()
                }
                .addOnCanceledListener {
                    Log.w("LocationClient", "⚠️ Запрос последней локации отменён")
                    trySend(null).isSuccess
                    close()
                }
        } catch (e: SecurityException) {
            Log.e("LocationClient", "❌ SecurityException при getLastLocation", e)
            trySend(null).isSuccess
            close()
        } catch (e: Exception) {
            Log.e("LocationClient", "❌ Неизвестная ошибка при getLastLocation", e)
            trySend(null).isSuccess
            close()
        }

        awaitClose { }
    }

    /**
     * Переключает режим работы GPS в зависимости от активности.
     * @param isRecording true - запись трека (высокая точность), false - просмотр (экономия батареи)
     */
    fun setRecordingMode(isRecording: Boolean) {
        priority = if (isRecording) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        Log.d("LocationClient", "🔄 Режим GPS: ${priorityToString(priority)}")
    }

    private fun priorityToString(priority: Int): String {
        return when (priority) {
            Priority.PRIORITY_HIGH_ACCURACY -> "HIGH_ACCURACY"
            Priority.PRIORITY_BALANCED_POWER_ACCURACY -> "BALANCED_POWER"
            Priority.PRIORITY_LOW_POWER -> "LOW_POWER"
            Priority.PRIORITY_PASSIVE -> "PASSIVE"
            else -> "UNKNOWN"
        }
    }
}