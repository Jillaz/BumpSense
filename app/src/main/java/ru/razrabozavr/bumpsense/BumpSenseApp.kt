package ru.razrabozavr.bumpsense

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import ru.razrabozavr.bumpsense.data.local.AppDatabase
import ru.razrabozavr.bumpsense.data.repository.TrackRepositoryImpl
import ru.razrabozavr.bumpsense.data.settings.AppPreferences
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository

/**
 * ✅ ИСПРАВЛЕНИЕ (Шаг 9): События от RecordingService для UI.
 * Заменяет Broadcast-механизм на type-safe SharedFlow.
 */
sealed class RecordingEvent {
    data class TrackPointUpdate(
        val latitude: Double,
        val longitude: Double,
        val bumpIndex: Int
    ) : RecordingEvent()

    data object RecordingStopped : RecordingEvent()

    data class TrackRotated(
        val previousTrackPoints: Int
    ) : RecordingEvent()
}

class BumpSenseApp : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var trackRepository: TrackRepository
        private set

    lateinit var appPreferences: AppPreferences
        private set

    /**
     * ✅ ИСПРАВЛЕНИЕ (Шаг 9): Общий канал событий от RecordingService.
     * Используется вместо системного Broadcast для type-safe коммуникации.
     */
    private val _recordingEvents = MutableSharedFlow<RecordingEvent>(
        replay = 0,
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val recordingEvents = _recordingEvents.asSharedFlow()

    /**
     * ✅ ИСПРАВЛЕНИЕ (Шаг 9): Метод для отправки событий из RecordingService.
     */
    suspend fun emitRecordingEvent(event: RecordingEvent) {
        _recordingEvents.emit(event)
    }

    /**
     * ✅ ИСПРАВЛЕНИЕ (Шаг 9): Try-emit для синхронного вызова без suspend.
     */
    fun tryEmitRecordingEvent(event: RecordingEvent) {
        _recordingEvents.tryEmit(event)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ✅ ВАЖНО: Инициализация MapLibre ДО создания MapView
        // Используем WellKnownTileServer.MapLibre (бесплатный сервер OSM)
        // API-ключ не требуется (передаём null)
        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre)

        // Инициализация базы данных
        database = AppDatabase.getDatabase(this)
        trackRepository = TrackRepositoryImpl(database.trackDao())

        // Инициализация настроек
        appPreferences = AppPreferences(this)

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            RECORDING_CHANNEL_ID,
            "Запись трека",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомление о записи маршрута"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        lateinit var instance: BumpSenseApp
            private set

        const val RECORDING_CHANNEL_ID = "recording_channel"
    }
}