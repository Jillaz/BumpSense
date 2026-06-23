package ru.razrabozavr.bumpsense.presentation.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionState(
    val fineLocationGranted: Boolean = false,
    val coarseLocationGranted: Boolean = false,
    val backgroundLocationGranted: Boolean = false,
    val allPermissionsGranted: Boolean = false
)

class PermissionHandler(private val context: Context) {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    fun checkPermissions() {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _permissionState.value = PermissionState(
            fineLocationGranted = fineLocation,
            coarseLocationGranted = coarseLocation,
            backgroundLocationGranted = backgroundLocation,
            allPermissionsGranted = fineLocation && coarseLocation
        )
    }

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        return permissions
    }

    fun updatePermissionResult(permission: String, granted: Boolean) {
        val currentState = _permissionState.value
        val newState = when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> currentState.copy(fineLocationGranted = granted)
            Manifest.permission.ACCESS_COARSE_LOCATION -> currentState.copy(coarseLocationGranted = granted)
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> currentState.copy(backgroundLocationGranted = granted)
            else -> currentState
        }
        _permissionState.value = newState.copy(
            allPermissionsGranted = newState.fineLocationGranted && newState.coarseLocationGranted
        )
    }
}