package ru.razrabozavr.bumpsense.presentation.map

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
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
import ru.razrabozavr.bumpsense.data.location.GpsTracker
import ru.razrabozavr.bumpsense.data.location.LocationClient
import ru.razrabozavr.bumpsense.data.receiver.RecordingServiceEvent
import ru.razrabozavr.bumpsense.data.receiver.RecordingServiceReceiver
import ru.razrabozavr.bumpsense.data.settings.SettingsManager
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.presentation.settings.SettingsState
import ru.razrabozavr.bumpsense.presentation.track.TrackEditUiState
import ru.razrabozavr.bumpsense.presentation.track.TrackListTab
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

    // ✅ РЕФАКТОРИНГ (Этап 6): Менеджер GPS-трекинга
    private val gpsTracker = GpsTracker(
        locationClient = LocationClient(application),
        appPreferences = appPreferences,
        scope = viewModelScope
    )

    // ✅ РЕФАКТОРИНГ (Этап 7): BroadcastReceiver вынесен в отдельный класс
    private val recordingServiceReceiver = RecordingServiceReceiver(application)

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
        gpsTracker.setMinUpdateDistance(meters)
    }

    fun updateGpsInterval(intervalMs: Long) {
        settingsManager.updateGpsInterval(intervalMs)
        gpsTracker.restart(_uiState.value.isRecording)
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

    // ✅ РЕФАКТОРИНГ (Этап 6): Подписка на GpsTracker для обновления UI
    private fun observeGpsTracker() {
        viewModelScope.launch {
            gpsTracker.currentLocation.collect { location ->
                // Обновляем currentLocation только если оно не переопределено из Broadcast
                // (при записи currentLocation обновляется через RecordingServiceReceiver)
                if (location != null && !_uiState.value.isRecording) {
                    _uiState.update { it.copy(currentLocation = location) }
                }
            }
        }
        viewModelScope.launch {
            gpsTracker.gpsStatus.collect { status ->
                _uiState.update { it.copy(gpsStatus = status) }
            }
        }
    }

    // ✅ РЕФАКТОРИНГ (Этап 7): Подписка на события от RecordingServiceReceiver
    private fun observeRecordingServiceEvents() {
        viewModelScope.launch {
            recordingServiceReceiver.events.collect { event ->
                when (event) {
                    is RecordingServiceEvent.TrackPointUpdate -> {
                        val trackPoint = TrackPoint(
                            id = 0,
                            trackId = 0,
                            latitude = event.latitude,
                            longitude = event.longitude,
                            timestamp = System.currentTimeMillis(),
                            bumpIndex = event.bumpIndex,
                            speed = 0f
                        )

                        synchronized(pendingPointsLock) {
                            pendingPoints.add(trackPoint)
                        }

                        // ✅ Обновляем локацию через GpsTracker
                        val location = recordingServiceReceiver.createLocationFromEvent(event)
                        gpsTracker.forceLocationUpdate(location)
                        _uiState.update { current ->
                            current.copy(currentLocation = location)
                        }
                    }
                    is RecordingServiceEvent.RecordingStopped -> {
                        Log.d("BumpSense", "⏹️ Запись остановлена — перезапускаем GPS для UI")

                        _uiState.update { it.copy(isRecording = false) }

                        // ✅ Перезапуск GPS через GpsTracker
                        gpsTracker.startTracking(isRecording = false)

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
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        loadHistoryTracks()
        gpsTracker.startTracking(isRecording = false)
        startTrackPointsBatching()
        observeGpsTracker()
        observeRecordingServiceEvents()
    }

    override fun onStart(owner: LifecycleOwner) {
        gpsTracker.onLifecycleStart(_uiState.value.isRecording)
    }

    override fun onStop(owner: LifecycleOwner) {
        gpsTracker.onLifecycleStop(_uiState.value.isRecording)
    }

    private fun startTrackPointsBatching() {
        trackPointsBatchJob?.cancel()
        trackPointsBatchJob = viewModelScope.launch {
            while (true) {
                delay(500.milliseconds)

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
            ru.razrabozavr.bumpsense.service.RecordingService.stopRecording(context)
            _uiState.update { it.copy(isRecording = false) }
        } else {
            Log.d("BumpSense", "▶️ Начало записи")

            // ✅ Останавливаем GPS в UI через GpsTracker
            gpsTracker.stopTracking()
            Log.d("BumpSense", "⏸️ GPS в UI остановлен")

            ru.razrabozavr.bumpsense.service.RecordingService.startRecording(context)
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
            gpsTracker.startTracking(_uiState.value.isRecording)
        } else {
            gpsTracker.setStatus(GpsStatus.UNAVAILABLE)
        }
    }

    fun setShowExportDialog(show: Boolean) {
        _showExportDialog.value = show
    }

    fun exportAllTracks(uri: Uri) {
        if (_uiState.value.isRecording) {
            pendingExportUri = uri
            Log.d("BumpSense", "⏸️ Запись идёт, останавливаем перед экспортом")
            ru.razrabozavr.bumpsense.service.RecordingService.stopRecording(getApplication())
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
        gpsTracker.release()
        trackPointsBatchJob?.cancel()
        // ✅ РЕФАКТОРИНГ (Этап 7): unregisterReceiver больше не нужен — RecordingServiceReceiver сам управляет регистрацией
    }
}