package ru.razrabozavr.bumpsense.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun AppMenu(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // ✅ Обертка Box для правильного позиционирования меню
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Меню"
            )
        }

        // ✅ Меню позиционируется относительно IconButton
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Экспорт трека") },
                onClick = {
                    expanded = false
                    onExportClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                }
            )

            DropdownMenuItem(
                text = { Text("Импорт трека") },
                onClick = {
                    expanded = false
                    onImportClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                }
            )
        }
    }
}