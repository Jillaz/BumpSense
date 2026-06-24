package ru.razrabozavr.bumpsense.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import ru.razrabozavr.bumpsense.data.sensor.AccelerometerViewModel
import ru.razrabozavr.bumpsense.presentation.map.MapScreen
import ru.razrabozavr.bumpsense.presentation.map.MapViewModel
import ru.razrabozavr.bumpsense.presentation.theme.BumpSenseTheme

class MainActivity : ComponentActivity() {

    private val mapViewModel: MapViewModel by viewModels()
    private val accelerometerViewModel: AccelerometerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BumpSenseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MapScreen(
                        viewModel = mapViewModel,
                        accelerometerViewModel = accelerometerViewModel,
                        onExportClick = { /* TODO: Реализовать экспорт */ },
                        onImportClick = { /* TODO: Реализовать импорт */ }
                    )
                }
            }
        }
    }
}