package com.raulburgosmurray.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_queue")
data class QueueItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: String,
    val orderIndex: Int
)
