package ru.razrabozavr.bumpsense

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import ru.razrabozavr.bumpsense.data.local.AppDatabase
import ru.razrabozavr.bumpsense.data.repository.TrackRepositoryImpl
import ru.razrabozavr.bumpsense.data.settings.AppPreferences
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository

class BumpSenseApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var trackRepository: TrackRepository
        private set

    lateinit var appPreferences: AppPreferences
        private set

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