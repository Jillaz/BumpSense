package ru.razrabozavr.bumpsense.presentation.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.razrabozavr.bumpsense.R
import ru.razrabozavr.bumpsense.data.sensor.AccelerometerViewModel
import ru.razrabozavr.bumpsense.presentation.components.AccelerometerPanel
import ru.razrabozavr.bumpsense.presentation.components.AppMenu
import ru.razrabozavr.bumpsense.presentation.components.ControlPanel
import ru.razrabozavr.bumpsense.presentation.components.SettingsInfoPanel
import ru.razrabozavr.bumpsense.presentation.components.TopStatusBar
import ru.razrabozavr.bumpsense.presentation.permissions.PermissionHandler
import ru.razrabozavr.bumpsense.presentation.settings.SettingsScreen
import ru.razrabozavr.bumpsense.presentation.track.TrackEditScreen

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
    val trackEditState by viewModel.trackEditState.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val cameraBounds by viewModel.cameraBounds.collectAsState()

    // Настройки
    val settingsState by viewModel.settingsState.collectAsState()
    val isSettingsMode by viewModel.isSettingsMode.collectAsState()

    val context = LocalContext.current
    val permissionHandler = remember { PermissionHandler(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    var centerTrigger by remember { mutableIntStateOf(0) }
    var autoFollow by remember { mutableStateOf(false) }

    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val topPadding = systemBarsPadding.calculateTopPadding()
    val bottomPadding = systemBarsPadding.calculateBottomPadding()

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/geo+json")
    ) { uri -> if (uri != null) viewModel.exportAllTracks(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importTracks(uri) }

    val appendTracksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.appendTracks(uri) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(top = topPadding)) {
                TopStatusBar(
                    gpsStatus = uiState.gpsStatus,
                    isRecording = uiState.isRecording,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                AppMenu(
                    onExportClick = { viewModel.setShowExportDialog(true) },
                    onImportClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    onAppendTracksClick = { appendTracksLauncher.launch(arrayOf("application/json", "*/*")) },
                    onClearDbClick = { viewModel.setShowClearDbDialog(true) },
                    onEditTracksClick = { viewModel.enterEditMode() },
                    onAboutClick = { showAboutDialog = true },
                    onSettingsClick = { viewModel.enterSettingsMode() },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        },
        bottomBar = {
            if (!isEditMode && !isSettingsMode) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding)) {
                    ControlPanel(
                        isRecording = uiState.isRecording,
                        isHistoryVisible = uiState.isHistoryVisible,
                        onRecordClick = {
                            if (uiState.locationPermissionGranted) viewModel.toggleRecording()
                        },
                        onHistoryClick = { viewModel.toggleHistoryVisibility() }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                currentTrackPoints = uiState.currentTrackPoints,
                historyTracks = if (uiState.isHistoryVisible) uiState.historyTracks else emptyList(),
                currentLocation = uiState.currentLocation,
                centerTrigger = centerTrigger,
                autoFollow = autoFollow,
                cameraBounds = cameraBounds,
                onCameraMove = { bounds -> viewModel.updateVisibleArea(bounds) },
                onStyleLoadError = { errorMessage ->
                    viewModel.showStyleLoadError(errorMessage)
                }
            )

            // Левая колонка: панель акселерометра + панель настроек
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AccelerometerPanel(
                    magnitude = accelData.magnitude,
                    bumpIndex = accelData.bumpIndex,
                    maxBumpIndex = accelData.maxBumpIndex,
                    isAvailable = accelData.isAvailable,
                    modifier = Modifier.width(132.dp)
                )

                SettingsInfoPanel(
                    settingsState = settingsState,
                    modifier = Modifier.width(132.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = if (isEditMode) 260.dp else 16.dp + bottomPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        autoFollow = !autoFollow
                        if (autoFollow) centerTrigger++
                    },
                    containerColor = if (autoFollow)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = if (autoFollow)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FloatingActionButton(
                    onClick = { centerTrigger++ },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.my_location),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // ✅ ИСПРАВЛЕНИЕ (Вариант К): Анимированное появление экрана редактирования треков
            AnimatedVisibility(
                visible = isEditMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                TrackEditScreen(
                    uiState = trackEditState,
                    onBackClick = { viewModel.exitEditMode() },
                    onTabChange = { tab -> viewModel.selectTrackTab(tab) },
                    onTrackClick = { trackId -> viewModel.focusOnTrack(trackId) },
                    onDeleteClick = { track -> viewModel.deleteTrack(track) },
                    onDeleteConfirm = { },
                    onDeleteCancel = { }
                )
            }

            // ✅ ИСПРАВЛЕНИЕ (Вариант К): Анимированное появление экрана настроек
            AnimatedVisibility(
                visible = isSettingsMode,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                SettingsScreen(
                    settingsState = settingsState,
                    onBackClick = { viewModel.exitSettingsMode() },
                    onDarkThemeChange = { viewModel.updateDarkTheme(it) },
                    onGpsIntervalChange = { viewModel.updateGpsInterval(it) },
                    onUpdateRadiusChange = { viewModel.updateRadius(it) },
                    onAccelerometerThresholdChange = { viewModel.updateAccelerometerThreshold(it) },
                    onAutoSaveIntervalChange = { minutes -> viewModel.updateAutoSaveInterval(minutes) },
                    onMinUpdateDistanceChange = { meters -> viewModel.updateMinUpdateDistance(meters) }
                )
            }

            // ✅ ИСПРАВЛЕНИЕ (Вариант К): Индикатор прогресса при экспорте/импорте
            AnimatedVisibility(
                visible = uiState.isExporting || uiState.isImporting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = if (uiState.isExporting) "Экспорт" else "Импорт",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            uiState.progressMessage?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Диалог экспорта
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowExportDialog(false) },
                title = { Text(stringResource(R.string.dialog_export_title)) },
                text = {
                    if (uiState.historyTracks.isEmpty()) {
                        Text(stringResource(R.string.dialog_export_empty))
                    } else {
                        val totalPoints = uiState.historyTracks.sumOf { it.size }
                        val warning = if (uiState.isRecording)
                            stringResource(R.string.dialog_export_warning)
                        else ""
                        Text(
                            stringResource(
                                R.string.dialog_export_confirm,
                                uiState.historyTracks.size,
                                totalPoints
                            ) + warning
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
                        Text(stringResource(R.string.button_export))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowExportDialog(false) }) {
                        Text(stringResource(R.string.button_cancel))
                    }
                }
            )
        }

        // Диалог очистки БД
        if (showClearDbDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowClearDbDialog(false) },
                title = { Text(stringResource(R.string.dialog_clear_db_title)) },
                text = { Text(stringResource(R.string.dialog_clear_db_message)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearDatabase() }) {
                        Text(
                            stringResource(R.string.button_delete_all),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowClearDbDialog(false) }) {
                        Text(stringResource(R.string.button_cancel))
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
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text(stringResource(R.string.button_close))
                    }
                }
            )
        }
    }
}