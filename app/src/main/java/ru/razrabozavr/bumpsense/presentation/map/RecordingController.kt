package ru.razrabozavr.bumpsense.presentation.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.razrabozavr.bumpsense.service.RecordingService

/**
 * Контроллер управления записью трека.
 * Отвечает за:
 * - Запуск и остановку записи через RecordingService
 * - Хранение состояния записи (isRecording)
 * - Инкапсуляцию логики взаимодействия с сервисом
 */
class RecordingController(
    private val context: Context
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /**
     * Запускает запись трека.
     * Отправляет команду в RecordingService через foreground service.
     */
    fun startRecording() {
        Log.d("RecordingController", "🎬 startRecording")

        if (_isRecording.value) {
            Log.w("RecordingController", "⚠️ Запись уже активна")
            return
        }

        _isRecording.value = true
        RecordingService.startRecording(context)

        Log.d("RecordingController", "✅ Запись запущена")
    }

    /**
     * Останавливает запись трека.
     * Отправляет команду остановки в RecordingService.
     */
    fun stopRecording() {
        Log.d("RecordingController", "⏹️ stopRecording")

        if (!_isRecording.value) {
            Log.w("RecordingController", "⚠️ Запись не активна")
            return
        }

        _isRecording.value = false
        RecordingService.stopRecording(context)

        Log.d("RecordingController", "✅ Запись остановлена")
    }

    /**
     * Принудительно устанавливает состояние записи.
     * Используется для синхронизации с внешними событиями (например, от broadcast receiver).
     */
    fun setRecordingState(isRecording: Boolean) {
        Log.d("RecordingController", "🔄 setRecordingState: $isRecording")
        _isRecording.value = isRecording
    }

    /**
     * Возвращает текущее состояние записи.
     */
    fun isCurrentlyRecording(): Boolean {
        return _isRecording.value
    }
}