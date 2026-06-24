package ru.razrabozavr.bumpsense.presentation.map

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.BumpSenseApp
import ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.service.RecordingService
import android.provider.OpenableColumns
import android.util.Log

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

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

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
                    updateCurrentLocation(Location("").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                    })
                }
                RecordingService.ACTION_RECORDING_STOPPED -> {
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            gpsStatus = GpsStatus.UNAVAILABLE,
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
            RecordingService.stopRecording(context)
        } else {
            RecordingService.startRecording(context)
            _uiState.update { it.copy(isRecording = true, gpsStatus = GpsStatus.FOUND) }
        }
    }

    fun toggleHistoryVisibility() {
        _uiState.update { it.copy(isHistoryVisible = !it.isHistoryVisible) }
    }

    fun updateCurrentLocation(location: Location) {
        _uiState.update {
            it.copy(
                currentLocation = location,
                gpsStatus = GpsStatus.FOUND
            )
        }
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
    }

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

                // Проверяем расширение файла
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                }

                Log.d("BumpSense", "📥 Импорт файла: $fileName")

                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()

                if (jsonString != null) {
                    val track = GeoJsonMapper.geoJsonToTrack(jsonString)
                    if (track != null) {
                        trackRepository.insertTrack(track)
                        _uiState.update { it.copy(snackbarMessage = "Трек успешно импортирован") }
                        Log.d("BumpSense", "✅ Трек импортирован: ${track.name}")
                    } else {
                        _uiState.update { it.copy(snackbarMessage = "Неверный формат файла") }
                        Log.e("BumpSense", "❌ Неверный формат файла")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(snackbarMessage = "Ошибка при импорте трека") }
                Log.e("BumpSense", "❌ Ошибка импорта", e)
            }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(trackPointReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}