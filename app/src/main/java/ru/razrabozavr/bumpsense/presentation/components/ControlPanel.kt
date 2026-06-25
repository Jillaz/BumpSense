package ru.razrabozavr.bumpsense.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ControlPanel(
    isRecording: Boolean,
    isHistoryVisible: Boolean,
    onRecordClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка записи
            IconButton(
                onClick = onRecordClick,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = if (isRecording) "Остановить запись" else "Начать запись",
                    tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            // ✅ Кнопка отображения истории треков — иконка глаза
            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (isHistoryVisible)
                        Icons.Default.Visibility      // 👁️ Глаз открыт — треки видны
                    else
                        Icons.Default.VisibilityOff,  // 👁️‍🗨️ Глаз закрыт — треки скрыты
                    contentDescription = if (isHistoryVisible)
                        "Скрыть историю треков"
                    else
                        "Показать историю треков",
                    tint = if (isHistoryVisible)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}