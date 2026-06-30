package ru.razrabozavr.bumpsense.presentation.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Обработчик runtime-разрешений для работы с геолокацией.
 *
 * ✅ ИСПРАВЛЕНИЕ: Убран ACCESS_BACKGROUND_LOCATION
 * - Foreground Service с foregroundServiceType="location" работает без этого разрешения
 * - Запрашиваются только FINE и COARSE (необходимые для GPS)
 * - Соответствие Google Play policies и принципам минимальных привилегий
 */
data class PermissionState(
    val fineLocationGranted: Boolean = false,
    val coarseLocationGranted: Boolean = false,
    // ✅ ИСПРАВЛЕНИЕ: backgroundLocation больше не требуется
    // Foreground Service с foregroundServiceType="location" работает без него
    // Поле оставлено для совместимости API, всегда true
    val backgroundLocationGranted: Boolean = true,
    val allPermissionsGranted: Boolean = false
)

class PermissionHandler(private val context: Context) {
    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    /**
     * Проверяет текущее состояние разрешений.
     * Вызывается при старте приложения для определения, нужно ли запрашивать разрешения.
     */
    fun checkPermissions() {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // ✅ ИСПРАВЛЕНИЕ: ACCESS_BACKGROUND_LOCATION больше не проверяется
        // Foreground Service работает без этого разрешения

        _permissionState.value = PermissionState(
            fineLocationGranted = fineLocation,
            coarseLocationGranted = coarseLocation,
            backgroundLocationGranted = true, // Всегда true — не требуется
            allPermissionsGranted = fineLocation && coarseLocation
        )
    }

    /**
     * Возвращает список разрешений, которые необходимо запросить у пользователя.
     *
     * ✅ ИСПРАВЛЕНИЕ: Запрашиваем только FINE и COARSE
     * ACCESS_BACKGROUND_LOCATION удалён — не требуется для Foreground Service
     */
    fun getRequiredPermissions(): List<String> {
        return listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /**
     * Обновляет состояние разрешений после диалога запроса.
     * Вызывается из MapScreen после получения результата от ActivityResultContracts.
     */
    fun updatePermissionResult(permission: String, granted: Boolean) {
        val currentState = _permissionState.value
        val newState = when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> currentState.copy(fineLocationGranted = granted)
            Manifest.permission.ACCESS_COARSE_LOCATION -> currentState.copy(coarseLocationGranted = granted)
            // ✅ ИСПРАВЛЕНИЕ: ACCESS_BACKGROUND_LOCATION больше не обрабатывается
            else -> currentState
        }
        _permissionState.value = newState.copy(
            allPermissionsGranted = newState.fineLocationGranted && newState.coarseLocationGranted
        )
    }
}