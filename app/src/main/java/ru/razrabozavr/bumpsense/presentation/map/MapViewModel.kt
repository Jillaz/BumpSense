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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.BumpSenseApp
import ru.razrabozavr.bumpsense.data.location.LocationClient
import ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.service.RecordingService

data class MapUiState(
    val isRecording: Boolean = false,
    val isHistoryVisible: Boolean = true,
    val currentLocation: Location? = null,
    val currentTrackPoints: List<TrackPoint> = emptyList(),
    val historyTracks: List<List<TrackPoint>> = emptyList(),
    val locationPermissionGranted: Boolean = false,
    val gpsStatus: GpsStatus = GpsStatus.SEARCHING,
    val snackbarMessage: String? = null
)

enum class GpsStatus {
    SEARCHING,
    FOUND,
    UNAVAILABLE
}

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val trackRepository = (application as BumpSenseApp).trackRepository

    // GPS клиент для постоянного отслеживания местоположения
    private val locationClient = LocationClient(application)
    private var locationJob: Job? = null

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    // Состояние диалога очистки БД
    private val _showClearDbDialog = MutableStateFlow(false)
    val showClearDbDialog: StateFlow<Boolean> = _showClearDbDialog.asStateFlow()

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

                    addTrackPoint(trackPoint)
                }
                RecordingService.ACTION_RECORDING_STOPPED -> {
                    Log.d("BumpSense", "⏹️ Запись остановлена (GPS продолжает работать)")
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            snackbarMessage = "Запись маршрута завершена"
                        )
                    }
                }
            }
        }
    }

    init {
        loadHistoryTracks()
        registerReceiver()
        startGpsTracking()  // Запускаем GPS сразу при создании ViewModel
    }

    /**
     * Запускает постоянное отслеживание GPS.
     * Работает независимо от режима записи.
     */
    private fun startGpsTracking() {
        if (locationJob?.isActive == true) {
            Log.d("BumpSense", "⏸️ GPS уже работает")
            return
        }

        Log.d("BumpSense", "🚀 Запуск постоянного GPS-трекинга")

        locationJob = viewModelScope.launch {
            try {
                locationClient.getLocationUpdates(2000L).collect { location ->
                    Log.d("BumpSense", "📍 GPS обновление: ${location.latitude}, ${location.longitude}")
                    _uiState.update {
                        it.copy(
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
                _uiState.update {
                    it.copy(historyTracks = tracks.map { track -> track.points })
                }
            }
        }
    }

    fun toggleRecording() {
        val context = getApplication<Application>()
        if (_uiState.value.isRecording) {
            Log.d("BumpSense", "⏹️ Остановка записи (GPS продолжает работать)")
            RecordingService.stopRecording(context)
        } else {
            Log.d("BumpSense", "▶️ Начало записи")
            RecordingService.startRecording(context)
            _uiState.update {
                it.copy(
                    isRecording = true,
                    currentTrackPoints = emptyList()  // Очищаем текущий трек
                )
            }
        }
    }

    fun toggleHistoryVisibility() {
        _uiState.update { it.copy(isHistoryVisible = !it.isHistoryVisible) }
    }

    fun addTrackPoint(point: TrackPoint) {
        _uiState.update {
            it.copy(currentTrackPoints = it.currentTrackPoints + point)
        }
    }

    fun clearCurrentTrack() {
        _uiState.update { it.copy(currentTrackPoints = emptyList()) }
    }

    fun updatePermissionState(granted: Boolean) {
        _uiState.update { it.copy(locationPermissionGranted = granted) }
        if (granted) {
            // Если разрешение получено - запускаем GPS
            startGpsTracking()
        } else {
            _uiState.update { it.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
        }
    }

    // ===== ЭКСПОРТ / ИМПОРТ =====

    fun setShowExportDialog(show: Boolean) {
        _showExportDialog.value = show
    }

    fun exportTrack(track: Track, uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val jsonString = GeoJsonMapper.trackToGeoJson(track)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                _uiState.update { it.copy(snackbarMessage = "Трек успешно экспортирован") }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(snackbarMessage = "Ошибка при экспорте трека") }
            }
        }
    }

    fun importTrack(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()

                if (jsonString != null) {
                    val track = GeoJsonMapper.geoJsonToTrack(jsonString)
                    if (track != null) {
                        trackRepository.insertTrack(track)
                        _uiState.update { it.copy(snackbarMessage = "Трек успешно импортирован") }
                    } else {
                        _uiState.update { it.copy(snackbarMessage = "Неверный формат файла") }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(snackbarMessage = "Ошибка при импорте трека") }
            }
        }
    }

    // ===== ОЧИСТКА БАЗЫ ДАННЫХ =====

    fun setShowClearDbDialog(show: Boolean) {
        _showClearDbDialog.value = show
    }

    fun clearDatabase() {
        viewModelScope.launch {
            try {
                trackRepository.clearDatabase()
                _uiState.update {
                    it.copy(
                        historyTracks = emptyList(),
                        currentTrackPoints = emptyList(),
                        snackbarMessage = "База данных полностью очищена"
                    )
                }
                Log.d("BumpSense", "🗑️ База данных очищена")
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

    override fun onCleared() {
        super.onCleared()
        // Останавливаем GPS только когда ViewModel уничтожается
        locationJob?.cancel()
        try {
            getApplication<Application>().unregisterReceiver(trackPointReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}