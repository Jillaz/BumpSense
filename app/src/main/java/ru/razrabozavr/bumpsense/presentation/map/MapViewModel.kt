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
import ru.razrabozavr.bumpsense.data.location.LocationClient
import ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.presentation.settings.SettingsState
import ru.razrabozavr.bumpsense.presentation.track.TrackEditUiState
import ru.razrabozavr.bumpsense.presentation.track.TrackListTab
import ru.razrabozavr.bumpsense.service.RecordingService
import kotlin.time.Duration.Companion.milliseconds

data class MapUiState(
    val isRecording: Boolean = false,
    val isHistoryVisible: Boolean = true,
    val currentLocation: Location? = null,
    val currentTrackPoints: List<TrackPoint> = emptyList(),
    val historyTracks: List<List<TrackPoint>> = emptyList(),
    val locationPermissionGranted: Boolean = false,
    val gpsStatus: GpsStatus = GpsStatus.SEARCHING,
    val snackbarMessage: String? = null,
    // ✅ ИСПРАВЛЕНИЕ (Вариант К): Состояния для индикатора прогресса
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val progressMessage: String? = null
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
        _settingsState.update { current -> current.copy(isDarkTheme = isDark) }
        _isDarkTheme.value = isDark
    }

    fun updateMinUpdateDistance(meters: Float) {
        appPreferences.minUpdateDistanceMeters = meters
        _settingsState.update { current -> current.copy(minUpdateDistanceMeters = meters) }
        locationClient.minUpdateDistanceMeters = meters
        Log.d("BumpSense", "📏 Мин. смещение изменено на $meters м")
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

    fun updateAutoSaveInterval(minutes: Int) {
        appPreferences.autoSaveIntervalMinutes = minutes
        _settingsState.update { current -> current.copy(autoSaveIntervalMinutes = minutes) }
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

                    _uiState.update { current -> current.copy(isRecording = false) }

                    startGpsTracking()

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
                _uiState.update { current -> current.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
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
            _uiState.update { current -> current.copy(isRecording = false) }
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

    // ✅ ИСПРАВЛЕНИЕ (Вариант К): Индикатор прогресса при экспорте
    private fun doExportAllTracks(uri: Uri) {
        viewModelScope.launch {
            try {
                // ✅ Показываем индикатор прогресса
                _uiState.update { current ->
                    current.copy(
                        isExporting = true,
                        progressMessage = "Подготовка к экспорту..."
                    )
                }

                val context = getApplication<Application>()
                val allTracks = trackRepository.getAllTracks().first()

                if (allTracks.isEmpty()) {
                    _uiState.update { current ->
                        current.copy(
                            isExporting = false,
                            progressMessage = null,
                            snackbarMessage = "Нет треков для экспорта"
                        )
                    }
                    return@launch
                }

                // ✅ Обновляем сообщение прогресса
                _uiState.update { current ->
                    current.copy(progressMessage = "Экспорт ${allTracks.size} треков...")
                }

                val jsonString = GeoJsonMapper.tracksToGeoJson(allTracks)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }

                val totalPoints = allTracks.sumOf { track -> track.points.size }
                _uiState.update { current ->
                    current.copy(
                        isExporting = false,
                        progressMessage = null,
                        snackbarMessage = "Экспортировано треков: ${allTracks.size}, точек: $totalPoints"
                    )
                }
                Log.d("BumpSense", "✅ Экспортировано треков: ${allTracks.size}, точек: $totalPoints")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { current ->
                    current.copy(
                        isExporting = false,
                        progressMessage = null,
                        snackbarMessage = "Ошибка при экспорте: ${e.message}"
                    )
                }
            }
        }
    }

    // ✅ ИСПРАВЛЕНИЕ (Вариант К): Индикатор прогресса при импорте
    fun importTracks(uri: Uri) {
        viewModelScope.launch {
            try {
                // ✅ Показываем индикатор прогресса
                _uiState.update { current ->
                    current.copy(
                        isImporting = true,
                        progressMessage = "Чтение файла..."
                    )
                }

                val context = getApplication<Application>()

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update { current ->
                        current.copy(
                            isImporting = false,
                            progressMessage = null,
                            snackbarMessage = "Не удалось открыть файл"
                        )
                    }
                    return@launch
                }

                val jsonString = inputStream.bufferedReader().use { reader -> reader.readText() }
                inputStream.close()

                Log.d("BumpSense", "📄 Размер файла: ${jsonString.length} символов")

                if (jsonString.isEmpty()) {
                    _uiState.update { current ->
                        current.copy(
                            isImporting = false,
                            progressMessage = null,
                            snackbarMessage = "Файл пустой"
                        )
                    }
                    return@launch
                }

                // ✅ Обновляем сообщение прогресса
                _uiState.update { current ->
                    current.copy(progressMessage = "Парсинг треков...")
                }

                val tracks = GeoJsonMapper.geoJsonToTracks(jsonString)
                Log.d("BumpSense", "📥 Распаршено треков: ${tracks.size}")

                if (tracks.isNotEmpty()) {
                    // ✅ Обновляем сообщение прогресса
                    _uiState.update { current ->
                        current.copy(progressMessage = "Сохранение ${tracks.size} треков в БД...")
                    }

                    Log.d("BumpSense", "🗑️ Очистка базы перед импортом")
                    trackRepository.clearDatabase()

                    var totalPoints = 0
                    tracks.forEach { track ->
                        trackRepository.insertTrack(track)
                        totalPoints += track.points.size
                    }

                    _uiState.update { current ->
                        current.copy(
                            isImporting = false,
                            progressMessage = null,
                            snackbarMessage = "Импортировано треков: ${tracks.size}, точек: $totalPoints"
                        )
                    }
                    Log.d("BumpSense", "✅ Импортировано треков: ${tracks.size}, точек: $totalPoints")
                } else {
                    _uiState.update { current ->
                        current.copy(
                            isImporting = false,
                            progressMessage = null,
                            snackbarMessage = "Неверный формат файла или нет треков"
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("BumpSense", "❌ Ошибка импорта: ${e.message}", e)
                _uiState.update { current ->
                    current.copy(
                        isImporting = false,
                        progressMessage = null,
                        snackbarMessage = "Ошибка при импорте: ${e.message}"
                    )
                }
            }
        }
    }

    // ✅ ИСПРАВЛЕНИЕ (Вариант К): Индикатор прогресса при добавлении треков
    fun appendTracks(uri: Uri) {
        viewModelScope.launch {
            try {
                // ✅ Показываем индикатор прогресса
                _uiState.update { current ->
                    current.copy(
                        isImporting = true,
                        progressMessage = "Чтение файла..."
                    )
                }

                val context = getApplication<Application>()

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update { current ->
                        current.copy(
                            isImporting = false,
                            progressMessage = null,
                            snackbarMessage = "Не удалось открыть файл"
                        )
                    }
                    return@launch
                }

                val jsonString = inputStream.bufferedReader().use { reader -> reader.readText() }
                inputStream.close()

                if (jsonString.isEmpty()) {
                    _uiState.update { current ->
                        current.copy(
                            isImporting = false,
                            progressMessage = null,
                            snackbarMessage = "Файл пустой"
                        )
                    }
                    return@launch
                }

                _uiState.update { current ->
                    current.copy(progressMessage = "Парсинг треков...")
                }

                val tracks = GeoJsonMapper.geoJsonToTracks(jsonString)

                if (tracks.isNotEmpty()) {
                    val existingTracks = trackRepository.getAllTracks().first()
                    val existingStartTimes = existingTracks.map { track -> track.startTime }.toSet()

                    val newTracks = tracks.filter { track -> track.startTime !in existingStartTimes }
                    val skippedCount = tracks.size - newTracks.size

                    if (newTracks.isNotEmpty()) {
                        _uiState.update { current ->
                            current.copy(progressMessage = "Сохранение ${newTracks.size} треков...")
                        }

                        var totalPoints = 0
                        newTracks.forEach { track ->
                            trackRepository.insertTrack(track)
                            totalPoints += track.points.size
                        }

                        val message = "Добавлено треков: ${newTracks.size}, точек: $totalPoints" +
                                if (skippedCount > 0) " (пропущено дубликатов: $skippedCount)" else ""

                        _uiState.update { current ->
                            current.copy(
                                isImporting = false,
                                progressMessage = null,
                                snackbarMessage = message
                            )
                        }
                    } else {
                        _uiState.update { current ->
                            current.copy(
                                isImporting = false,
                                progressMessage = null,
                                snackbarMessage = "Все треки уже существуют (дубликаты)"
                            )
                        }
                    }
                } else {
                    _uiState.update { current ->
                        current.copy(
                            isImporting = false,
                            progressMessage = null,
                            snackbarMessage = "Неверный формат файла или нет треков"
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { current ->
                    current.copy(
                        isImporting = false,
                        progressMessage = null,
                        snackbarMessage = "Ошибка при добавлении треков: ${e.message}"
                    )
                }
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
        }
    }

    fun selectTrackTab(tab: TrackListTab) {
        _trackEditState.update { current ->
            current.copy(currentTab = tab)
        }
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
                _trackEditState.update { current ->
                    current.copy(
                        allTracks = emptyList(),
                        visibleTracks = emptyList()
                    )
                }
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