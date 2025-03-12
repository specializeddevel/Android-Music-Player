package com.raulburgosmurray.musicplayer

import android.app.Activity
import android.os.Build
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts

class PermissionManager(private val caller:ActivityResultCaller) {

        private val requestPermission = caller.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.entries.all { it.value == true }
            val message = (caller as Activity).getString(R.string.text_dialog_permissions)
            if (!granted) DialogHelper.showPermissionDeniedDialog(caller as Activity, message)
        }

        fun requestPermissions() {
            val permissions = mutableListOf<String>()

            // Camera is always required
            permissions.add(android.Manifest.permission.CAMERA)
            permissions.add(android.Manifest.permission.FOREGROUND_SERVICE)


            // Handle storage permissions according to the Android version
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }

            requestPermission.launch(permissions.toTypedArray())
        }
}
