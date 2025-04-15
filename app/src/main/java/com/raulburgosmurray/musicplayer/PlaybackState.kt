package com.raulburgosmurray.musicplayer

data class PlaybackState(
    val audioId: String,
    val position: Int,
    val lastModified: Long = System.currentTimeMillis()
)