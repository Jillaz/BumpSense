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
import ru.razrabozavr.bumpsense.data.export.ExportResult
import ru.razrabozavr.bumpsense.data.export.ImportResult
import ru.razrabozavr.bumpsense.data.export.TrackExportImportManager
import ru.razrabozavr.bumpsense.data.location.LocationClient
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

    // ✅ РЕФАКТОРИНГ (Этап 3): Менеджер экспорта/импорта вынесен в отдельный класс
    private val exportImportManager = TrackExportImportManager(application, trackRepository)

    private val locationClient = LocationClient(application)
    private var locationJob: Job? = null

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    private val _showClearDbDialog = MutableStateFlow(false)
    val showClearDbDialog: StateFlow<Boolean> = _showClearDbDialog.asStateFlow()

    private var pendingExportUri: Uri? = null

    private val _trackEditState = MutableStateFlow(TrackEditUiState())
    val trackEditState: StateFlow<TrackEditUiState> = _trackEditState.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _cameraBounds = MutableStateFlow<CameraBounds?>(null)
    val cameraBounds: StateFlow<CameraBounds?> = _cameraBounds.asStateFlow()

    private val _currentMapBounds = MutableStateFlow<CameraBounds?>(null)
    private var cameraMoveJob: Job? = null

    private val pendingPoints = mutableListOf<TrackPoint>()
    private val pendingPointsLock = Any()
    private var trackPointsBatchJob: Job? = null

    // ===== НАСТРОЙКИ =====
    private val _settingsState = MutableStateFlow(
        SettingsState(
            isDarkTheme = appPreferences.isDarkTheme,
            gpsIntervalMs = appPreferences.gpsIntervalMs,
            updateRadiusMeters = appPreferences.updateRadiusMeters,
            accelerometerThreshold = appPreferences.accelerometerThreshold,
            autoSaveIntervalMinutes = appPreferences.autoSaveIntervalMinutes,
            minUpdateDistanceMeters = appPreferences.minUpdateDistanceMeters
        )
    )
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(appPreferences.isDarkTheme)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isSettingsMode = MutableStateFlow(false)
    val isSettingsMode: StateFlow<Boolean> = _isSettingsMode.asStateFlow()

    fun enterSettingsMode() { _isSettingsMode.value = true }
    fun exitSettingsMode() { _isSettingsMode.value = false }

    fun updateDarkTheme(isDark: Boolean) {
        appPreferences.isDarkTheme = isDark
        _settingsState.update { it.copy(isDarkTheme = isDark) }
        _isDarkTheme.value = isDark
    }

    fun updateMinUpdateDistance(meters: Float) {
        appPreferences.minUpdateDistanceMeters = meters
        _settingsState.update { it.copy(minUpdateDistanceMeters = meters) }
        locationClient.minUpdateDistanceMeters = meters
        Log.d("BumpSense", "📏 Мин. смещение изменено на $meters м")
    }

    fun updateGpsInterval(intervalMs: Long) {
        appPreferences.gpsIntervalMs = intervalMs
        _settingsState.update { it.copy(gpsIntervalMs = intervalMs) }
        locationJob?.cancel()
        startGpsTracking()
    }

    fun updateRadius(radius: Double) {
        appPreferences.updateRadiusMeters = radius
        _settingsState.update { it.copy(updateRadiusMeters = radius) }
    }

    fun updateAccelerometerThreshold(threshold: Float) {
        appPreferences.accelerometerThreshold = threshold
        _settingsState.update { it.copy(accelerometerThreshold = threshold) }
    }

    fun updateAutoSaveInterval(minutes: Int) {
        appPreferences.autoSaveIntervalMinutes = minutes
        _settingsState.update { it.copy(autoSaveIntervalMinutes = minutes) }
        Log.d("BumpSense", "⏱️ Интервал автосохранения изменён на $minutes мин")
    }
    // ==========================

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
                _trackEditState.update { currentState ->
                    currentState.copy(
                        allTracks = tracks,
                        visibleTracks = filterTracksByVisibleArea(tracks, _currentMapBounds.value)
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

    fun addTrackPoint(point: TrackPoint) {
        _uiState.update { current ->
            current.copy(currentTrackPoints = current.currentTrackPoints + point)
        }
    }

    fun clearCurrentTrack() {
        _uiState.update { it.copy(currentTrackPoints = emptyList()) }
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

    // ✅ РЕФАКТОРИНГ (Этап 3): Делегируем работу менеджеру, оставляем только UI-логику
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

    // ✅ РЕФАКТОРИНГ (Этап 3): Делегируем работу менеджеру
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

    // ✅ РЕФАКТОРИНГ (Этап 3): Делегируем работу менеджеру
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

    // ===== РЕДАКТИРОВАНИЕ ТРЕКОВ =====

    fun enterEditMode() {
        _isEditMode.value = true
        viewModelScope.launch {
            val allTracks = trackRepository.getAllTracks().first()
            _trackEditState.update { current ->
                current.copy(
                    allTracks = allTracks,
                    visibleTracks = filterTracksByVisibleArea(allTracks, _currentMapBounds.value),
                    currentTab = TrackListTab.ALL,
                    focusedTrackId = null
                )
            }
        }
    }

    fun exitEditMode() {
        _isEditMode.value = false
        _trackEditState.update { it.copy(focusedTrackId = null) }
        _cameraBounds.value = null

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
        _trackEditState.update { it.copy(currentTab = tab) }
    }

    fun updateVisibleArea(bounds: CameraBounds?) {
        _currentMapBounds.value = bounds

        if (_isEditMode.value) {
            cameraMoveJob?.cancel()
            cameraMoveJob = viewModelScope.launch {
                delay(300.milliseconds)
                val allTracks = trackRepository.getAllTracks().first()
                _trackEditState.update { current ->
                    current.copy(
                        visibleTracks = filterTracksByVisibleArea(allTracks, bounds)
                    )
                }
            }
        }
    }

    private fun filterTracksByVisibleArea(
        allTracks: List<Track>,
        bounds: CameraBounds?
    ): List<Track> {
        if (bounds == null) return emptyList()

        return allTracks.filter { track ->
            track.points.any { point ->
                point.latitude in bounds.minLat..bounds.maxLat &&
                        point.longitude in bounds.minLon..bounds.maxLon
            }
        }
    }

    fun focusOnTrack(trackId: Long) {
        viewModelScope.launch {
            val track = trackRepository.getTrackById(trackId)
            if (track != null && track.points.isNotEmpty()) {
                _trackEditState.update { it.copy(focusedTrackId = trackId) }

                val bounds = calculateTrackBounds(track.points)
                _cameraBounds.value = bounds

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
            trackRepository.deleteTrack(track.id)
            val updatedTracks = _trackEditState.value.allTracks.filter { t -> t.id != track.id }
            _trackEditState.update { current ->
                current.copy(
                    allTracks = updatedTracks,
                    visibleTracks = filterTracksByVisibleArea(updatedTracks, _currentMapBounds.value)
                )
            }

            if (_trackEditState.value.focusedTrackId == track.id) {
                _trackEditState.update { it.copy(focusedTrackId = null) }
                _cameraBounds.value = null
            }

            _uiState.update { it.copy(snackbarMessage = "Трек удален") }
        }
    }

    fun clearTrackFocus() {
        _trackEditState.update { it.copy(focusedTrackId = null) }
        _cameraBounds.value = null
        _uiState.update { it.copy(isHistoryVisible = true) }
    }

    private fun calculateTrackBounds(points: List<TrackPoint>): CameraBounds {
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        points.forEach { point ->
            if (point.latitude < minLat) minLat = point.latitude
            if (point.latitude > maxLat) maxLat = point.latitude
            if (point.longitude < minLon) minLon = point.longitude
            if (point.longitude > maxLon) maxLon = point.longitude
        }

        val latPadding = (maxLat - minLat) * 0.1
        val lonPadding = (maxLon - minLon) * 0.1

        return CameraBounds(
            minLat = minLat - latPadding,
            maxLat = maxLat + latPadding,
            minLon = minLon - lonPadding,
            maxLon = maxLon + lonPadding
        )
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
                _trackEditState.update { current ->
                    current.copy(
                        allTracks = emptyList(),
                        visibleTracks = emptyList()
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
        cameraMoveJob?.cancel()
        trackPointsBatchJob?.cancel()
        try {
            getApplication<Application>().unregisterReceiver(trackPointReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}