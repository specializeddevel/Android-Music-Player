package com.raulburgosmurray.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_books")
data class CachedBook(
    @PrimaryKey val id: String,
    val title: String,
    val album: String,
    val artist: String,
    val duration: Long,
    val path: String,
    val artUri: String?,
    val fileSize: Long,
    val fileName: String = ""
)
