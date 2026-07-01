package ru.razrabozavr.bumpsense.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Типизированные события от RecordingService.
 *
 * ✅ РЕФАКТОРИНГ (Этап 7): Вынесено из MapViewModel для улучшения структуры кода.
 * - Type-safe события вместо Intent extras
 * - Инкапсуляция работы с BroadcastReceiver
 * - Возможность тестирования без Android Context
 */
sealed class RecordingServiceEvent {
    data class TrackPointUpdate(
        val latitude: Double,
        val longitude: Double,
        val bumpIndex: Int
    ) : RecordingServiceEvent()

    data object RecordingStopped : RecordingServiceEvent()
}

/**
 * BroadcastReceiver для приёма событий от RecordingService.
 *
 * ✅ РЕФАКТОРИНГ (Этап 7): Вынесено из MapViewModel.
 */
class RecordingServiceReceiver(context: Context) : BroadcastReceiver() {

    private val _events = MutableSharedFlow<RecordingServiceEvent>(replay = 0)
    val events: SharedFlow<RecordingServiceEvent> = _events.asSharedFlow()

    private val filter = IntentFilter().apply {
        addAction(ACTION_TRACK_POINT_UPDATE)
        addAction(ACTION_RECORDING_STOPPED)
    }

    init {
        // Регистрируем receiver при создании
        ContextCompat.registerReceiver(
            context,
            this,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_TRACK_POINT_UPDATE -> {
                val latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                val bumpIndex = intent.getIntExtra(EXTRA_BUMP_INDEX, 0)

                _events.tryEmit(
                    RecordingServiceEvent.TrackPointUpdate(
                        latitude = latitude,
                        longitude = longitude,
                        bumpIndex = bumpIndex
                    )
                )
            }
            ACTION_RECORDING_STOPPED -> {
                _events.tryEmit(RecordingServiceEvent.RecordingStopped)
            }
        }
    }

    /**
     * Создаёт Location из координат события.
     */
    fun createLocationFromEvent(event: RecordingServiceEvent.TrackPointUpdate): Location {
        return Location("service").apply {
            this.latitude = event.latitude
            this.longitude = event.longitude
            time = System.currentTimeMillis()
        }
    }

    companion object {
        const val ACTION_TRACK_POINT_UPDATE = "action_track_point_update"
        const val ACTION_RECORDING_STOPPED = "action_recording_stopped"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_BUMP_INDEX = "extra_bump_index"
    }
}