package ru.razrabozavr.bumpsense.domain.usecase

import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository

class SaveTrackUseCase(
    private val repository: TrackRepository
) {
    suspend operator fun invoke(track: Track): Long {
        return repository.insertTrack(track)
    }
}