package ru.razrabozavr.bumpsense.data.sensor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccelerometerData(
    val magnitude: Float = 0f,
    val bumpIndex: Int = 0,
    val maxBumpIndex: Int = 0,  // ✅ Новое поле: максимальное значение
    val isAvailable: Boolean = true
)

class AccelerometerViewModel(application: Application) : AndroidViewModel(application) {

    private val accelerometerClient = AccelerometerClient(application)
    private val bumpIndexCalculator = BumpIndexCalculator()

    private val _accelerometerData = MutableStateFlow(AccelerometerData())
    val accelerometerData: StateFlow<AccelerometerData> = _accelerometerData.asStateFlow()

    // Состояние максимального значения
    private var maxBumpIndex = 0
    private var maxBumpIndexResetJob: Job? = null

    init {
        startCollecting()
    }

    private fun startCollecting() {
        accelerometerClient.getAccelerationUpdates()
            .onEach { magnitude ->
                val bumpIndex = bumpIndexCalculator.addSample(magnitude)
                updateMaxBumpIndex(bumpIndex)

                _accelerometerData.update {
                    it.copy(
                        magnitude = magnitude,
                        bumpIndex = bumpIndex,
                        maxBumpIndex = maxBumpIndex,
                        isAvailable = true
                    )
                }
            }
            .catch { e ->
                _accelerometerData.update { AccelerometerData(isAvailable = false) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Обновляет максимальное значение индекса неровности.
     * - Если новый индекс выше текущего максимума → обновляем и запускаем таймер 5 сек.
     * - Если за 5 сек не было новых максимумов → сбрасываем на 0.
     */
    private fun updateMaxBumpIndex(newBumpIndex: Int) {
        if (newBumpIndex > maxBumpIndex) {
            maxBumpIndex = newBumpIndex

            // Отменяем предыдущий таймер сброса
            maxBumpIndexResetJob?.cancel()

            // Запускаем новый таймер на 5 секунд
            maxBumpIndexResetJob = viewModelScope.launch {
                delay(5000)
                maxBumpIndex = 0
                _accelerometerData.update { it.copy(maxBumpIndex = 0) }
            }
        }
    }

    fun reset() {
        bumpIndexCalculator.reset()
        maxBumpIndex = 0
        maxBumpIndexResetJob?.cancel()
        _accelerometerData.update { it.copy(maxBumpIndex = 0) }
    }

    override fun onCleared() {
        super.onCleared()
        maxBumpIndexResetJob?.cancel()
    }
}