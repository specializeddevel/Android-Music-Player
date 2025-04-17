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

    )

    private val ANDROID_13_PLUS_PERMISSIONS = arrayOf(
        Manifest.permission.READ_MEDIA_AUDIO
    )

    private val MEDIA_PLAYBACK_PERMISSION = arrayOf(
        Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
    )

    fun getRequiredMusicPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12 (API 29-32)
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.FOREGROUND_SERVICE
            )
        } else {
            // Android 5-9 (API 21-28)
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
    fun checkAndRequestPermissions(activity: AppCompatActivity, listener: PermissionListener) {
        val permissionsToRequest = getRequiredMusicPermissions()

        TedPermission.create()
            .setPermissionListener(listener)
            .setPermissions(*permissionsToRequest)
            .setRationaleTitle("Required permissions")
            .setRationaleMessage("This app requires access to your audio files on this device. Please grant the necessary permission to proceed.")
            .setDeniedTitle("Denied permissions")
            .setDeniedMessage("If you don’t allow the required permissions, the app might not work properly. You can enable them anytime from your device settings")
            .setGotoSettingButton(true)
            .check()
    }
}