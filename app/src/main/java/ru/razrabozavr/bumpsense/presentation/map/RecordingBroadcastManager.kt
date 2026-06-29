package ru.razrabozavr.bumpsense.presentation.map

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import ru.razrabozavr.bumpsense.service.RecordingService

/**
 * Данные о новой точке трека, полученной от RecordingService.
 */
data class TrackPointUpdate(
    val latitude: Double,
    val longitude: Double,
    val bumpIndex: Int
)

/**
 * Менеджер broadcast-событий от RecordingService.
 * Отвечает за:
 * - Регистрацию и отписку от BroadcastReceiver
 * - Парсинг входящих Intent-ов
 * - Передачу событий через callback-и
 */
class RecordingBroadcastManager(
    private val context: Context,
    private val onTrackPointUpdate: (TrackPointUpdate) -> Unit,
    private val onRecordingStopped: () -> Unit,
    private val onTrackRotated: (previousPointsCount: Int) -> Unit
) {
    private var isRegistered = false

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                RecordingService.ACTION_TRACK_POINT_UPDATE -> {
                    val latitude = intent.getDoubleExtra(RecordingService.EXTRA_LATITUDE, 0.0)
                    val longitude = intent.getDoubleExtra(RecordingService.EXTRA_LONGITUDE, 0.0)
                    val bumpIndex = intent.getIntExtra(RecordingService.EXTRA_BUMP_INDEX, 0)

                    Log.d("RecordingBroadcastManager", "📍 Новая точка: lat=$latitude, lon=$longitude, bump=$bumpIndex")

                    onTrackPointUpdate(TrackPointUpdate(latitude, longitude, bumpIndex))
                }

                RecordingService.ACTION_RECORDING_STOPPED -> {
                    Log.d("RecordingBroadcastManager", "⏹️ Запись остановлена")
                    onRecordingStopped()
                }

                RecordingService.ACTION_TRACK_ROTATED -> {
                    val previousPoints = intent.getIntExtra(
                        RecordingService.EXTRA_PREVIOUS_TRACK_POINTS, 0
                    )
                    Log.d("RecordingBroadcastManager", "🔄 Трек ротирован, предыдущих точек: $previousPoints")
                    onTrackRotated(previousPoints)
                }
            }
        }
    }

    /**
     * Регистрирует BroadcastReceiver для прослушивания событий RecordingService.
     */
    fun register() {
        if (isRegistered) {
            Log.w("RecordingBroadcastManager", "⚠️ Receiver уже зарегистрирован")
            return
        }

        val filter = IntentFilter().apply {
            addAction(RecordingService.ACTION_TRACK_POINT_UPDATE)
            addAction(RecordingService.ACTION_RECORDING_STOPPED)
            addAction(RecordingService.ACTION_TRACK_ROTATED)
        }

        ContextCompat.registerReceiver(
            context,
            broadcastReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        isRegistered = true
        Log.d("RecordingBroadcastManager", "✅ Broadcast receiver зарегистрирован")
    }

    /**
     * Отписывает BroadcastReceiver.
     */
    fun unregister() {
        if (!isRegistered) {
            Log.w("RecordingBroadcastManager", "⚠️ Receiver не зарегистрирован")
            return
        }

        try {
            context.unregisterReceiver(broadcastReceiver)
            isRegistered = false
            Log.d("RecordingBroadcastManager", "✅ Broadcast receiver отменён")
        } catch (e: Exception) {
            Log.e("RecordingBroadcastManager", "❌ Ошибка отмены receiver", e)
        }
    }
}