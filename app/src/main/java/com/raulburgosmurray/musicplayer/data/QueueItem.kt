package com.raulburgosmurray.musicplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_queue",
    indices = [
        Index(value = ["orderIndex"]),
        Index(value = ["mediaId"])
    ]
)
data class QueueItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: String,
    val orderIndex: Int
)
