package ru.razrabozavr.bumpsense.data.sensor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class AccelerometerData(
    val magnitude: Float = 0f,
    val bumpIndex: Int = 0,
    val isAvailable: Boolean = true
)

class AccelerometerViewModel(application: Application) : AndroidViewModel(application) {

    private val accelerometerClient = AccelerometerClient(application)
    private val bumpIndexCalculator = BumpIndexCalculator()

    private val _accelerometerData = MutableStateFlow(AccelerometerData())
    val accelerometerData: StateFlow<AccelerometerData> = _accelerometerData.asStateFlow()

    init {
        startCollecting()
    }

    private fun startCollecting() {
        accelerometerClient.getAccelerationUpdates()
            .onEach { magnitude ->
                val bumpIndex = bumpIndexCalculator.addSample(magnitude)
                _accelerometerData.value = AccelerometerData(
                    magnitude = magnitude,
                    bumpIndex = bumpIndex,
                    isAvailable = true
                )
            }
            .catch { e ->
                _accelerometerData.value = AccelerometerData(isAvailable = false)
            }
            .launchIn(viewModelScope)
    }

    fun reset() {
        bumpIndexCalculator.reset()
    }
}