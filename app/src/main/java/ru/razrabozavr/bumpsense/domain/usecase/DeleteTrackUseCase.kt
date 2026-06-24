package ru.razrabozavr.bumpsense.domain.usecase

import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository

class DeleteTrackUseCase(
    private val repository: TrackRepository
) {
    suspend operator fun invoke(track: Track) {
        // Передаем id трека, а не весь объект
        repository.deleteTrack(track.id)
    }
}