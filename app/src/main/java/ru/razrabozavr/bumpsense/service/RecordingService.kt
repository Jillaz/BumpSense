package ru.razrabozavr.bumpsense.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.razrabozavr.bumpsense.BumpSenseApp
import ru.razrabozavr.bumpsense.R
import ru.razrabozavr.bumpsense.data.location.LocationClient
import ru.razrabozavr.bumpsense.data.sensor.AccelerometerClient
import ru.razrabozavr.bumpsense.data.sensor.BumpIndexCalculator
import ru.razrabozavr.bumpsense.data.sensor.RecordingDataCollector
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.model.TrackPoint
import ru.razrabozavr.bumpsense.presentation.MainActivity
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class RecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    private lateinit var locationClient: LocationClient
    private lateinit var accelerometerClient: AccelerometerClient
    private lateinit var bumpIndexCalculator: BumpIndexCalculator
    private lateinit var dataCollector: RecordingDataCollector

    // WakeLock для удержания CPU активным во время записи
    private var wakeLock: PowerManager.WakeLock? = null

    // Таймер автосохранения
    private var autoSaveJob: Job? = null
    private var currentTrackStartTime: Long = 0L

    private var currentTrackId: Long = 0L
    private var currentTrack: Track? = null

    // Thread-safe коллекция с ограничением размера (макс 1000 точек в памяти)
    private val trackPoints = Collections.synchronizedList(mutableListOf<TrackPoint>())

    // ✅ ИСПРАВЛЕНИЕ: Ограничение размера буфера
    private val maxBufferSize = 1000

    // ✅ ИСПРАВЛЕНИЕ (Вариант Б): Счётчик несохранённых точек
    private var unsavedPointsCount = 0

    override fun onCreate() {
        super.onCreate()
        Log.d("BumpSense", "🔧 RecordingService: onCreate")

        val app = application as BumpSenseApp
        locationClient = LocationClient(this)
        accelerometerClient = AccelerometerClient(this)
        bumpIndexCalculator = BumpIndexCalculator()
        dataCollector = RecordingDataCollector(locationClient, accelerometerClient, bumpIndexCalculator)

        // Инициализация WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BumpSense::RecordingWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BumpSense", "📥 RecordingService: onStartCommand, action=${intent?.action}")

        when (intent?.action) {
            ACTION_START_RECORDING -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        Log.d("BumpSense", "🎬 RecordingService: startRecording")

        // ✅ ИСПРАВЛЕНИЕ: Переключаем GPS в режим высокой точности для записи
        locationClient.priority = Priority.PRIORITY_HIGH_ACCURACY
        Log.d("BumpSense", "🛰️ GPS переключён в HIGH_ACCURACY для записи")

        // ✅ ИСПРАВЛЕНИЕ: Безопасное получение WakeLock с try-finally
        try {
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(10 * 60 * 1000L)
                    Log.d("BumpSense", "🔒 WakeLock acquired (10 минут)")
                } else {
                    Log.d("BumpSense", "🔒 WakeLock уже удерживается")
                }
            }
        } catch (e: Exception) {
            Log.e("BumpSense", "❌ Ошибка при получении WakeLock", e)
        }

        recordingJob = serviceScope.launch {
            try {
                val trackName = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                val track = Track(
                    name = trackName,
                    startTime = System.currentTimeMillis()
                )
                currentTrack = track

                val app = application as BumpSenseApp
                locationClient.minUpdateDistanceMeters = app.appPreferences.minUpdateDistanceMeters
                Log.d("BumpSense", "📏 Мин. смещение для записи: ${app.appPreferences.minUpdateDistanceMeters} м")

                currentTrackId = app.trackRepository.insertTrack(track)
                dataCollector.setTrackId(currentTrackId)
                bumpIndexCalculator.reset()
                trackPoints.clear()
                unsavedPointsCount = 0

                currentTrackStartTime = System.currentTimeMillis()

                val autoSaveInterval = app.appPreferences.autoSaveIntervalMinutes
                startAutoSaveTimer(autoSaveInterval)

                Log.d("BumpSense", "🎬 RecordingService: трек создан, ID=$currentTrackId, автосохранение каждые $autoSaveInterval мин")

                dataCollector.collectTrackPoints()
                    .onEach { trackPoint ->
                        trackPoints.add(trackPoint)
                        unsavedPointsCount++

                        Log.d("BumpSense", "📍 RecordingService: точка #${trackPoints.size}, bump=${trackPoint.bumpIndex}")

                        app.trackRepository.updateNearbyPoints(
                            latitude = trackPoint.latitude,
                            longitude = trackPoint.longitude,
                            bumpIndex = trackPoint.bumpIndex,
                            radiusMeters = 10.0
                        )

                        // ✅ ИСПРАВЛЕНИЕ (Вариант Б): Сохраняем каждые 50 точек через batch insert
                        if (unsavedPointsCount >= 50) {
                            // ✅ ИСПРАВЛЕНИЕ: Безопасное продление WakeLock
                            try {
                                wakeLock?.let {
                                    if (!it.isHeld) {
                                        it.acquire(10 * 60 * 1000L)
                                        Log.d("BumpSense", "🔒 WakeLock продлён")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("BumpSense", "❌ Ошибка при продлении WakeLock", e)
                            }

                            // ✅ ИСПРАВЛЕНИЕ (Вариант Б): Batch insert только новых точек
                            saveNewPointsBatch()
                        }

                        // ✅ ИСПРАВЛЕНИЕ: Ограничение размера буфера
                        if (trackPoints.size > maxBufferSize) {
                            Log.d("BumpSense", "⚠️ Буфер переполнен (${trackPoints.size}), сохраняем и очищаем")
                            saveAndClearBuffer()
                        }

                        sendBroadcast(Intent(ACTION_TRACK_POINT_UPDATE).apply {
                            setPackage(packageName)
                            putExtra(EXTRA_LATITUDE, trackPoint.latitude)
                            putExtra(EXTRA_LONGITUDE, trackPoint.longitude)
                            putExtra(EXTRA_BUMP_INDEX, trackPoint.bumpIndex)
                        })
                    }
                    .catch { e ->
                        Log.e("BumpSense", "❌ RecordingService: ошибка в потоке", e)
                    }
                    .launchIn(this)

                Log.d("BumpSense", "✅ RecordingService: сбор данных запущен")
            } catch (e: Exception) {
                Log.e("BumpSense", "❌ RecordingService: критическая ошибка", e)
            }
        }
    }

    // ✅ ИСПРАВЛЕНИЕ (Вариант Б): Batch insert только новых точек
    private suspend fun saveNewPointsBatch() {
        val app = application as BumpSenseApp

        val pointsToSave: List<TrackPoint>
        synchronized(trackPoints) {
            if (unsavedPointsCount == 0) return
            // Берём последние unsavedPointsCount точек
            val startIndex = trackPoints.size - unsavedPointsCount
            pointsToSave = trackPoints.subList(startIndex, trackPoints.size).toList()
            unsavedPointsCount = 0
        }

        try {
            app.trackRepository.insertPoints(currentTrackId, pointsToSave)
            Log.d("BumpSense", "💾 RecordingService: batch insert ${pointsToSave.size} точек")
        } catch (e: Exception) {
            Log.e("BumpSense", "❌ Ошибка batch insert", e)
        }
    }

    // ✅ ИСПРАВЛЕНИЕ: Метод для сохранения и очистки буфера
    private suspend fun saveAndClearBuffer() {
        val app = application as BumpSenseApp

        val pointsToSave: List<TrackPoint>
        synchronized(trackPoints) {
            pointsToSave = trackPoints.toList()
            trackPoints.clear()
            unsavedPointsCount = 0
        }

        try {
            app.trackRepository.insertPoints(currentTrackId, pointsToSave)
            Log.d("BumpSense", "💾 Буфер сохранён в БД, точек=${pointsToSave.size}")
        } catch (e: Exception) {
            Log.e("BumpSense", "❌ Ошибка сохранения буфера", e)
        }
    }

    private fun startAutoSaveTimer(intervalMinutes: Int) {
        autoSaveJob?.cancel()

        if (intervalMinutes <= 0) {
            Log.d("BumpSense", "♾️ Автосохранение отключено")
            return
        }

        Log.d("BumpSense", "⏱️ Автосохранение каждые $intervalMinutes минут")

        autoSaveJob = serviceScope.launch {
            while (isActive) {
                delay((intervalMinutes * 60 * 1000L).milliseconds)
                Log.d("BumpSense", "⏱️ Автосохранение: ротация трека")
                rotateTrack()
            }
        }
    }

    private suspend fun rotateTrack() {
        val app = application as BumpSenseApp

        // ✅ ИСПРАВЛЕНИЕ (Вариант Б): Сохраняем оставшиеся точки и обновляем метаданные
        saveNewPointsBatch()

        val endTime = System.currentTimeMillis()
        app.trackRepository.updateTrackMetadata(currentTrackId, endTime, 0.0)

        val savedPointsCount = trackPoints.size
        Log.d("BumpSense", "💾 Автосохранение: трек #$currentTrackId сохранён, точек=$savedPointsCount")

        currentTrackStartTime = System.currentTimeMillis()
        val newTrack = Track(
            name = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date(currentTrackStartTime)),
            startTime = currentTrackStartTime
        )
        currentTrack = newTrack
        currentTrackId = app.trackRepository.insertTrack(newTrack)
        dataCollector.setTrackId(currentTrackId)
        trackPoints.clear()
        unsavedPointsCount = 0

        // ✅ ИСПРАВЛЕНИЕ: Безопасное получение WakeLock
        try {
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(10 * 60 * 1000L)
                }
            }
        } catch (e: Exception) {
            Log.e("BumpSense", "❌ Ошибка при получении WakeLock в rotateTrack", e)
        }

        Log.d("BumpSense", "🆕 Автосохранение: создан новый трек #$currentTrackId")

        sendBroadcast(Intent(ACTION_TRACK_ROTATED).apply {
            setPackage(packageName)
            putExtra(EXTRA_PREVIOUS_TRACK_POINTS, savedPointsCount)
        })
    }

    private fun stopRecording() {
        Log.d("BumpSense", "⏹️ RecordingService: stopRecording")

        recordingJob?.cancel()
        recordingJob = null

        autoSaveJob?.cancel()
        autoSaveJob = null

        // ✅ ИСПРАВЛЕНИЕ: Безопасное освобождение WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    Log.d("BumpSense", "🔓 WakeLock released")
                } catch (e: Exception) {
                    Log.e("BumpSense", "❌ Ошибка при освобождении WakeLock", e)
                }
            }
        }

        serviceScope.launch {
            val app = application as BumpSenseApp

            // ✅ ИСПРАВЛЕНИЕ (Вариант Б): Сохраняем оставшиеся точки и обновляем метаданные
            saveNewPointsBatch()

            val endTime = System.currentTimeMillis()
            app.trackRepository.updateTrackMetadata(currentTrackId, endTime, 0.0)

            Log.d("BumpSense", "💾 RecordingService: финальное сохранение, точек=${trackPoints.size}")

            sendBroadcast(Intent(ACTION_RECORDING_STOPPED).apply {
                setPackage(packageName)
            })
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, BumpSenseApp.RECORDING_CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("BumpSense", "🔚 RecordingService: onDestroy")

        autoSaveJob?.cancel()
        autoSaveJob = null

        // ✅ ИСПРАВЛЕНИЕ: Безопасное освобождение WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                try {
                    it.release()
                    Log.d("BumpSense", "🔓 WakeLock released (onDestroy)")
                } catch (e: Exception) {
                    Log.e("BumpSense", "❌ Ошибка при освобождении WakeLock в onDestroy", e)
                }
            }
        }

        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_RECORDING = "action_start_recording"
        const val ACTION_STOP_RECORDING = "action_stop_recording"
        const val ACTION_TRACK_POINT_UPDATE = "action_track_point_update"
        const val ACTION_RECORDING_STOPPED = "action_recording_stopped"
        const val ACTION_TRACK_ROTATED = "action_track_rotated"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_BUMP_INDEX = "extra_bump_index"
        const val EXTRA_PREVIOUS_TRACK_POINTS = "extra_previous_track_points"

        private const val NOTIFICATION_ID = 1

        fun startRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START_RECORDING
            }
            context.startForegroundService(intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }
}