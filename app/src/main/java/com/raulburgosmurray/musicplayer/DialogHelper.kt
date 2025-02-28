package com.raulburgosmurray.musicplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import kotlin.system.exitProcess

object DialogHelper {

    fun showPermissionDeniedDialog(activity: Activity, message:String) {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.yes)) { _, _ ->
                openSettings(activity)
            }
            .setNegativeButton(activity.getString(R.string.no)) { dialog, _ ->
                exitProcess(1)
            }
            .show()
    }

    private fun openSettings(activity: Activity) {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }.also { activity.startActivity(it) }
    }
}
