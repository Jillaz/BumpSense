package ru.razrabozavr.bumpsense.presentation.map

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.razrabozavr.bumpsense.data.sensor.AccelerometerViewModel
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.presentation.components.AccelerometerPanel
import ru.razrabozavr.bumpsense.presentation.components.AppMenu
import ru.razrabozavr.bumpsense.presentation.components.ControlPanel
import ru.razrabozavr.bumpsense.presentation.components.TopStatusBar
import ru.razrabozavr.bumpsense.presentation.permissions.PermissionHandler

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
    val context = LocalContext.current
    val permissionHandler = remember { PermissionHandler(context) }

    val snackbarHostState = remember { SnackbarHostState() }
    var centerTrigger by remember { mutableIntStateOf(0) }

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
    ) { uri ->
        if (uri != null) {
            val trackToExport = uiState.historyTracks.lastOrNull()?.let { points ->
                Track(
                    name = "Exported_Track",
                    startTime = System.currentTimeMillis(),
                    points = points
                )
            }
            if (trackToExport != null) {
                viewModel.exportTrack(trackToExport, uri)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importTrack(uri)
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

                // ✅ Меню справа вверху
                AppMenu(
                    onExportClick = { viewModel.setShowExportDialog(true) },
                    onImportClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        },
        bottomBar = {
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                currentTrackPoints = uiState.currentTrackPoints,
                historyTracks = if (uiState.isHistoryVisible) uiState.historyTracks else emptyList(),
                currentLocation = uiState.currentLocation,
                centerTrigger = centerTrigger
            )

            AccelerometerPanel(
                magnitude = accelData.magnitude,
                bumpIndex = accelData.bumpIndex,
                isAvailable = accelData.isAvailable,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = 16.dp,
                        top = 16.dp + topPadding
                    )
            )

            FloatingActionButton(
                onClick = {
                    Log.d("BumpSense", " FAB нажата, centerTrigger=$centerTrigger")
                    centerTrigger++
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = 16.dp + bottomPadding
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Мое местоположение"
                )
            }
        }

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