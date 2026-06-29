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
import java.util.Date
import java.util.Locale

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
    private var currentTrack: Track? = null // ✅ Хранит метаданные текущего трека (имя, время начала)
    private val trackPoints = mutableListOf<TrackPoint>()

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

        // Приобретаем WakeLock на 10 минут
        wakeLock?.acquire(10 * 60 * 1000L)
        Log.d("BumpSense", "🔒 WakeLock acquired (10 минут)")

        recordingJob = serviceScope.launch {
            try {
                val trackName = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                val track = Track(
                    name = trackName,
                    startTime = System.currentTimeMillis()
                )
                currentTrack = track // ✅ Сохраняем ссылку на текущий трек

                val app = application as BumpSenseApp
                currentTrackId = app.trackRepository.insertTrack(track)
                dataCollector.setTrackId(currentTrackId)
                bumpIndexCalculator.reset()
                trackPoints.clear()

                // Запоминаем время начала текущего трека
                currentTrackStartTime = System.currentTimeMillis()

                // Запускаем таймер автосохранения
                val autoSaveInterval = app.appPreferences.autoSaveIntervalMinutes
                startAutoSaveTimer(autoSaveInterval)

                Log.d("BumpSense", "🎬 RecordingService: трек создан, ID=$currentTrackId, автосохранение каждые $autoSaveInterval мин")

                dataCollector.collectTrackPoints()
                    .onEach { trackPoint ->
                        trackPoints.add(trackPoint)

                        Log.d("BumpSense", "📍 RecordingService: точка #${trackPoints.size}, bump=${trackPoint.bumpIndex}")

                        // Обновляем ближайшие исторические точки
                        app.trackRepository.updateNearbyPoints(
                            latitude = trackPoint.latitude,
                            longitude = trackPoint.longitude,
                            bumpIndex = trackPoint.bumpIndex,
                            radiusMeters = 10.0
                        )

                        // Сохраняем в БД каждые 5 точек
                        if (trackPoints.size % 5 == 0) {
                            // Продлеваем WakeLock ещё на 10 минут
                            wakeLock?.acquire(10 * 60 * 1000L)

                            // ✅ Используем currentTrack, чтобы брать актуальные name и startTime
                            currentTrack?.let { t ->
                                app.trackRepository.insertTrack(
                                    t.copy(
                                        id = currentTrackId,
                                        points = trackPoints.toList()
                                    )
                                )
                            }
                            Log.d("BumpSense", "💾 RecordingService: сохранено в БД, точек=${trackPoints.size}, WakeLock продлён")
                        }

                        // Отправляем обновление в UI
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

    // Запуск таймера автосохранения
    private fun startAutoSaveTimer(intervalMinutes: Int) {
        autoSaveJob?.cancel()

        if (intervalMinutes <= 0) {
            Log.d("BumpSense", "♾️ Автосохранение отключено")
            return
        }

        Log.d("BumpSense", "⏱️ Автосохранение каждые $intervalMinutes минут")

        autoSaveJob = serviceScope.launch {
            while (isActive) {
                delay(intervalMinutes * 60 * 1000L)
                Log.d("BumpSense", "⏱️ Автосохранение: ротация трека")
                rotateTrack()
            }
        }
    }

    // Ротация трека: сохранение текущего и создание нового
    private suspend fun rotateTrack() {
        val app = application as BumpSenseApp

        // 1. Сохраняем текущий трек с endTime
        currentTrack?.let { t ->
            val finishedTrack = t.copy(
                id = currentTrackId,
                endTime = System.currentTimeMillis(),
                points = trackPoints.toList()
            )
            app.trackRepository.insertTrack(finishedTrack)
        }

        val savedPointsCount = trackPoints.size
        Log.d("BumpSense", "💾 Автосохранение: трек #$currentTrackId сохранён, точек=$savedPointsCount")

        // 2. Создаём новый трек
        currentTrackStartTime = System.currentTimeMillis()
        val newTrack = Track(
            name = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date(currentTrackStartTime)),
            startTime = currentTrackStartTime
        )
        currentTrack = newTrack // ✅ Обновляем ссылку на новый трек
        currentTrackId = app.trackRepository.insertTrack(newTrack)
        dataCollector.setTrackId(currentTrackId)
        trackPoints.clear()

        // 3. Продлеваем WakeLock
        wakeLock?.acquire(10 * 60 * 1000L)

        Log.d("BumpSense", "🆕 Автосохранение: создан новый трек #$currentTrackId")

        // 4. Уведомляем UI о ротации
        sendBroadcast(Intent(ACTION_TRACK_ROTATED).apply {
            setPackage(packageName)
            putExtra(EXTRA_PREVIOUS_TRACK_POINTS, savedPointsCount)
        })
    }

    private fun stopRecording() {
        Log.d("BumpSense", "⏹️ RecordingService: stopRecording")

        recordingJob?.cancel()
        recordingJob = null

        // Отменяем таймер автосохранения
        autoSaveJob?.cancel()
        autoSaveJob = null

        // Освобождаем WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("BumpSense", "🔓 WakeLock released")
            }
        }

        serviceScope.launch {
            val app = application as BumpSenseApp

            // Сохраняем финальный трек
            if (trackPoints.isNotEmpty()) {
                val track = Track(
                    id = currentTrackId,
                    name = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()),
                    startTime = currentTrack?.startTime ?: (System.currentTimeMillis() - (trackPoints.size * 2000L)),
                    endTime = System.currentTimeMillis(),
                    points = trackPoints.toList()
                )

                app.trackRepository.insertTrack(track)
                Log.d("BumpSense", "💾 RecordingService: финальное сохранение, точек=${trackPoints.size}")
            }

            // Отправляем уведомление об остановке
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

        // Отменяем таймер автосохранения
        autoSaveJob?.cancel()
        autoSaveJob = null

        // Гарантированное освобождение WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("BumpSense", "🔓 WakeLock released (onDestroy)")
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