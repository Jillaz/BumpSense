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
import ru.razrabozavr.bumpsense.presentation.update.UpdateDialog
import ru.razrabozavr.bumpsense.presentation.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val mapViewModel: MapViewModel by viewModels()
    private val accelerometerViewModel: AccelerometerViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private val accelerometerLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            accelerometerViewModel.restartCollecting()
        }
        override fun onStop(owner: LifecycleOwner) {
            if (!mapViewModel.uiState.value.isRecording) {
                accelerometerViewModel.stopCollecting()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ProcessLifecycleOwner.get().lifecycle.addObserver(accelerometerLifecycleObserver)

        updateViewModel.checkForUpdates()

        setContent {
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

                    // ✅ Исправлено: убран параметр activity
                    UpdateDialog(
                        viewModel = updateViewModel,
                        onDismiss = { }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(accelerometerLifecycleObserver)
    }
}