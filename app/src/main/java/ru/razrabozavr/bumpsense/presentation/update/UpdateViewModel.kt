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
                            _updateState.value = UpdateState.UpdateAvailable
                        }
                        UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                            _updateState.value = UpdateState.NoUpdateAvailable
                        }
                        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                            registerInstallStateListener()
                        }
                        else -> {
                            _updateState.value = UpdateState.Idle
                        }
                    }
                },
                onFailure = { exception ->
                    _updateState.value = UpdateState.Error(
                        exception.message ?: "Ошибка проверки обновлений"
                    )
                }
            )
        }
    }

    fun startUpdate() { // ✅ Убран параметр activity
        val updateInfo = currentUpdateInfo ?: return
        updateManager.startUpdate(
            appUpdateInfo = updateInfo, // ✅ Убран параметр activity
            onSuccess = { resultCode ->
                if (resultCode == Activity.RESULT_OK) {
                    registerInstallStateListener()
                } else {
                    _updateState.value = UpdateState.Idle
                }
            },
            onFailure = { exception ->
                _updateState.value = UpdateState.Error(
                    exception.message ?: "Ошибка запуска обновления"
                )
            }
        )
    }

    fun completeUpdate() {
        updateManager.completeUpdate(
            onSuccess = {
                _updateState.value = UpdateState.Idle
            },
            onFailure = { exception ->
                _updateState.value = UpdateState.Error(
                    exception.message ?: "Ошибка установки"
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
            InstallStatus.INSTALLED -> {
                _updateState.value = UpdateState.Idle
            }
            else -> Unit
        }
    }

    override fun onCleared() {
        super.onCleared()
        installStateListener?.let { updateManager.unregisterListener(it) }
    }
}