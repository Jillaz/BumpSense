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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.BumpSenseApp
import ru.razrabozavr.bumpsense.data.location.LocationClient
import ru.razrabozavr.bumpsense.data.mapper.GeoJsonMapper
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
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

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val trackRepository = (application as BumpSenseApp).trackRepository

    // GPS клиент для постоянного отслеживания местоположения
    private val locationClient = LocationClient(application)
    private var locationJob: Job? = null

    // Диалоги
    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    private val _showClearDbDialog = MutableStateFlow(false)
    val showClearDbDialog: StateFlow<Boolean> = _showClearDbDialog.asStateFlow()

    // URI для отложенного экспорта (если запись ещё идёт)
    private var pendingExportUri: Uri? = null

    // ===== РЕЖИМ РЕДАКТИРОВАНИЯ ТРЕКОВ =====

    private val _trackEditState = MutableStateFlow(TrackEditUiState())
    val trackEditState: StateFlow<TrackEditUiState> = _trackEditState.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _cameraBounds = MutableStateFlow<CameraBounds?>(null)
    val cameraBounds: StateFlow<CameraBounds?> = _cameraBounds.asStateFlow()

    // ===== BROADCAST RECEIVER =====

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

                    // ✅ ВСЕГДА обновляем состояние записи
                    _uiState.update { it.copy(isRecording = false) }

                    // Если был запрошен экспорт во время записи — выполняем его
                    val pendingUri = pendingExportUri
                    if (pendingUri != null) {
                        pendingExportUri = null
                        Log.d("BumpSense", "📤 Выполняем отложенный экспорт")
                        doExportAllTracks(pendingUri)
                    } else {
                        _uiState.update {
                            it.copy(snackbarMessage = "Запись маршрута завершена")
                        }
                    }
                }
            }
        }
    }

    // ===== ИНИЦИАЛИЗАЦИЯ =====

    init {
        loadHistoryTracks()
        registerReceiver()
        startGpsTracking()
    }

    // ===== GPS ТРЕКИНГ =====

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
                // Обновляем список в режиме редактирования, если он активен
                _trackEditState.update {
                    it.copy(tracks = tracks)
                }
            }
        }
    }

    // ===== УПРАВЛЕНИЕ ЗАПИСЬЮ =====

    fun toggleRecording() {
        val context = getApplication<Application>()
        if (_uiState.value.isRecording) {
            Log.d("BumpSense", "⏹️ Остановка записи (GPS продолжает работать)")
            RecordingService.stopRecording(context)
            // Сразу меняем состояние, не ждём broadcast
            _uiState.update { it.copy(isRecording = false) }
        } else {
            Log.d("BumpSense", "▶️ Начало записи")
            RecordingService.startRecording(context)
            _uiState.update {
                it.copy(
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
        _uiState.update {
            it.copy(currentTrackPoints = it.currentTrackPoints + point)
        }
    }

    fun clearCurrentTrack() {
        _uiState.update { it.copy(currentTrackPoints = emptyList()) }
    }

    // ===== РАЗРЕШЕНИЯ =====

    fun updatePermissionState(granted: Boolean) {
        _uiState.update { it.copy(locationPermissionGranted = granted) }
        if (granted) {
            startGpsTracking()
        } else {
            _uiState.update { it.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
        }
    }

    // ===== ЭКСПОРТ / ИМПОРТ =====

    fun setShowExportDialog(show: Boolean) {
        _showExportDialog.value = show
    }

    /**
     * Экспортирует все треки из базы в GeoJSON файл.
     * Если в момент вызова идёт запись — сначала останавливает её,
     * сохраняет текущий трек, и только потом делает экспорт.
     */
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
                    _uiState.update { it.copy(snackbarMessage = "Нет треков для экспорта") }
                    return@launch
                }

                val jsonString = GeoJsonMapper.tracksToGeoJson(allTracks)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }

                val totalPoints = allTracks.sumOf { it.points.size }
                _uiState.update {
                    it.copy(snackbarMessage = "Экспортировано треков: ${allTracks.size}, точек: $totalPoints")
                }
                Log.d("BumpSense", "✅ Экспортировано треков: ${allTracks.size}, точек: $totalPoints")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(snackbarMessage = "Ошибка при экспорте: ${e.message}") }
            }
        }
    }

    /**
     * Импортирует все треки из GeoJSON файла.
     * Перед импортом база полностью очищается.
     */
    fun importTracks(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update { it.copy(snackbarMessage = "Не удалось открыть файл") }
                    return@launch
                }

                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                Log.d("BumpSense", "📥 Размер файла: ${jsonString.length} символов")

                if (jsonString.isEmpty()) {
                    _uiState.update { it.copy(snackbarMessage = "Файл пустой") }
                    return@launch
                }

                val tracks = GeoJsonMapper.geoJsonToTracks(jsonString)
                Log.d("BumpSense", "📥 Распаршено треков: ${tracks.size}")

                if (tracks.isNotEmpty()) {
                    // Очищаем базу перед импортом
                    Log.d("BumpSense", "🗑️ Очистка базы перед импортом")
                    trackRepository.clearDatabase()

                    // Импортируем все треки без проверок
                    var totalPoints = 0
                    tracks.forEach { track ->
                        trackRepository.insertTrack(track)
                        totalPoints += track.points.size
                        Log.d("BumpSense", "📥 Трек '${track.name}': ${track.points.size} точек")
                    }

                    _uiState.update {
                        it.copy(snackbarMessage = "Импортировано треков: ${tracks.size}, точек: $totalPoints")
                    }
                    Log.d("BumpSense", "✅ Импортировано треков: ${tracks.size}, точек: $totalPoints")
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Неверный формат файла или нет треков") }
                    Log.e("BumpSense", "❌ geoJsonToTracks вернул пустой список")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("BumpSense", "❌ Ошибка импорта: ${e.message}", e)
                _uiState.update { it.copy(snackbarMessage = "Ошибка при импорте: ${e.message}") }
            }
        }
    }

    /**
     * Добавляет треки из GeoJSON файла в существующую базу.
     * НЕ очищает базу перед импортом.
     * Проверяет дубликаты по startTime (если трек с таким же временем уже есть — пропускаем).
     */
    fun appendTracks(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.update { it.copy(snackbarMessage = "Не удалось открыть файл") }
                    return@launch
                }

                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                Log.d("BumpSense", "📥 Размер файла: ${jsonString.length} символов")

                if (jsonString.isEmpty()) {
                    _uiState.update { it.copy(snackbarMessage = "Файл пустой") }
                    return@launch
                }

                val tracks = GeoJsonMapper.geoJsonToTracks(jsonString)
                Log.d("BumpSense", " Распаршено треков: ${tracks.size}")

                if (tracks.isNotEmpty()) {
                    // Получаем существующие треки для проверки дубликатов
                    val existingTracks = trackRepository.getAllTracks().first()
                    val existingStartTimes = existingTracks.map { it.startTime }.toSet()

                    var addedCount = 0
                    var skippedCount = 0
                    var totalPoints = 0

                    tracks.forEach { track ->
                        // Проверяем дубликат по startTime
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

                    _uiState.update { it.copy(snackbarMessage = message) }
                    Log.d("BumpSense", "✅ $message")
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Неверный формат файла или нет треков") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("BumpSense", "❌ Ошибка добавления треков: ${e.message}", e)
                _uiState.update { it.copy(snackbarMessage = "Ошибка при добавлении треков: ${e.message}") }
            }
        }
    }

    // ===== РЕДАКТИРОВАНИЕ ТРЕКОВ =====

    /**
     * Вход в режим редактирования треков
     */
    fun enterEditMode() {
        _isEditMode.value = true
        viewModelScope.launch {
            val allTracks = trackRepository.getAllTracks().first()
            _trackEditState.update {
                it.copy(
                    tracks = allTracks,
                    focusedTrackId = null
                )
            }
        }
    }

    /**
     * Выход из режима редактирования
     */
    fun exitEditMode() {
        _isEditMode.value = false
        _trackEditState.update { it.copy(focusedTrackId = null) }
        _cameraBounds.value = null

        // ✅ Загружаем все треки из базы и показываем их
        viewModelScope.launch {
            val allTracks = trackRepository.getAllTracks().first()
            _uiState.update {
                it.copy(
                    isHistoryVisible = true,
                    historyTracks = allTracks.map { track -> track.points },
                    currentTrackPoints = emptyList()
                )
            }
            Log.d("BumpSense", "📋 Выход из режима редактирования, загружено треков: ${allTracks.size}")
        }
    }

    /**
     * Сфокусироваться на треке: показать только его и центрировать камеру на его области
     */
    fun focusOnTrack(trackId: Long) {
        viewModelScope.launch {
            val track = trackRepository.getTrackById(trackId)
            if (track != null && track.points.isNotEmpty()) {
                _trackEditState.update { it.copy(focusedTrackId = trackId) }

                // Вычисляем bounds трека
                val bounds = calculateTrackBounds(track.points)
                _cameraBounds.value = bounds

                // Показываем только выбранный трек
                _uiState.update {
                    it.copy(
                        isHistoryVisible = true,
                        currentTrackPoints = track.points,
                        historyTracks = listOf(track.points)
                    )
                }

                Log.d("BumpSense", "🎯 Фокус на треке #$trackId: bounds=$bounds")
            }
        }
    }

    /**
     * Удалить трек
     */
    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            trackRepository.deleteTrack(track.id)
            // Обновляем список
            val updatedTracks = _trackEditState.value.tracks.filter { it.id != track.id }
            _trackEditState.update { it.copy(tracks = updatedTracks) }

            // Если удалённый трек был в фокусе — сбрасываем фокус
            if (_trackEditState.value.focusedTrackId == track.id) {
                _trackEditState.update { it.copy(focusedTrackId = null) }
                _cameraBounds.value = null
            }

            _uiState.update { it.copy(snackbarMessage = "Трек удален") }
        }
    }

    /**
     * Сбросить фокус (показать все треки)
     */
    fun clearTrackFocus() {
        _trackEditState.update { it.copy(focusedTrackId = null) }
        _cameraBounds.value = null
        _uiState.update { it.copy(isHistoryVisible = true) }
    }

    /**
     * Вычисляет bounding box для списка точек трека
     */
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

        // Добавляем небольшой отступ (10%)
        val latPadding = (maxLat - minLat) * 0.1
        val lonPadding = (maxLon - minLon) * 0.1

        return CameraBounds(
            minLat = minLat - latPadding,
            maxLat = maxLat + latPadding,
            minLon = minLon - lonPadding,
            maxLon = maxLon + lonPadding
        )
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
                // Обновляем список в режиме редактирования
                _trackEditState.update { it.copy(tracks = emptyList()) }
                Log.d("BumpSense", "🗑️ База данных очищена")
            } catch (e: Exception) {
                Log.e("BumpSense", "❌ Ошибка при очистке БД", e)
                _uiState.update { it.copy(snackbarMessage = "Ошибка при очистке БД") }
            } finally {
                _showClearDbDialog.value = false
            }
        }
    }

    // ===== SNACKBAR =====

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ===== ЖИЗНЕННЫЙ ЦИКЛ =====

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        try {
            getApplication<Application>().unregisterReceiver(trackPointReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}