package com.raulburgosmurray.musicplayer
import android.Manifest
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.raulburgosmurray.musicplayer.R

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

        val ctx = activity.applicationContext
        TedPermission.create()
            .setPermissionListener(listener)
            .setPermissions(*permissionsToRequest)
            .setRationaleTitle(ctx.getString(R.string.storage_permission_title))
            .setRationaleMessage(ctx.getString(R.string.storage_permission_rationale))
            .setDeniedTitle(ctx.getString(R.string.storage_permission_denied_title))
            .setDeniedMessage(ctx.getString(R.string.storage_permission_denied_message))
            .setGotoSettingButton(true)
            .check()
    }
}