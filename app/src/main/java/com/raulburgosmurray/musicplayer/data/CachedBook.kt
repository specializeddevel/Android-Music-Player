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

fun CachedBook.toMusic(): com.raulburgosmurray.musicplayer.Music = 
    com.raulburgosmurray.musicplayer.Music(id, title, album, artist, duration, path, artUri, fileSize, fileName)

fun com.raulburgosmurray.musicplayer.Music.toCachedBook(): CachedBook = 
    CachedBook(id, title, album, artist, duration, path, artUri, fileSize, fileName)
