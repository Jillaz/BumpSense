package ru.razrabozavr.bumpsense.presentation.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TermsAndConditionsDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    context: Context
) {
    AlertDialog(
        onDismissRequest = { /* Запрещаем закрытие вне кнопок */ },
        title = {
            Text(
                text = "Добро пожаловать!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Для использования приложения «Трекер дорожных неровностей» необходимо принять следующие документы:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Ссылка на Политику конфиденциальности
                DocumentLink(
                    icon = Icons.Outlined.PrivacyTip,
                    text = "Политика конфиденциальности",
                    url = "https://razrabozavr.github.io/Razrabozavr/BumpSense-privacy-policy",
                    context = context
                )

                // Ссылка на Пользовательское соглашение
                DocumentLink(
                    icon = Icons.Outlined.Description,
                    text = "Пользовательское соглашение",
                    url = "https://razrabozavr.github.io/Razrabozavr/BumpSense-terms-of-service",
                    context = context
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Нажимая «Принять», вы подтверждаете, что ознакомились с этими документами и согласны с ними.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Принять")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отклонить и выйти")
            }
        }
    )
}

@Composable
private fun DocumentLink(
    icon: ImageVector,
    text: String,
    url: String,
    context: Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium
            )
        )
    }
}