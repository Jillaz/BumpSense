package ru.razrabozavr.bumpsense.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.razrabozavr.bumpsense.R

// ✅ Обновлённый SettingsState с полем автосохранения
data class SettingsState(
    val isDarkTheme: Boolean,
    val gpsIntervalMs: Long,
    val updateRadiusMeters: Double,
    val accelerometerThreshold: Float,
    val autoSaveIntervalMinutes: Int  // ✅ Новое поле (5-60 минут)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsState: SettingsState,
    onBackClick: () -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onGpsIntervalChange: (Long) -> Unit,
    onUpdateRadiusChange: (Double) -> Unit,
    onAccelerometerThresholdChange: (Float) -> Unit,
    onAutoSaveIntervalChange: (Int) -> Unit  // ✅ Новый callback
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Тема
            SettingsCard(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.settings_dark_theme_title),
                description = if (settingsState.isDarkTheme)
                    stringResource(R.string.settings_dark_theme_on)
                else
                    stringResource(R.string.settings_dark_theme_off)
            ) {
                Switch(
                    checked = settingsState.isDarkTheme,
                    onCheckedChange = onDarkThemeChange
                )
            }

            // Частота GPS
            var gpsSliderValue by remember { mutableFloatStateOf(settingsState.gpsIntervalMs.toFloat()) }
            SettingsCard(
                icon = Icons.Default.GpsFixed,
                title = stringResource(R.string.settings_gps_title),
                description = stringResource(R.string.settings_gps_description)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = gpsSliderValue,
                        onValueChange = {
                            gpsSliderValue = it
                            onGpsIntervalChange(it.toLong())
                        },
                        valueRange = 1000f..10000f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_gps_current,
                            (gpsSliderValue / 1000).toInt()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Радиус обновления
            var radiusSliderValue by remember { mutableFloatStateOf(settingsState.updateRadiusMeters.toFloat()) }
            SettingsCard(
                icon = Icons.Default.Radar,
                title = stringResource(R.string.settings_radius_title),
                description = stringResource(R.string.settings_radius_description)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = radiusSliderValue,
                        onValueChange = {
                            radiusSliderValue = it
                            onUpdateRadiusChange(it.toDouble())
                        },
                        valueRange = 5f..50f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_radius_current,
                            radiusSliderValue.toInt()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Порог акселерометра
            var accelSliderValue by remember { mutableFloatStateOf(settingsState.accelerometerThreshold) }
            SettingsCard(
                icon = Icons.Default.Vibration,
                title = stringResource(R.string.settings_accel_title),
                description = stringResource(R.string.settings_accel_description)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = accelSliderValue,
                        onValueChange = {
                            accelSliderValue = it
                            onAccelerometerThresholdChange(it)
                        },
                        valueRange = 1f..20f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_accel_current,
                            accelSliderValue
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ✅ Автосохранение треков (новая карточка)
            var autoSaveValue by remember { mutableFloatStateOf(settingsState.autoSaveIntervalMinutes.toFloat()) }
            SettingsCard(
                icon = Icons.Default.Timer,
                title = stringResource(R.string.settings_autosave_title),
                description = stringResource(R.string.settings_autosave_description)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = autoSaveValue,
                        onValueChange = {
                            autoSaveValue = it
                            onAutoSaveIntervalChange(it.toInt())
                        },
                        valueRange = 5f..60f,
                        steps = 11,  // 12 позиций: 5, 10, 15... 60
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_autosave_current,
                            autoSaveValue.toInt()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            content()

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 14.sp
            )
        }
    }
}