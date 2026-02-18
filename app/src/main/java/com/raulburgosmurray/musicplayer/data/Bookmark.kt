package com.raulburgosmurray.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaId: String,
    val position: Long,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)
