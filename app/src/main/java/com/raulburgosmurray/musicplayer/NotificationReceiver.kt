package com.raulburgosmurray.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action) {
            ApplicationClass.PREVIUS -> Toast.makeText(context, "Previus", Toast.LENGTH_SHORT).show()
            ApplicationClass.PLAY -> Toast.makeText(context, "Play", Toast.LENGTH_SHORT).show()
            ApplicationClass.NEXT -> Toast.makeText(context, "Next", Toast.LENGTH_SHORT).show()
            ApplicationClass.EXIT -> Music.exitApplication()
            //TODO: The EXIT action don't works
        }
    }
}