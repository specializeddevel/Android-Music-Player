package com.raulburgosmurray.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audiobook_progress")
data class AudiobookProgress(
    @PrimaryKey val mediaId: String,
    val lastPosition: Long,
    val duration: Long,
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastPauseTimestamp: Long = 0L,
    val playbackSpeed: Float = 1.0f
)
