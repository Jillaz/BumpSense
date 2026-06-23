package ru.razrabozavr.bumpsense

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.maplibre.android.MapLibre
import ru.razrabozavr.bumpsense.data.local.AppDatabase
import ru.razrabozavr.bumpsense.data.repository.TrackRepositoryImpl
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository

class BumpSenseApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var trackRepository: TrackRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Инициализация MapLibre
        MapLibre.getInstance(this)

        // Инициализация базы данных
        database = AppDatabase.getDatabase(this)

        // Инициализация репозитория
        trackRepository = TrackRepositoryImpl(database.trackDao())

        // Создание канала уведомлений
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            RECORDING_CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_channel_description)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val RECORDING_CHANNEL_ID = "recording_channel"

        lateinit var instance: BumpSenseApp
            private set
    }
}