package com.loaiza.software.whitescreenapp

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.isImmediateUpdateAllowed

class MyAppUpdateManager(private val activity: Activity) {

    var appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    private val updateType = AppUpdateType.FLEXIBLE

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if(state.installStatus() == InstallStatus.DOWNLOADED) {
            Toast.makeText(
                activity,
                "Download successful. Restarting app in 5 seconds.",
                Toast.LENGTH_LONG)
                .show()

            Handler(Looper.getMainLooper()).postDelayed(
                {
                    appUpdateManager.completeUpdate()
                },
                5000 // value in milliseconds
            )

        }
    }

    init {

        if(updateType == AppUpdateType.FLEXIBLE) {
            appUpdateManager.registerListener(installStateUpdatedListener)
        }

        checkForAppUpdates()

    }

    private fun checkForAppUpdates(){
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            val isUpdateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE

            val isUpdateAllowed = when(updateType) {
                AppUpdateType.FLEXIBLE -> info.isFlexibleUpdateAllowed
                AppUpdateType.IMMEDIATE -> info.isImmediateUpdateAllowed
                else -> false
            }

            if(isUpdateAvailable && isUpdateAllowed) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateType,
                    activity,
                    123
                )
            }

        }
    }

    fun doOnImmediateUpdate() {

        if(updateType == AppUpdateType.IMMEDIATE) {

            appUpdateManager.appUpdateInfo.addOnSuccessListener {info ->
                if(info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        updateType,
                        activity,
                        123
                    )
                }
            }

        }

    }

    fun unRegisterListener() {
        if(updateType == AppUpdateType.FLEXIBLE) {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        }
    }

    fun checkUpdateResult(requestCode: Int, resultCode: Int) {

        if(requestCode == 123) {
            if(resultCode != AppCompatActivity.RESULT_OK) {
                Toast.makeText(activity, "Something went wrong with the update!", Toast.LENGTH_LONG).show()
            }
        }

    }

}