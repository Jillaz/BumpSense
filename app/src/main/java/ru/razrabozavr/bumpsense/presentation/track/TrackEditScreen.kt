package ru.razrabozavr.bumpsense.presentation.track

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.razrabozavr.bumpsense.R
import ru.razrabozavr.bumpsense.domain.model.Track

enum class TrackListTab {
    ALL,
    VISIBLE
}

data class TrackEditUiState(
    val allTracks: List<Track> = emptyList(),
    val visibleTracks: List<Track> = emptyList(),
    val currentTab: TrackListTab = TrackListTab.ALL,
    val focusedTrackId: Long? = null
) {
    val currentTracks: List<Track>
        get() = when (currentTab) {
            TrackListTab.ALL -> allTracks
            TrackListTab.VISIBLE -> visibleTracks
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackEditScreen(
    uiState: TrackEditUiState,
    onBackClick: () -> Unit,
    onTabChange: (TrackListTab) -> Unit,
    onTrackClick: (Long) -> Unit,
    onDeleteClick: (Track) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }

    // ✅ ИСПРАВЛЕНИЕ (Вариант К): Сохранение позиции скролла
    val listState = rememberLazyListState()

    // ✅ ИСПРАВЛЕНИЕ (Вариант К): Прокрутка к сфокусированному треку
    LaunchedEffect(uiState.focusedTrackId, uiState.currentTracks) {
        val focusedId = uiState.focusedTrackId ?: return@LaunchedEffect
        val index = uiState.currentTracks.indexOfFirst { it.id == focusedId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.3f)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Заголовок с кнопкой "Назад"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.edit_tracks_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Вкладки
                TabRow(
                    selectedTabIndex = when (uiState.currentTab) {
                        TrackListTab.ALL -> 0
                        TrackListTab.VISIBLE -> 1
                    },
                    modifier = Modifier.height(36.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = uiState.currentTab == TrackListTab.ALL,
                        onClick = { onTabChange(TrackListTab.ALL) },
                        text = {
                            Text(
                                text = "Все треки (${uiState.allTracks.size})",
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    )
                    Tab(
                        selected = uiState.currentTab == TrackListTab.VISIBLE,
                        onClick = { onTabChange(TrackListTab.VISIBLE) },
                        text = {
                            Text(
                                text = "На карте (${uiState.visibleTracks.size})",
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    )
                }

                // Разделитель
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Содержимое
                if (uiState.currentTracks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.currentTab == TrackListTab.VISIBLE)
                                "Нет треков в видимой области"
                            else
                                stringResource(R.string.edit_tracks_empty),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // ✅ ИСПРАВЛЕНИЕ (Вариант К): Используем rememberLazyListState для сохранения позиции
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = uiState.currentTracks,
                            key = { it.id }
                        ) { track ->
                            // ✅ ИСПРАВЛЕНИЕ (Вариант К): Анимация появления элемента
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(300)) +
                                        slideInHorizontally(
                                            initialOffsetX = { -it / 2 },
                                            animationSpec = tween(300)
                                        )
                            ) {
                                TrackItem(
                                    track = track,
                                    isFocused = uiState.focusedTrackId == track.id,
                                    onClick = { onTrackClick(track.id) },
                                    onDeleteClick = {
                                        trackToDelete = track
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Диалог подтверждения удаления
        if (showDeleteDialog && trackToDelete != null) {
            val safeTrack = trackToDelete ?: return@Box

            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    trackToDelete = null
                },
                title = { Text(stringResource(R.string.dialog_delete_track_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.dialog_delete_track_message,
                            safeTrack.name
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteClick(safeTrack)
                            onDeleteConfirm()
                            showDeleteDialog = false
                            trackToDelete = null
                        }
                    ) {
                        Text(
                            stringResource(R.string.button_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            trackToDelete = null
                            onDeleteCancel()
                        }
                    ) {
                        Text(stringResource(R.string.button_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun TrackItem(
    track: Track,
    isFocused: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val dateText = dateFormat.format(Date(track.startTime))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isFocused)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(R.string.edit_tracks_points_count, track.points.size),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = if (isFocused)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.button_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}