package ru.razrabozavr.bumpsense.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ru.razrabozavr.bumpsense.data.sensor.AccelerometerViewModel
import ru.razrabozavr.bumpsense.presentation.map.MapScreen
import ru.razrabozavr.bumpsense.presentation.map.MapViewModel
import ru.razrabozavr.bumpsense.presentation.theme.BumpSenseTheme

class MainActivity : ComponentActivity() {
    private val mapViewModel: MapViewModel by viewModels()
    private val accelerometerViewModel: AccelerometerViewModel by viewModels()

    // ✅ НОВОЕ: Lifecycle observer для управления акселерометром
    private val accelerometerLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // ✅ Перезапускаем акселерометр при возврате приложения на передний план
            accelerometerViewModel.restartCollecting()
        }

        override fun onStop(owner: LifecycleOwner) {
            // ✅ Останавливаем акселерометр, если запись не идёт
            if (!mapViewModel.uiState.value.isRecording) {
                accelerometerViewModel.stopCollecting()
            }
            // Если запись идёт - акселерометр продолжает работать для RecordingService
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ Регистрируем lifecycle observer для акселерометра
        ProcessLifecycleOwner.get().lifecycle.addObserver(accelerometerLifecycleObserver)

        setContent {
            // Читаем состояние темы из ViewModel
            val isDarkTheme by mapViewModel.isDarkTheme.collectAsState()
            val colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()

            BumpSenseTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MapScreen(
                        viewModel = mapViewModel,
                        accelerometerViewModel = accelerometerViewModel,
                        onExportClick = { },
                        onImportClick = { }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ Удаляем observer при уничтожении Activity
        ProcessLifecycleOwner.get().lifecycle.removeObserver(accelerometerLifecycleObserver)
    }
}