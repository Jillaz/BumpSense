package ru.razrabozavr.bumpsense.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.razrabozavr.bumpsense.domain.model.Track
import ru.razrabozavr.bumpsense.domain.repository.TrackRepository

class GetAllTracksUseCase(
    private val repository: TrackRepository
) {
    operator fun invoke(): Flow<List<Track>> {
        return repository.getAllTracks()
    }
}