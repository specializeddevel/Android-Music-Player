package com.raulburgosmurray.musicplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_books",
    indices = [
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"])
    ]
)
data class CachedBook(
    @PrimaryKey val id: String,
    val title: String,
    val album: String,
    val artist: String,
    val duration: Long,
    val path: String,
    val artUri: String?,
    val fileSize: Long,
    val fileName: String = "",
    val description: String? = null
)

fun CachedBook.toMusic(): com.raulburgosmurray.musicplayer.Music = 
    com.raulburgosmurray.musicplayer.Music(id, title, album, artist, duration, path, artUri, fileSize, fileName, description = description)

fun CachedBook.withEditedMetadata(metadata: AudioMetadata?): CachedBook {
    if (metadata == null) return this

    return copy(
        title = if (metadata.isUserEdited(AudioMetadata.FIELD_TITLE)) metadata.title else title,
        artist = if (metadata.isUserEdited(AudioMetadata.FIELD_ARTIST)) metadata.artist else artist,
        album = if (metadata.isUserEdited(AudioMetadata.FIELD_ALBUM)) metadata.album.orEmpty() else album,
        artUri = if (metadata.isUserEdited(AudioMetadata.FIELD_ART_URI)) metadata.artUri else artUri
    )
}

fun com.raulburgosmurray.musicplayer.Music.toCachedBook(): CachedBook = 
    CachedBook(id, title, album, artist, duration, path, artUri, fileSize, fileName, description)
