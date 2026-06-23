package ru.razrabozavr.bumpsense.presentation.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.presentation.components.ControlPanel
import ru.razrabozavr.bumpsense.presentation.components.TopStatusBar
import ru.razrabozavr.bumpsense.presentation.permissions.PermissionHandler

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val showExportDialog by viewModel.showExportDialog.collectAsState()
    val context = LocalContext.current
    val permissionHandler = remember { PermissionHandler(context) }

    // Состояние для Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Автоматическое отображение Snackbar при изменении сообщения в ViewModel
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

    // Лаунчер для создания файла (Экспорт)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        if (uri != null) {
            // Для примера экспортируем последний трек из истории
            val trackToExport = uiState.historyTracks.lastOrNull()?.let { points ->
                Track(
                    name = "Exported_Track",
                    startTime = System.currentTimeMillis(),
                    points = points
                )
            }
            if (trackToExport != null) {
                viewModel.exportTrack(trackToExport, uri)
            } else {
                viewModel.clearSnackbarMessage() // Сброс, если треков нет
            }
        }
    }

    // Лаунчер для открытия файла (Импорт)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importTrack(uri)
        }
    }

    // Запрос разрешений
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (permission, isGranted) ->
            permissionHandler.updatePermissionResult(permission, isGranted)
            viewModel.updatePermissionState(
                permissionHandler.permissionState.value.allPermissionsGranted
            )
        }
    }

    LaunchedEffect(Unit) {
        permissionHandler.checkPermissions()
        if (!permissionHandler.permissionState.value.allPermissionsGranted) {
            requestPermissionLauncher.launch(permissionHandler.getRequiredPermissions().toTypedArray())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ControlPanel(
                isRecording = uiState.isRecording,
                isHistoryVisible = uiState.isHistoryVisible,
                onRecordClick = {
                    if (uiState.locationPermissionGranted) {
                        viewModel.toggleRecording()
                    }
                },
                onHistoryClick = { viewModel.toggleHistoryVisibility() },
                onExportClick = { viewModel.setShowExportDialog(true) },
                onImportClick = {
                    importLauncher.launch(arrayOf("application/geo+json", "application/json"))
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Карта
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                currentTrackPoints = uiState.currentTrackPoints,
                historyTracks = if (uiState.isHistoryVisible) uiState.historyTracks else emptyList()
            )

            // Индикаторы состояния (сверху)
            TopStatusBar(
                gpsStatus = uiState.gpsStatus,
                isRecording = uiState.isRecording,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Кнопка центрирования карты (справа снизу, над панелью управления)
            FloatingActionButton(
                onClick = {
                    uiState.currentLocation?.let { loc ->
                        // Здесь можно добавить логику перемещения камеры MapLibre
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Мое местоположение"
                )
            }
        }

        // Диалог выбора трека для экспорта
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowExportDialog(false) },
                title = { Text("Экспорт трека") },
                text = {
                    if (uiState.historyTracks.isEmpty()) {
                        Text("Нет сохраненных треков для экспорта.")
                    } else {
                        Text("Будет экспортирован последний записанный трек в формате GeoJSON.")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.setShowExportDialog(false)
                            if (uiState.historyTracks.isNotEmpty()) {
                                exportLauncher.launch("track_export.geojson")
                            }
                        }
                    ) {
                        Text("Экспортировать")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowExportDialog(false) }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}