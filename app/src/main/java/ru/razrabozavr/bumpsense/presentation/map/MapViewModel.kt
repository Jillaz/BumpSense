package ru.razrabozavr.bumpsense.presentation.map

import android.app.Application
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.BumpSenseApp
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint

data class CameraBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

data class MapState(
    val tracks: List<Track> = emptyList(),
    val currentTrackPoints: List<TrackPoint> = emptyList(),
    val visibleTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    // Менеджер локации
    private val locationStateManager = LocationStateManager(application)
    val currentLocation: StateFlow<Location?> = locationStateManager.currentLocation

    // Контроллер записи
    private val recordingController = RecordingController(application)
    val isRecording: StateFlow<Boolean> = recordingController.isRecording

    // Контроллер камеры
    private val cameraController = MapCameraController()
    val centerTrigger: StateFlow<Int> = cameraController.centerTrigger
    val autoFollow: StateFlow<Boolean> = cameraController.autoFollow
    val cameraBounds: StateFlow<CameraBounds?> = cameraController.cameraBounds

    // Менеджер треков
    private val trackListManager: TrackListManager

    // ✅ НОВОЕ: Менеджер диалогов
    private val dialogManager = DialogManager()
    val showExportDialog: StateFlow<Boolean> = dialogManager.showExportDialog
    val showClearDbDialog: StateFlow<Boolean> = dialogManager.showClearDbDialog
    val snackbarMessage: StateFlow<String?> = dialogManager.snackbarMessage

    // ✅ НОВОЕ: Менеджер режима редактирования
    private val editModeManager = EditModeManager()
    val isEditMode: StateFlow<Boolean> = editModeManager.isEditMode
    val trackEditState: StateFlow<TrackEditState> = editModeManager.trackEditState
    val isHistoryVisible: StateFlow<Boolean> = editModeManager.isHistoryVisible

    // ✅ НОВОЕ: Менеджер настроек
    private val settingsManager = SettingsManager(application)
    val settingsState: StateFlow<SettingsState> = settingsManager.settingsState
    val isSettingsMode: StateFlow<Boolean> = settingsManager.isSettingsMode

    // Менеджер broadcast-событий
    private val broadcastManager = RecordingBroadcastManager(
        context = application,
        onTrackPointUpdate = { update ->
            handleTrackPointUpdate(update)
        },
        onRecordingStopped = {
            handleRecordingStopped()
        },
        onTrackRotated = { previousPointsCount ->
            handleTrackRotated(previousPointsCount)
        }
    )

    private val _mapState = MutableStateFlow(MapState())
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    // ✅ НОВОЕ: UI State для совместимости с MapScreen
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _locationPermissionGranted = MutableStateFlow(false)
    val locationPermissionGranted: StateFlow<Boolean> = _locationPermissionGranted.asStateFlow()

    init {
        Log.d("MapViewModel", "🔧 MapViewModel: init")

        val app = getApplication<BumpSenseApp>()
        trackListManager = TrackListManager(
            trackRepository = app.trackRepository,
            geoJsonMapper = ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper
        )

        locationStateManager.start(viewModelScope)
        broadcastManager.register()
        trackListManager.loadTracks(viewModelScope)

        observeTracks()
        observeCameraBounds()
        observeLocation()
        observeRecording()
    }

    private fun observeTracks() {
        viewModelScope.launch {
            trackListManager.tracks.collect { tracks ->
                _mapState.update { it.copy(tracks = tracks) }
                editModeManager.updateTracks(tracks)

                cameraController.cameraBoundsForTracks.value?.let { bounds ->
                    updateVisibleTracks(bounds)
                }

                updateUiState()
            }
        }
    }

    private fun observeCameraBounds() {
        viewModelScope.launch {
            cameraController.cameraBoundsForTracks.collect { bounds ->
                if (bounds != null) {
                    updateVisibleTracks(bounds)
                }
            }
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            locationStateManager.currentLocation.collect { location ->
                updateUiState()
            }
        }
    }

    private fun observeRecording() {
        viewModelScope.launch {
            recordingController.isRecording.collect { isRecording ->
                updateUiState()
            }
        }
    }

    private fun updateUiState() {
        _uiState.update {
            it.copy(
                currentLocation = locationStateManager.currentLocation.value,
                currentTrackPoints = _mapState.value.currentTrackPoints,
                historyTracks = _mapState.value.tracks.map { track -> track.points },
                isRecording = recordingController.isRecording.value,
                isHistoryVisible = editModeManager.isHistoryVisible.value,
                gpsStatus = determineGpsStatus(),
                locationPermissionGranted = _locationPermissionGranted.value,
                snackbarMessage = dialogManager.snackbarMessage.value
            )
        }
    }

    private fun determineGpsStatus(): GpsStatus {
        val location = locationStateManager.currentLocation.value
        return when {
            location == null -> GpsStatus.SEARCHING
            else -> GpsStatus.AVAILABLE
        }
    }

    // Обработчики событий от BroadcastManager

    private fun handleTrackPointUpdate(update: TrackPointUpdate) {
        val newPoint = TrackPoint(
            id = 0,
            trackId = 0,
            latitude = update.latitude,
            longitude = update.longitude,
            timestamp = System.currentTimeMillis(),
            bumpIndex = update.bumpIndex,
            speed = 0f
        )

        _mapState.update { state ->
            state.copy(currentTrackPoints = state.currentTrackPoints + newPoint)
        }
        updateUiState()
    }

    private fun handleRecordingStopped() {
        recordingController.setRecordingState(false)
        _mapState.update { it.copy(currentTrackPoints = emptyList()) }
        updateUiState()
    }

    private fun handleTrackRotated(previousPointsCount: Int) {
        _mapState.update { it.copy(currentTrackPoints = emptyList()) }
        updateUiState()
    }

    // Публичные методы

    fun centerOnCurrentLocation() {
        cameraController.centerOnCurrentLocation()
    }

    fun setAutoFollow(enabled: Boolean) {
        cameraController.setAutoFollow(enabled)
    }

    fun onCameraMove(bounds: CameraBounds) {
        cameraController.onCameraMove(bounds, viewModelScope)
    }

    fun updateVisibleArea(bounds: CameraBounds) {
        cameraController.onCameraMove(bounds, viewModelScope)
    }

    fun toggleRecording() {
        if (recordingController.isCurrentlyRecording()) {
            recordingController.stopRecording()
        } else {
            _mapState.update { it.copy(currentTrackPoints = emptyList()) }
            recordingController.startRecording()
        }
        updateUiState()
    }

    fun toggleHistoryVisibility() {
        editModeManager.toggleHistoryVisibility()
        updateUiState()
    }

    // Диалоги

    fun setShowExportDialog(show: Boolean) {
        dialogManager.setShowExportDialog(show)
    }

    fun setShowClearDbDialog(show: Boolean) {
        dialogManager.setShowClearDbDialog(show)
    }

    fun clearSnackbarMessage() {
        dialogManager.clearSnackbarMessage()
    }

    // Режим редактирования

    fun enterEditMode() {
        editModeManager.enterEditMode(_mapState.value.tracks)
    }

    fun exitEditMode() {
        editModeManager.exitEditMode()
    }

    fun selectTrackTab(tab: Int) {
        editModeManager.selectTrackTab(tab)
    }

    fun focusOnTrack(trackId: Long) {
        editModeManager.focusOnTrack(trackId)
    }

    fun deleteTrack(track: Track) {
        trackListManager.deleteTrack(track.id, viewModelScope)
    }

    // Настройки

    fun enterSettingsMode() {
        settingsManager.enterSettingsMode()
    }

    fun exitSettingsMode() {
        settingsManager.exitSettingsMode()
    }

    fun updateDarkTheme(isDark: Boolean) {
        settingsManager.updateDarkTheme(isDark)
    }

    fun updateGpsInterval(seconds: Int) {
        settingsManager.updateGpsInterval(seconds)
    }

    fun updateRadius(meters: Double) {
        settingsManager.updateRadius(meters)
    }

    fun updateAccelerometerThreshold(threshold: Float) {
        settingsManager.updateAccelerometerThreshold(threshold)
    }

    fun updateAutoSaveInterval(minutes: Int) {
        settingsManager.updateAutoSaveInterval(minutes)
    }

    fun updateMinUpdateDistance(meters: Float) {
        settingsManager.updateMinUpdateDistance(meters)
    }

    // Разрешения

    fun updatePermissionState(granted: Boolean) {
        _locationPermissionGranted.value = granted
        updateUiState()
    }

    // Экспорт/Импорт

    fun exportAllTracks(uri: Uri) {
        viewModelScope.launch {
            try {
                val app = getApplication<BumpSenseApp>()
                val tracks = _mapState.value.tracks
                val jsonString = ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper.tracksToGeoJson(tracks)

                app.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }

                dialogManager.showSnackbar("Треки экспортированы")
            } catch (e: Exception) {
                Log.e("MapViewModel", "❌ Ошибка экспорта", e)
                dialogManager.showSnackbar("Ошибка экспорта")
            }
        }
    }

    fun importTracks(uri: Uri) {
        viewModelScope.launch {
            try {
                val app = getApplication<BumpSenseApp>()
                val jsonString = app.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }

                if (jsonString != null) {
                    val track = ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper.geoJsonToTrack(jsonString)
                    if (track != null) {
                        app.trackRepository.insertTrack(track)
                        dialogManager.showSnackbar("Трек импортирован")
                    }
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "❌ Ошибка импорта", e)
                dialogManager.showSnackbar("Ошибка импорта")
            }
        }
    }

    fun appendTracks(uri: Uri) {
        importTracks(uri)
    }

    fun clearDatabase() {
        viewModelScope.launch {
            try {
                val app = getApplication<BumpSenseApp>()
                app.trackRepository.clearDatabase()
                dialogManager.showSnackbar("База данных очищена")
                dialogManager.setShowClearDbDialog(false)
            } catch (e: Exception) {
                Log.e("MapViewModel", "❌ Ошибка очистки БД", e)
                dialogManager.showSnackbar("Ошибка очистки БД")
            }
        }
    }

    private fun updateVisibleTracks(bounds: CameraBounds) {
        val tracks = _mapState.value.tracks
        val visibleTracks = cameraController.filterVisibleTracks(tracks, bounds)
        _mapState.update { it.copy(visibleTracks = visibleTracks) }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("MapViewModel", "🔚 MapViewModel: onCleared")

        locationStateManager.stop()
        broadcastManager.unregister()
        cameraController.cleanup()
        trackListManager.cleanup()
    }
}

// ✅ НОВОЕ: UI State для совместимости с MapScreen
data class MapUiState(
    val currentLocation: Location? = null,
    val currentTrackPoints: List<TrackPoint> = emptyList(),
    val historyTracks: List<List<TrackPoint>> = emptyList(),
    val isRecording: Boolean = false,
    val isHistoryVisible: Boolean = true,
    val gpsStatus: GpsStatus = GpsStatus.SEARCHING,
    val locationPermissionGranted: Boolean = false,
    val snackbarMessage: String? = null
)