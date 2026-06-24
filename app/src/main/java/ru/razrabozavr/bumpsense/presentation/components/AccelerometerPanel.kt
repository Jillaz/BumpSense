package ru.razrabozavr.bumpsense.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.razrabozavr.bumpsense.domain.model.BumpLevel

@Composable
fun AccelerometerPanel(
    magnitude: Float,
    bumpIndex: Int,
    isAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val bumpLevel = BumpLevel.fromIndex(bumpIndex)

    // Пульсация индикатора в зависимости от величины ускорения
    val pulseScale by animateFloatAsState(
        targetValue = 1f + (magnitude / 20f).coerceIn(0f, 0.5f),
        label = "pulse"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Заголовок
            Text(
                text = "Акселерометр",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (!isAvailable) {
                Text(
                    text = "Недоступен",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                )
            } else {
                // Строка с ускорением
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ускор.:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                    )
                    Text(
                        text = String.format("%.2f", magnitude),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Строка с индексом
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Индекс:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(pulseScale)
                                .background(bumpLevel.color, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$bumpIndex",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = bumpLevel.color
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Название уровня (одной строкой, обрезается если не влезает)
                Text(
                    text = when (bumpLevel) {
                        BumpLevel.SMOOTH -> "Ровная"
                        BumpLevel.SLIGHT -> "Лёгкая тряска"
                        BumpLevel.MODERATE -> "Умеренная"
                        BumpLevel.STRONG -> "Сильная"
                        BumpLevel.EXTREME -> "Экстрем."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 9.sp,
                        color = bumpLevel.color
                    ),
                    maxLines = 1
                )
            }
        }
    }
}