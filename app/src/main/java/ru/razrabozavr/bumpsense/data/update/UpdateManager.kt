package ru.razrabozavr.bumpsense.data.update

import android.content.Context
import android.util.Log
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.AppUpdateInfo
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType

class UpdateManager(context: Context) {

    private val updateManager = RuStoreAppUpdateManagerFactory.create(context)

    companion object {
        private const val TAG = "UpdateManager"
    }

    fun checkForUpdates(
        onSuccess: (AppUpdateInfo) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        updateManager
            .getAppUpdateInfo()
            .addOnSuccessListener { appUpdateInfo ->
                Log.d(TAG, "Update availability: ${appUpdateInfo.updateAvailability}")
                onSuccess(appUpdateInfo)
            }
            .addOnFailureListener { throwable ->
                Log.e(TAG, "Failed to check for updates", throwable)
                onFailure(throwable as Exception)
            }
    }

    fun startUpdate(
        appUpdateInfo: AppUpdateInfo, // ✅ Убран параметр Activity
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val options = AppUpdateOptions.Builder()
            .appUpdateType(AppUpdateType.FLEXIBLE) // ✅ Исправлено: было setAppUpdateType
            .build()

        updateManager
            .startUpdateFlow(appUpdateInfo, options) // ✅ Исправлено: убран параметр activity
            .addOnSuccessListener { resultCode ->
                Log.d(TAG, "Update flow result: $resultCode")
                onSuccess(resultCode)
            }
            .addOnFailureListener { throwable ->
                Log.e(TAG, "Failed to start update", throwable)
                onFailure(throwable as Exception)
            }
    }

    fun registerListener(listener: InstallStateUpdateListener) {
        updateManager.registerListener(listener)
    }

    fun unregisterListener(listener: InstallStateUpdateListener) {
        updateManager.unregisterListener(listener)
    }

    fun completeUpdate(
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val options = AppUpdateOptions.Builder()
            .appUpdateType(AppUpdateType.FLEXIBLE) // ✅ Исправлено: было setAppUpdateType
            .build()

        updateManager
            .completeUpdate(options) // ✅ Исправлено: добавлен параметр options
            .addOnSuccessListener {
                Log.d(TAG, "Update completed successfully")
                onSuccess()
            }
            .addOnFailureListener { throwable ->
                Log.e(TAG, "Failed to complete update", throwable)
                onFailure(throwable as Exception)
            }
    }
}