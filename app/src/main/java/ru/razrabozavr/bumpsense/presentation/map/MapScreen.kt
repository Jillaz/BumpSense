package ru.razrabozavr.bumpsense.presentation.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.razrabozavr.bumpsense.data.sensor.AccelerometerViewModel
import ru.razrabozavr.bumpsense.presentation.components.AccelerometerPanel
import ru.razrabozavr.bumpsense.presentation.components.AppMenu
import ru.razrabozavr.bumpsense.presentation.components.ControlPanel
import ru.razrabozavr.bumpsense.presentation.components.TopStatusBar
import ru.razrabozavr.bumpsense.presentation.permissions.PermissionHandler
import ru.razrabozavr.bumpsense.presentation.track.TrackEditScreen
import ru.razrabozavr.bumpsense.R

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    accelerometerViewModel: AccelerometerViewModel,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val accelData by accelerometerViewModel.accelerometerData.collectAsState()
    val showExportDialog by viewModel.showExportDialog.collectAsState()
    val showClearDbDialog by viewModel.showClearDbDialog.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }

    // Состояния для режима редактирования
    val trackEditState by viewModel.trackEditState.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val cameraBounds by viewModel.cameraBounds.collectAsState()

    val context = LocalContext.current
    val permissionHandler = remember { PermissionHandler(context) }

    val snackbarHostState = remember { SnackbarHostState() }
    var centerTrigger by remember { mutableIntStateOf(0) }
    var autoFollow by remember { mutableStateOf(false) }

    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val topPadding = systemBarsPadding.calculateTopPadding()
    val bottomPadding = systemBarsPadding.calculateBottomPadding()

    // Показ Snackbar
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

    // Лаунчер разрешений
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.forEach { (permission, isGranted) ->
            permissionHandler.updatePermissionResult(permission, isGranted)
            if (!isGranted) allGranted = false
        }
        viewModel.updatePermissionState(allGranted)
    }

    LaunchedEffect(Unit) {
        permissionHandler.checkPermissions()
        if (!permissionHandler.permissionState.value.allPermissionsGranted) {
            requestPermissionLauncher.launch(permissionHandler.getRequiredPermissions().toTypedArray())
        } else {
            viewModel.updatePermissionState(true)
        }
    }

    // Лаунчер экспорта
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportAllTracks(uri)
        }
    }

    // Лаунчер импорта
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importTracks(uri)
        }
    }

    // Лаунчер добавления треков (без очистки базы)
    val appendTracksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.appendTracks(uri)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                TopStatusBar(
                    gpsStatus = uiState.gpsStatus,
                    isRecording = uiState.isRecording,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                AppMenu(
                    onExportClick = { viewModel.setShowExportDialog(true) },
                    onImportClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    onAppendTracksClick = {
                        appendTracksLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    onClearDbClick = { viewModel.setShowClearDbDialog(true) },
                    onEditTracksClick = { viewModel.enterEditMode() },
                    onAboutClick = { showAboutDialog = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        },
        bottomBar = {
            // Скрываем панель управления в режиме редактирования
            if (!isEditMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomPadding)
                ) {
                    ControlPanel(
                        isRecording = uiState.isRecording,
                        isHistoryVisible = uiState.isHistoryVisible,
                        onRecordClick = {
                            if (uiState.locationPermissionGranted) {
                                viewModel.toggleRecording()
                            }
                        },
                        onHistoryClick = { viewModel.toggleHistoryVisibility() }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Карта (всегда видна)
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                currentTrackPoints = uiState.currentTrackPoints,
                historyTracks = if (uiState.isHistoryVisible) uiState.historyTracks else emptyList(),
                currentLocation = uiState.currentLocation,
                centerTrigger = centerTrigger,
                autoFollow = autoFollow,
                cameraBounds = cameraBounds
            )

            // Панель акселерометра
            AccelerometerPanel(
                magnitude = accelData.magnitude,
                bumpIndex = accelData.bumpIndex,
                maxBumpIndex = accelData.maxBumpIndex,
                isAvailable = accelData.isAvailable,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = 8.dp,
                        top = 8.dp
                    )
                    .width(120.dp)
            )

            // FAB кнопки (поднимаем выше, если открыт режим редактирования)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = if (isEditMode) 260.dp else 16.dp + bottomPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка авто-наведения
                FloatingActionButton(
                    onClick = {
                        autoFollow = !autoFollow
                        if (autoFollow) {
                            centerTrigger++
                        }
                    },
                    containerColor = if (autoFollow)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = if (autoFollow)
                            "Авто-наведение включено"
                        else
                            "Авто-наведение выключено",
                        tint = if (autoFollow)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Кнопка разового центрирования
                FloatingActionButton(
                    onClick = { centerTrigger++ },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Мое местоположение",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // ✅ Список треков внизу экрана (30% высоты)
            if (isEditMode) {
                TrackEditScreen(
                    uiState = trackEditState,
                    onBackClick = { viewModel.exitEditMode() },
                    onTrackClick = { trackId -> viewModel.focusOnTrack(trackId) },
                    onDeleteClick = { track -> viewModel.deleteTrack(track) },
                    onDeleteConfirm = { },
                    onDeleteCancel = { },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Диалог экспорта
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowExportDialog(false) },
                title = { Text("Экспорт всех треков") },
                text = {
                    if (uiState.historyTracks.isEmpty()) {
                        Text("Нет сохраненных треков для экспорта.")
                    } else {
                        val totalPoints = uiState.historyTracks.sumOf { it.size }
                        Text(
                            "Будут экспортированы все треки (${uiState.historyTracks.size} треков, " +
                                    "$totalPoints точек) в формате GeoJSON.\n\n" +
                                    if (uiState.isRecording)
                                        "⚠️ Текущая запись будет остановлена перед экспортом."
                                    else ""
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.setShowExportDialog(false)
                            if (uiState.historyTracks.isNotEmpty()) {
                                exportLauncher.launch("all_tracks_export.geojson")
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

        // Диалог очистки БД
        if (showClearDbDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowClearDbDialog(false) },
                title = { Text("Очистка базы данных") },
                text = {
                    Text(
                        "Вы уверены, что хотите удалить ВСЕ сохраненные треки? " +
                                "Это действие нельзя отменить."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearDatabase() }
                    ) {
                        Text(
                            "Удалить всё",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowClearDbDialog(false) }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Диалог "О программе"
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text(stringResource(R.string.about_title)) },
                text = {
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { showAboutDialog = false }
                    ) {
                        Text("Закрыть")
                    }
                }
            )
        }
    }
}