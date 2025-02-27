package com.raulburgosmurray.musicplayer

import java.util.concurrent.TimeUnit

data class Music(
    val id:String,
    val title: String,
    val album: String,
    val artist: String,
    val duration: Long = 0,
    val path: String,
    val artUri: String
)

fun formatDuration(duration:Long):String{
    val hours = TimeUnit.HOURS.convert(duration, TimeUnit.MILLISECONDS)
    val minutes = TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS) % 60
    val seconds = TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS) % 60
    if(hours>0) {
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        return String.format("%02d:%02d", minutes, seconds)
    }
}