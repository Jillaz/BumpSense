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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.BumpSenseApp
import ru.razrabozavr.bumpsense.data.location.LocationClient
import ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.presentation.settings.SettingsState
import ru.razrabozavr.bumpsense.presentation.track.TrackEditUiState
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

data class CameraBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

class MapViewModel(application: Application) : AndroidViewModel(application),
    DefaultLifecycleObserver {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val trackRepository = (application as BumpSenseApp).trackRepository
    private val appPreferences = (application as BumpSenseApp).appPreferences

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

    // ===== НАСТРОЙКИ =====
    private val _settingsState = MutableStateFlow(
        SettingsState(
            isDarkTheme = appPreferences.isDarkTheme,
            gpsIntervalMs = appPreferences.gpsIntervalMs,
            updateRadiusMeters = appPreferences.updateRadiusMeters,
            accelerometerThreshold = appPreferences.accelerometerThreshold
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
        _settingsState.update { current -> current.copy(isDarkTheme = isDark) }
        _isDarkTheme.value = isDark
    }

    fun updateGpsInterval(intervalMs: Long) {
        appPreferences.gpsIntervalMs = intervalMs
        _settingsState.update { current -> current.copy(gpsIntervalMs = intervalMs) }
        locationJob?.cancel()
        startGpsTracking()
    }

    fun updateRadius(radius: Double) {
        appPreferences.updateRadiusMeters = radius
        _settingsState.update { current -> current.copy(updateRadiusMeters = radius) }
    }

    fun updateAccelerometerThreshold(threshold: Float) {
        appPreferences.accelerometerThreshold = threshold
        _settingsState.update { current -> current.copy(accelerometerThreshold = threshold) }
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
                    addTrackPoint(trackPoint)
                }
                RecordingService.ACTION_RECORDING_STOPPED -> {
                    Log.d("BumpSense", "⏹️ Запись остановлена (GPS продолжает работать)")

                    _uiState.update { current -> current.copy(isRecording = false) }

                    val pendingUri = pendingExportUri
                    if (pendingUri != null) {
                        pendingExportUri = null
                        Log.d("BumpSense", "📤 Выполняем отложенный экспорт")
                        doExportAllTracks(pendingUri)
                    } else {
                        _uiState.update { current ->
                            current.copy(snackbarMessage = "Запись маршрута завершена")
                        }
                    }
                }
            }
        }
    }

    init {
        // ✅ Регистрируем observer для lifecycle
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        loadHistoryTracks()
        registerReceiver()
        startGpsTracking()
    }

    // ===== LIFECYCLE-AWARE GPS =====

    override fun onStart(owner: LifecycleOwner) {
        // Приложение на переднем плане — запускаем GPS для отображения на карте
        Log.d("BumpSense", "▶️ App foreground — запускаем GPS для карты")
        startGpsTracking()
    }

    override fun onStop(owner: LifecycleOwner) {
        // ✅ Приложение в фоне — останавливаем GPS для карты (если запись не идёт)
        // Во время записи GPS работает через RecordingService с WakeLock
        if (!_uiState.value.isRecording) {
            locationJob?.cancel()
            locationJob = null
            Log.d("BumpSense", "⏸️ GPS остановлен (приложение в фоне, запись не идёт) — разрешаем Doze Mode")
        } else {
            Log.d("BumpSense", "⏸️ Приложение в фоне, но запись идёт — GPS работает через сервис")
        }
    }

    // ===== GPS ТРЕКИНГ =====

    private fun startGpsTracking() {
        if (locationJob?.isActive == true) {
            Log.d("BumpSense", "️ GPS уже работает")
            return
        }

        val interval = appPreferences.gpsIntervalMs
        Log.d("BumpSense", "🚀 Запуск GPS с интервалом ${interval}мс")

        locationJob = viewModelScope.launch {
            try {
                locationClient.getLocationUpdates(interval).collect { location ->
                    Log.d("BumpSense", " GPS обновление: ${location.latitude}, ${location.longitude}")
                    _uiState.update { current ->
                        current.copy(
                            currentLocation = location,
                            gpsStatus = GpsStatus.FOUND
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.e("BumpSense", "❌ Нет разрешения на GPS", e)
                _uiState.update { current -> current.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
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
                _uiState.update { currentState ->
                    currentState.copy(
                        historyTracks = tracks.map { track -> track.points }
                    )
                }
                _trackEditState.update { currentState ->
                    currentState.copy(tracks = tracks)
                }
            }
        }
    }

    fun toggleRecording() {
        val context = getApplication<Application>()
        if (_uiState.value.isRecording) {
            Log.d("BumpSense", "⏹️ Остановка записи (GPS продолжает работать)")
            RecordingService.stopRecording(context)
            _uiState.update { current -> current.copy(isRecording = false) }
        } else {
            Log.d("BumpSense", "▶️ Начало записи")
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
        _uiState.update { current -> current.copy(isHistoryVisible = !current.isHistoryVisible) }
    }

    fun addTrackPoint(point: TrackPoint) {
        _uiState.update { current ->
            current.copy(currentTrackPoints = current.currentTrackPoints + point)
        }
    }

    fun clearCurrentTrack() {
        _uiState.update { current -> current.copy(currentTrackPoints = emptyList()) }
    }

    fun updatePermissionState(granted: Boolean) {
        _uiState.update { current -> current.copy(locationPermissionGranted = granted) }
        if (granted) {
            startGpsTracking()
        } else {
            _uiState.update { current -> current.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
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
            try {
                val context = getApplication<Application>()
                val allTracks = trackRepository.getAllTracks().first()

                if (allTracks.isEmpty()) {
                    _uiState.update { current -> current.copy(snackbarMessage = "Нет треков для экспорта") }
                    return@launch
                }

                val jsonString = GeoJsonMapper.tracksToGeoJson(allTracks)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }

                val totalPoints = allTracks.sumOf { track -> track.points.size }
                _uiState.update { current ->
                    current.copy(snackbarMessage = "Экспортировано треков: ${allTracks.size}, точек: $totalPoints")
                }
                Log.d("BumpSense", "✅ Экспортировано треков: ${allTracks.size}, точек: $totalPoints")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { current -> current.copy(snackbarMessage = "Ошибка при экспорте: ${e.message}") }
            }
        }
    }

    fun importTracks(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update { current -> current.copy(snackbarMessage = "Не удалось открыть файл") }
                    return@launch
                }

                val jsonString = inputStream.bufferedReader().use { reader -> reader.readText() }
                inputStream.close()

                Log.d("BumpSense", "📥 Размер файла: ${jsonString.length} символов")

                if (jsonString.isEmpty()) {
                    _uiState.update { current -> current.copy(snackbarMessage = "Файл пустой") }
                    return@launch
                }

                val tracks = GeoJsonMapper.geoJsonToTracks(jsonString)
                Log.d("BumpSense", "📥 Распаршено треков: ${tracks.size}")

                if (tracks.isNotEmpty()) {
                    Log.d("BumpSense", "🗑️ Очистка базы перед импортом")
                    trackRepository.clearDatabase()

                    var totalPoints = 0
                    tracks.forEach { track ->
                        trackRepository.insertTrack(track)
                        totalPoints += track.points.size
                        Log.d("BumpSense", "📥 Трек '${track.name}': ${track.points.size} точек")
                    }

                    _uiState.update { current ->
                        current.copy(snackbarMessage = "Импортировано треков: ${tracks.size}, точек: $totalPoints")
                    }
                    Log.d("BumpSense", "✅ Импортировано треков: ${tracks.size}, точек: $totalPoints")
                } else {
                    _uiState.update { current -> current.copy(snackbarMessage = "Неверный формат файла или нет треков") }
                    Log.e("BumpSense", "❌ geoJsonToTracks вернул пустой список")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("BumpSense", "❌ Ошибка импорта: ${e.message}", e)
                _uiState.update { current -> current.copy(snackbarMessage = "Ошибка при импорте: ${e.message}") }
            }
        }
    }

    fun appendTracks(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update { current -> current.copy(snackbarMessage = "Не удалось открыть файл") }
                    return@launch
                }

                val jsonString = inputStream.bufferedReader().use { reader -> reader.readText() }
                inputStream.close()

                Log.d("BumpSense", "📥 Размер файла: ${jsonString.length} символов")

                if (jsonString.isEmpty()) {
                    _uiState.update { current -> current.copy(snackbarMessage = "Файл пустой") }
                    return@launch
                }

                val tracks = GeoJsonMapper.geoJsonToTracks(jsonString)
                Log.d("BumpSense", " Распаршено треков: ${tracks.size}")

                if (tracks.isNotEmpty()) {
                    val existingTracks = trackRepository.getAllTracks().first()
                    val existingStartTimes = existingTracks.map { track -> track.startTime }.toSet()

                    var addedCount = 0
                    var skippedCount = 0
                    var totalPoints = 0

                    tracks.forEach { track ->
                        if (track.startTime in existingStartTimes) {
                            skippedCount++
                            Log.d("BumpSense", "⏭️ Пропущен дубликат трека '${track.name}' (startTime=${track.startTime})")
                        } else {
                            trackRepository.insertTrack(track)
                            addedCount++
                            totalPoints += track.points.size
                            Log.d("BumpSense", "✅ Добавлен трек '${track.name}': ${track.points.size} точек")
                        }
                    }

                    val message = "Добавлено треков: $addedCount, точек: $totalPoints" +
                            if (skippedCount > 0) " (пропущено дубликатов: $skippedCount)" else ""

                    _uiState.update { current -> current.copy(snackbarMessage = message) }
                    Log.d("BumpSense", "✅ $message")
                } else {
                    _uiState.update { current -> current.copy(snackbarMessage = "Неверный формат файла или нет треков") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("BumpSense", "❌ Ошибка добавления треков: ${e.message}", e)
                _uiState.update { current -> current.copy(snackbarMessage = "Ошибка при добавлении треков: ${e.message}") }
            }
        }
    }

    fun enterEditMode() {
        _isEditMode.value = true
        viewModelScope.launch {
            val allTracks = trackRepository.getAllTracks().first()
            _trackEditState.update { current ->
                current.copy(
                    tracks = allTracks,
                    focusedTrackId = null
                )
            }
        }
    }

    fun exitEditMode() {
        _isEditMode.value = false
        _trackEditState.update { current -> current.copy(focusedTrackId = null) }
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
            Log.d("BumpSense", "📋 Выход из режима редактирования, загружено треков: ${allTracks.size}")
        }
    }

    fun focusOnTrack(trackId: Long) {
        viewModelScope.launch {
            val track = trackRepository.getTrackById(trackId)
            if (track != null && track.points.isNotEmpty()) {
                _trackEditState.update { current -> current.copy(focusedTrackId = trackId) }

                val bounds = calculateTrackBounds(track.points)
                _cameraBounds.value = bounds

                _uiState.update { current ->
                    current.copy(
                        isHistoryVisible = true,
                        currentTrackPoints = track.points,
                        historyTracks = listOf(track.points)
                    )
                }

                Log.d("BumpSense", "🎯 Фокус на треке #$trackId: bounds=$bounds")
            }
        }
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            trackRepository.deleteTrack(track.id)
            val updatedTracks = _trackEditState.value.tracks.filter { t -> t.id != track.id }
            _trackEditState.update { current -> current.copy(tracks = updatedTracks) }

            if (_trackEditState.value.focusedTrackId == track.id) {
                _trackEditState.update { current -> current.copy(focusedTrackId = null) }
                _cameraBounds.value = null
            }

            _uiState.update { current -> current.copy(snackbarMessage = "Трек удален") }
        }
    }

    fun clearTrackFocus() {
        _trackEditState.update { current -> current.copy(focusedTrackId = null) }
        _cameraBounds.value = null
        _uiState.update { current -> current.copy(isHistoryVisible = true) }
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
                _trackEditState.update { current -> current.copy(tracks = emptyList()) }
                Log.d("BumpSense", "🗑️ База данных очищена")
            } catch (e: Exception) {
                Log.e("BumpSense", "❌ Ошибка при очистке БД", e)
                _uiState.update { current -> current.copy(snackbarMessage = "Ошибка при очистке БД") }
            } finally {
                _showClearDbDialog.value = false
            }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { current -> current.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        // ✅ Удаляем observer
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        locationJob?.cancel()
        try {
            getApplication<Application>().unregisterReceiver(trackPointReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}