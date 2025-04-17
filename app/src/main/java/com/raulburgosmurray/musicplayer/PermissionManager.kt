package com.raulburgosmurray.musicplayer
import android.Manifest
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission

object PermissionManager {
    private val BASE_PERMISSIONS = arrayOf(
        Manifest.permission.FOREGROUND_SERVICE
    )

    private val PRE_ANDROID_13_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private val ANDROID_13_PLUS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_MEDIA_AUDIO
    )

    private val MEDIA_PLAYBACK_PERMISSION = arrayOf(
        Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
    )

    fun getRequiredPermissions(): Array<String> {
        return mutableListOf<String>().apply {
            addAll(BASE_PERMISSIONS)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ (API 33+)
                addAll(ANDROID_13_PLUS_PERMISSIONS)
            } else {
                // Versions prior to Android 13
                addAll(PRE_ANDROID_13_PERMISSIONS)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+)
                addAll(MEDIA_PLAYBACK_PERMISSION)
            }
        }.toTypedArray()
    }

    fun checkAndRequestPermissions(activity: AppCompatActivity, listener: PermissionListener) {
        val permissionsToRequest = getRequiredPermissions()

        TedPermission.create()
            .setPermissionListener(listener)
            .setPermissions(*permissionsToRequest)
            .setRationaleTitle("Required permissions")
            .setRationaleMessage("The application needs some permits to function properly, will be requested below")
            .setDeniedTitle("Denied permissions")
            .setDeniedMessage("If you reject permissions, the application may not work.\nPlease allow permissions opening settings")
            .setGotoSettingButton(true)
            .check()
    }
}