package ru.razrabozavr.bumpsense.presentation.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.razrabozavr.bumpsense.data.update.UpdateState

@Composable
fun UpdateDialog(
    viewModel: UpdateViewModel, // ✅ Убран параметр activity
    onDismiss: () -> Unit
) {
    val updateState by viewModel.updateState.collectAsState()

    when (val state = updateState) {
        is UpdateState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Доступно обновление") },
                text = { Text("Доступна новая версия приложения. Хотите обновить сейчас?") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.startUpdate() // ✅ Вызов без activity
                        onDismiss()
                    }) {
                        Text("Обновить")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Позже")
                    }
                }
            )
        }

        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Загрузка обновления") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${state.progress}%")
                    }
                },
                confirmButton = { },
                dismissButton = { }
            )
        }

        is UpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Обновление готово") },
                text = { Text("Обновление загружено. Приложение будет перезапущено.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.completeUpdate()
                        onDismiss()
                    }) {
                        Text("Установить")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Позже")
                    }
                }
            )
        }

        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Ошибка обновления") },
                text = { Text(state.message, color = MaterialTheme.colorScheme.error) },
                confirmButton = {
                    Button(onClick = onDismiss) { Text("OK") }
                }
            )
        }

        else -> Unit
    }
}