package ru.razrabozavr.bumpsense.presentation.update

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.model.AppUpdateInfo
import ru.rustore.sdk.appupdate.model.InstallState
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability
import ru.razrabozavr.bumpsense.data.update.UpdateManager
import ru.razrabozavr.bumpsense.data.update.UpdateState

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updateManager = UpdateManager(application)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var currentUpdateInfo: AppUpdateInfo? = null
    private var installStateListener: InstallStateUpdateListener? = null

    companion object {
        private const val TAG = "UpdateViewModel"
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            updateManager.checkForUpdates(
                onSuccess = { appUpdateInfo ->
                    currentUpdateInfo = appUpdateInfo
                    when (appUpdateInfo.updateAvailability) {
                        UpdateAvailability.UPDATE_AVAILABLE -> {
                            Log.d(TAG, "Update available")
                            _updateState.value = UpdateState.UpdateAvailable
                        }
                        UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                            Log.d(TAG, "No update available")
                            _updateState.value = UpdateState.NoUpdateAvailable
                        }
                        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                            Log.d(TAG, "Update in progress")
                            registerInstallStateListener()
                        }
                        else -> {
                            Log.d(TAG, "Unknown update availability")
                            _updateState.value = UpdateState.Idle
                        }
                    }
                },
                onFailure = { exception ->
                    val errorMessage = exception.message ?: "Unknown error"

                    // ✅ Обработка ошибки 404 - приложение не найдено в RuStore
                    // Это нормально для debug-сборок или приложений не в RuStore
                    if (errorMessage.contains("404") || errorMessage.contains("Not Found")) {
                        Log.d(TAG, "App not found in RuStore (404) - это нормально для debug-сборки")
                        _updateState.value = UpdateState.Idle
                    } else {
                        Log.e(TAG, "Failed to check for updates: $errorMessage")
                        _updateState.value = UpdateState.Error(
                            "Ошибка проверки обновлений"
                        )
                    }
                }
            )
        }
    }

    fun startUpdate() {
        val updateInfo = currentUpdateInfo ?: return
        updateManager.startUpdate(
            appUpdateInfo = updateInfo,
            onSuccess = { resultCode ->
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "User agreed to update")
                    registerInstallStateListener()
                } else {
                    Log.d(TAG, "User declined update")
                    _updateState.value = UpdateState.Idle
                }
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to start update", exception)
                _updateState.value = UpdateState.Error(
                    "Ошибка запуска обновления"
                )
            }
        )
    }

    fun completeUpdate() {
        updateManager.completeUpdate(
            onSuccess = {
                Log.d(TAG, "Update completed")
                _updateState.value = UpdateState.Idle
            },
            onFailure = { exception ->
                Log.e(TAG, "Failed to complete update", exception)
                _updateState.value = UpdateState.Error(
                    "Ошибка установки"
                )
            }
        )
    }

    private fun registerInstallStateListener() {
        installStateListener?.let { updateManager.unregisterListener(it) }
        installStateListener = InstallStateUpdateListener { state ->
            handleInstallState(state)
        }
        updateManager.registerListener(installStateListener!!)
    }

    private fun handleInstallState(state: InstallState) {
        Log.d(TAG, "Install state: ${state.installStatus}")

        when (state.installStatus) {
            InstallStatus.DOWNLOADED -> {
                _updateState.value = UpdateState.ReadyToInstall
            }
            InstallStatus.DOWNLOADING -> {
                val progress = if (state.totalBytesToDownload > 0) {
                    ((state.bytesDownloaded * 100) / state.totalBytesToDownload).toInt()
                } else {
                    0
                }
                _updateState.value = UpdateState.Downloading(progress)
            }
            InstallStatus.FAILED -> {
                _updateState.value = UpdateState.Error(
                    "Ошибка загрузки: код ${state.installErrorCode}"
                )
            }
            InstallStatus.PENDING -> {
                Log.d(TAG, "Update pending")
            }
            InstallStatus.UNKNOWN -> {
                Log.d(TAG, "Unknown install status")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        installStateListener?.let { updateManager.unregisterListener(it) }
    }
}