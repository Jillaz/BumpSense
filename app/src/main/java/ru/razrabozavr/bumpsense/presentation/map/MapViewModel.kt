package ru.razrabozavr.bumpsense.presentation.map

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.BumpSenseApp
import ru.razrabozavr.bumpsense.data.edit.TrackEditManager
import ru.razrabozavr.bumpsense.data.export.ExportResult
import ru.razrabozavr.bumpsense.data.export.ImportResult
import ru.razrabozavr.bumpsense.data.export.TrackExportImportManager
import ru.razrabozavr.bumpsense.data.location.LocationClient
import ru.razrabozavr.bumpsense.data.settings.SettingsManager
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.presentation.settings.SettingsState
import ru.razrabozavr.bumpsense.presentation.track.TrackEditUiState
import ru.razrabozavr.bumpsense.presentation.track.TrackListTab
import ru.razrabozavr.bumpsense.service.RecordingService
import kotlin.time.Duration.Companion.milliseconds

class MapViewModel(application: Application) : AndroidViewModel(application),
    DefaultLifecycleObserver {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val trackRepository = (application as BumpSenseApp).trackRepository
    private val appPreferences = (application as BumpSenseApp).appPreferences

    // ✅ РЕФАКТОРИНГ (Этап 3): Менеджер экспорта/импорта
    private val exportImportManager = TrackExportImportManager(application, trackRepository)

    // ✅ РЕФАКТОРИНГ (Этап 4): Менеджер настроек
    private val settingsManager = SettingsManager(appPreferences)

    // ✅ РЕФАКТОРИНГ (Этап 5): Менеджер редактирования треков
    private val trackEditManager = TrackEditManager(trackRepository)

    private val locationClient = LocationClient(application)
    private var locationJob: Job? = null

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    private val _showClearDbDialog = MutableStateFlow(false)
    val showClearDbDialog: StateFlow<Boolean> = _showClearDbDialog.asStateFlow()

    private var pendingExportUri: Uri? = null

    private val pendingPoints = mutableListOf<TrackPoint>()
    private val pendingPointsLock = Any()
    private var trackPointsBatchJob: Job? = null

    // ✅ РЕФАКТОРИНГ (Этап 4): Делегирование к SettingsManager
    val settingsState: StateFlow<SettingsState> = settingsManager.settingsState
    val isDarkTheme: StateFlow<Boolean> = settingsManager.isDarkTheme

    // ✅ РЕФАКТОРИНГ (Этап 5): Делегирование к TrackEditManager
    val trackEditState: StateFlow<TrackEditUiState> = trackEditManager.trackEditState
    val isEditMode: StateFlow<Boolean> = trackEditManager.isEditMode
    val cameraBounds: StateFlow<CameraBounds?> = trackEditManager.cameraBounds

    private val _isSettingsMode = MutableStateFlow(false)
    val isSettingsMode: StateFlow<Boolean> = _isSettingsMode.asStateFlow()

    fun enterSettingsMode() { _isSettingsMode.value = true }
    fun exitSettingsMode() { _isSettingsMode.value = false }

    // ✅ РЕФАКТОРИНГ (Этап 4): Делегирование методов обновления настроек
    fun updateDarkTheme(isDark: Boolean) {
        settingsManager.updateDarkTheme(isDark)
    }

    fun updateMinUpdateDistance(meters: Float) {
        settingsManager.updateMinUpdateDistance(meters)
        locationClient.minUpdateDistanceMeters = meters
    }

    fun updateGpsInterval(intervalMs: Long) {
        settingsManager.updateGpsInterval(intervalMs)
        locationJob?.cancel()
        startGpsTracking()
    }

    fun updateRadius(radius: Double) {
        settingsManager.updateRadius(radius)
    }

    fun updateAccelerometerThreshold(threshold: Float) {
        settingsManager.updateAccelerometerThreshold(threshold)
    }

    fun updateAutoSaveInterval(minutes: Int) {
        settingsManager.updateAutoSaveInterval(minutes)
    }

    // ✅ РЕФАКТОРИНГ (Этап 5): Делегирование методов редактирования треков
    fun enterEditMode() {
        trackEditManager.enterEditMode()
    }

    fun exitEditMode() {
        trackEditManager.exitEditMode()
        viewModelScope.launch {
            val allTracks = trackRepository.getAllTracks().first()
            _uiState.update { currentState ->
                currentState.copy(
                    isHistoryVisible = true,
                    historyTracks = allTracks.map { track -> track.points },
                    currentTrackPoints = emptyList()
                )
            }
        }
    }

    fun selectTrackTab(tab: TrackListTab) {
        trackEditManager.selectTrackTab(tab)
    }

    fun updateVisibleArea(bounds: CameraBounds?) {
        trackEditManager.updateVisibleArea(bounds)
    }

    fun focusOnTrack(trackId: Long) {
        trackEditManager.focusOnTrack(trackId)
        viewModelScope.launch {
            val track = trackRepository.getTrackById(trackId)
            if (track != null && track.points.isNotEmpty()) {
                val bounds = trackEditManager.calculateTrackBounds(track.points)
                _uiState.update { current ->
                    current.copy(
                        isHistoryVisible = true,
                        currentTrackPoints = track.points,
                        historyTracks = listOf(track.points)
                    )
                }
            }
        }
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            trackEditManager.deleteTrack(track.id)
            _uiState.update { it.copy(snackbarMessage = "Трек удален") }
        }
    }

    private val trackPointReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RecordingService.ACTION_TRACK_POINT_UPDATE -> {
                    val latitude = intent.getDoubleExtra(RecordingService.EXTRA_LATITUDE, 0.0)
                    val longitude = intent.getDoubleExtra(RecordingService.EXTRA_LONGITUDE, 0.0)
                    val bumpIndex = intent.getIntExtra(RecordingService.EXTRA_BUMP_INDEX, 0)

                    val trackPoint = TrackPoint(
                        id = 0,
                        trackId = 0,
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = System.currentTimeMillis(),
                        bumpIndex = bumpIndex,
                        speed = 0f
                    )

                    synchronized(pendingPointsLock) {
                        pendingPoints.add(trackPoint)
                    }

                    val location = Location("service").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                        time = System.currentTimeMillis()
                    }
                    _uiState.update { current ->
                        current.copy(
                            currentLocation = location,
                            gpsStatus = GpsStatus.FOUND
                        )
                    }
                }
                RecordingService.ACTION_RECORDING_STOPPED -> {
                    Log.d("BumpSense", "⏹️ Запись остановлена — перезапускаем GPS для UI")

                    _uiState.update { it.copy(isRecording = false) }

                    startGpsTracking()

                    val pendingUri = pendingExportUri
                    if (pendingUri != null) {
                        pendingExportUri = null
                        Log.d("BumpSense", "📤 Выполняем отложенный экспорт")
                        doExportAllTracks(pendingUri)
                    } else {
                        _uiState.update { it.copy(snackbarMessage = "Запись маршрута завершена") }
                    }
                }
            }
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        loadHistoryTracks()
        registerReceiver()
        startGpsTracking()
        startTrackPointsBatching()
    }

    override fun onStart(owner: LifecycleOwner) {
        Log.d("BumpSense", "▶️ App foreground — запускаем GPS для карты")
        startGpsTracking()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!_uiState.value.isRecording) {
            locationJob?.cancel()
            locationJob = null
            Log.d("BumpSense", "⏸️ GPS остановлен (приложение в фоне)")
        } else {
            Log.d("BumpSense", "⏸️ Приложение в фоне, но запись идёт")
        }
    }

    private fun startGpsTracking() {
        if (_uiState.value.isRecording) {
            Log.d("BumpSense", "⏸️ GPS для UI отключён (работает через RecordingService)")
            return
        }

        if (locationJob?.isActive == true) {
            Log.d("BumpSense", "⏸️ GPS уже работает")
            return
        }

        val interval = appPreferences.gpsIntervalMs

        locationClient.priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
        locationClient.minUpdateDistanceMeters = appPreferences.minUpdateDistanceMeters

        Log.d(
            "BumpSense",
            "🚀 Запуск GPS для UI: interval=${interval}мс, priority=BALANCED_POWER"
        )

        locationJob = viewModelScope.launch {
            try {
                locationClient.getLocationUpdates(interval).collect { location ->
                    _uiState.update { current ->
                        current.copy(
                            currentLocation = location,
                            gpsStatus = GpsStatus.FOUND
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.e("BumpSense", "❌ Нет разрешения на GPS", e)
                _uiState.update { it.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
            } catch (e: Exception) {
                Log.e("BumpSense", "❌ Ошибка GPS", e)
            }
        }
    }

    private fun startTrackPointsBatching() {
        trackPointsBatchJob?.cancel()
        trackPointsBatchJob = viewModelScope.launch {
            while (true) {
                delay(500)

                val pointsToFlush: List<TrackPoint>
                synchronized(pendingPointsLock) {
                    if (pendingPoints.isEmpty()) continue
                    pointsToFlush = pendingPoints.toList()
                    pendingPoints.clear()
                }

                _uiState.update { current ->
                    current.copy(currentTrackPoints = current.currentTrackPoints + pointsToFlush)
                }

                Log.d("BumpSense", "🎨 Карта обновлена: +${pointsToFlush.size} точек (батч)")
            }
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(RecordingService.ACTION_TRACK_POINT_UPDATE)
            addAction(RecordingService.ACTION_RECORDING_STOPPED)
        }

        ContextCompat.registerReceiver(
            getApplication(),
            trackPointReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun loadHistoryTracks() {
        viewModelScope.launch {
            trackRepository.getAllTracks().collect { tracks ->
                _uiState.update { currentState ->
                    currentState.copy(
                        historyTracks = tracks.map { track -> track.points }
                    )
                }
            }
        }
    }

    fun toggleRecording() {
        val context = getApplication<Application>()
        if (_uiState.value.isRecording) {
            Log.d("BumpSense", "⏹️ Остановка записи")
            RecordingService.stopRecording(context)
            _uiState.update { it.copy(isRecording = false) }
        } else {
            Log.d("BumpSense", "▶️ Начало записи")

            locationJob?.cancel()
            locationJob = null
            Log.d("BumpSense", "⏸️ GPS в UI остановлен")

            RecordingService.startRecording(context)
            _uiState.update { current ->
                current.copy(
                    isRecording = true,
                    currentTrackPoints = emptyList()
                )
            }
        }
    }

    fun toggleHistoryVisibility() {
        _uiState.update { it.copy(isHistoryVisible = !it.isHistoryVisible) }
    }

    fun updatePermissionState(granted: Boolean) {
        _uiState.update { it.copy(locationPermissionGranted = granted) }
        if (granted) {
            startGpsTracking()
        } else {
            _uiState.update { it.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
        }
    }

    fun setShowExportDialog(show: Boolean) {
        _showExportDialog.value = show
    }

    fun exportAllTracks(uri: Uri) {
        if (_uiState.value.isRecording) {
            pendingExportUri = uri
            Log.d("BumpSense", "⏸️ Запись идёт, останавливаем перед экспортом")
            RecordingService.stopRecording(getApplication())
        } else {
            doExportAllTracks(uri)
        }
    }

    private fun doExportAllTracks(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, progressMessage = null) }

            val result = exportImportManager.exportTracks(uri) { message ->
                _uiState.update { it.copy(progressMessage = message) }
            }

            val (isExporting, progressMessage, snackbarMessage) = when (result) {
                is ExportResult.Success -> Triple(
                    false,
                    null,
                    "Экспортировано треков: ${result.tracksCount}, точек: ${result.pointsCount}"
                )
                is ExportResult.Error -> Triple(
                    false,
                    null,
                    "Ошибка при экспорте: ${result.message}"
                )
                ExportResult.Empty -> Triple(
                    false,
                    null,
                    "Нет треков для экспорта"
                )
            }

            _uiState.update { current ->
                current.copy(
                    isExporting = isExporting,
                    progressMessage = progressMessage,
                    snackbarMessage = snackbarMessage
                )
            }
        }
    }

    fun importTracks(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, progressMessage = null) }

            val result = exportImportManager.importTracks(uri) { message ->
                _uiState.update { it.copy(progressMessage = message) }
            }

            val (isImporting, progressMessage, snackbarMessage) = when (result) {
                is ImportResult.Success -> Triple(
                    false,
                    null,
                    "Импортировано треков: ${result.tracksCount}, точек: ${result.pointsCount}"
                )
                is ImportResult.Error -> Triple(
                    false,
                    null,
                    "Ошибка при импорте: ${result.message}"
                )
                ImportResult.Empty -> Triple(
                    false,
                    null,
                    "Файл пустой"
                )
            }

            _uiState.update { current ->
                current.copy(
                    isImporting = isImporting,
                    progressMessage = progressMessage,
                    snackbarMessage = snackbarMessage
                )
            }
        }
    }

    fun appendTracks(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, progressMessage = null) }

            val result = exportImportManager.appendTracks(uri) { message ->
                _uiState.update { it.copy(progressMessage = message) }
            }

            val (isImporting, progressMessage, snackbarMessage) = when (result) {
                is ImportResult.Success -> {
                    val baseMessage = if (result.tracksCount > 0) {
                        "Добавлено треков: ${result.tracksCount}, точек: ${result.pointsCount}"
                    } else {
                        "Новых треков не найдено"
                    }
                    val skippedInfo = if (result.skippedCount > 0) {
                        " (пропущено дубликатов: ${result.skippedCount})"
                    } else {
                        ""
                    }
                    Triple(false, null, baseMessage + skippedInfo)
                }
                is ImportResult.Error -> Triple(
                    false,
                    null,
                    "Ошибка при добавлении треков: ${result.message}"
                )
                ImportResult.Empty -> Triple(
                    false,
                    null,
                    "Файл пустой"
                )
            }

            _uiState.update { current ->
                current.copy(
                    isImporting = isImporting,
                    progressMessage = progressMessage,
                    snackbarMessage = snackbarMessage
                )
            }
        }
    }

    fun setShowClearDbDialog(show: Boolean) {
        _showClearDbDialog.value = show
    }

    fun clearDatabase() {
        viewModelScope.launch {
            try {
                trackRepository.clearDatabase()
                _uiState.update { current ->
                    current.copy(
                        historyTracks = emptyList(),
                        currentTrackPoints = emptyList(),
                        snackbarMessage = "База данных полностью очищена"
                    )
                }
            } catch (e: Exception) {
                Log.e("BumpSense", "❌ Ошибка при очистке БД", e)
                _uiState.update { it.copy(snackbarMessage = "Ошибка при очистке БД") }
            } finally {
                _showClearDbDialog.value = false
            }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showStyleLoadError(message: String) {
        Log.e("BumpSense", "❌ Ошибка стиля карты: $message")
        _uiState.update { current ->
            current.copy(snackbarMessage = "⚠️ $message")
        }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        locationJob?.cancel()
        trackPointsBatchJob?.cancel()
        try {
            getApplication<Application>().unregisterReceiver(trackPointReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}