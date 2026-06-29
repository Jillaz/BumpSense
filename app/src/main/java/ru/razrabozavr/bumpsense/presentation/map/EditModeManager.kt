package ru.razrabozavr.bumpsense.presentation.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.razrabozavr.bumpsense.domain.model.Track

data class TrackEditState(
    val tracks: List<Track> = emptyList(),
    val selectedTab: Int = 0,
    val focusedTrackId: Long? = null
)

/**
 * Менеджер режима редактирования треков.
 */
class EditModeManager {
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _trackEditState = MutableStateFlow(TrackEditState())
    val trackEditState: StateFlow<TrackEditState> = _trackEditState.asStateFlow()

    private val _isHistoryVisible = MutableStateFlow(true)
    val isHistoryVisible: StateFlow<Boolean> = _isHistoryVisible.asStateFlow()

    fun enterEditMode(tracks: List<Track>) {
        _isEditMode.value = true
        _trackEditState.value = TrackEditState(tracks = tracks)
    }

    fun exitEditMode() {
        _isEditMode.value = false
        _trackEditState.value = TrackEditState()
    }

    fun selectTrackTab(tab: Int) {
        _trackEditState.value = _trackEditState.value.copy(selectedTab = tab)
    }

    fun focusOnTrack(trackId: Long) {
        _trackEditState.value = _trackEditState.value.copy(focusedTrackId = trackId)
    }

    fun toggleHistoryVisibility() {
        _isHistoryVisible.value = !_isHistoryVisible.value
    }

    fun updateTracks(tracks: List<Track>) {
        if (_isEditMode.value) {
            _trackEditState.value = _trackEditState.value.copy(tracks = tracks)
        }
    }
}