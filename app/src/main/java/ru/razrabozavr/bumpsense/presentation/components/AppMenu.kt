package ru.razrabozavr.bumpsense.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import ru.razrabozavr.bumpsense.R
import androidx.compose.ui.res.stringResource

@Composable
fun AppMenu(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onAppendTracksClick: () -> Unit,
    onClearDbClick: () -> Unit,
    onEditTracksClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = stringResource(R.string.menu_content_description)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_export)) },
                onClick = {
                    expanded = false
                    onExportClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_import)) },
                onClick = {
                    expanded = false
                    onImportClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_append)) },
                onClick = {
                    expanded = false
                    onAppendTracksClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.AddCircle, contentDescription = null)
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_edit)) },
                onClick = {
                    expanded = false
                    onEditTracksClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_clear)) },
                onClick = {
                    expanded = false
                    onClearDbClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                }
            )
        }
    }
}