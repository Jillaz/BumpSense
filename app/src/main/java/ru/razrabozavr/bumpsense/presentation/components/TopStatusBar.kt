package ru.razrabozavr.bumpsense.presentation.components

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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.razrabozavr.bumpsense.presentation.map.GpsStatus

/**
 * Верхняя панель статуса с информацией о GPS и записи.
 */
@Composable
fun TopStatusBar(
    gpsStatus: GpsStatus,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

        // Индикатор записи
        if (isRecording) {
            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = "Запись активна",
                tint = Color.Red,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = "REC",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Red
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Индикатор статуса GPS
        Spacer(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
    }
}