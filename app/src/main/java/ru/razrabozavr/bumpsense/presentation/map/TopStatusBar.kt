package ru.razrabozavr.bumpsense.presentation.map

import android.content.Context
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Статус GPS для отображения в UI.
 */
enum class GpsStatus {
    AVAILABLE,      // GPS доступен и есть сигнал
    SEARCHING,      // GPS ищет спутники
    UNAVAILABLE     // GPS выключен или недоступен
}

/**
 * Верхняя панель статуса с информацией о GPS.
 */
@Composable
fun TopStatusBar(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel
) {
    val context = LocalContext.current
    val currentLocation by viewModel.currentLocation.collectAsState()

    // ✅ ИСПРАВЛЕНИЕ: Определяем статус GPS на основе наличия локации
    val gpsStatus = determineGpsStatus(context, currentLocation != null)

    val (statusColor, statusText, statusIcon) = when (gpsStatus) {
        GpsStatus.AVAILABLE -> Triple(
            Color(0xFF4CAF50),
            "GPS активен",
            Icons.Filled.GpsFixed
        )
        GpsStatus.SEARCHING -> Triple(
            Color(0xFFFFC107),
            "Поиск GPS...",
            Icons.Filled.GpsNotFixed
        )
        GpsStatus.UNAVAILABLE -> Triple(
            Color(0xFFF44336),
            "GPS недоступен",
            Icons.Filled.GpsOff
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = statusIcon,
            contentDescription = statusText,
            tint = statusColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )

        Spacer(modifier = Modifier.weight(1f))

        // Индикатор статуса
        Spacer(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
    }
}

/**
 * Определяет статус GPS на основе контекста и наличия локации.
 */
private fun determineGpsStatus(context: Context, hasLocation: Boolean): GpsStatus {
    val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java)
        ?: return GpsStatus.UNAVAILABLE

    val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    return when {
        !isGpsEnabled -> GpsStatus.UNAVAILABLE
        hasLocation -> GpsStatus.AVAILABLE
        else -> GpsStatus.SEARCHING
    }
}