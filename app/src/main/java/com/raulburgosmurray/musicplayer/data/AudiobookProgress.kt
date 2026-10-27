package com.raulburgosmurray.musicplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audiobook_progress",
    indices = [
        Index(value = ["lastPauseTimestamp"]),
        Index(value = ["lastUpdated"])
    ]
)
data class AudiobookProgress(
    @PrimaryKey val mediaId: String,
    val lastPosition: Long,
    val duration: Long,
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastPauseTimestamp: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val eqPresetName: String = "",
    val isRead: Boolean = false
)

/** A nullable field means "leave the stored value unchanged". */
data class ProgressUpdate(
    val lastPosition: Long? = null,
    val duration: Long? = null,
    val lastPauseTimestamp: Long? = null,
    val playbackSpeed: Float? = null,
    val pitch: Float? = null,
    val eqPresetName: String? = null,
    val isRead: Boolean? = null
)

fun mergeProgress(
    existing: AudiobookProgress?,
    mediaId: String,
    update: ProgressUpdate,
    timestamp: Long = System.currentTimeMillis()
): AudiobookProgress {
    val current = existing ?: AudiobookProgress(mediaId, lastPosition = 0L, duration = 0L)
    return current.copy(
        lastPosition = update.lastPosition ?: current.lastPosition,
        duration = update.duration ?: current.duration,
        lastUpdated = timestamp,
        lastPauseTimestamp = update.lastPauseTimestamp ?: current.lastPauseTimestamp,
        playbackSpeed = update.playbackSpeed ?: current.playbackSpeed,
        pitch = update.pitch ?: current.pitch,
        eqPresetName = update.eqPresetName ?: current.eqPresetName,
        isRead = update.isRead ?: current.isRead
    )
}
