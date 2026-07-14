package ru.razrabozavr.bumpsense.data.update

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpdateAvailable : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data object ReadyToInstall : UpdateState()
    data class Error(val message: String) : UpdateState()
    data object NoUpdateAvailable : UpdateState()
}