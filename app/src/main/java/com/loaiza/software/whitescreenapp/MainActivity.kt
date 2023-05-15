package com.loaiza.software.whitescreenapp

import android.content.Intent
import android.os.Bundle
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

/**
 * Created by Julio Segura
 */
class MainActivity : AppCompatActivity() {

    private lateinit var updater: MyAppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updater = MyAppUpdateManager(this)

        //Remove de shadow from action bar:
        supportActionBar?.elevation = 0F

    }

    override fun onResume() {
        super.onResume()
        updater.doOnImmediateUpdate()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updater.checkUpdateResult(requestCode, resultCode)
    }

    override fun onDestroy() {
        super.onDestroy()
        updater.unRegisterListener()
    }

}