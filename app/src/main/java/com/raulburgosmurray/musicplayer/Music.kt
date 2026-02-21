package com.raulburgosmurray.musicplayer

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

data class Chapter(
    val title: String,
    val startMs: Long,
    val durationMs: Long = 0
)

data class HistoryAction(
    val label: String,
    val audioPositionMs: Long,
    val realTimestamp: Long = System.currentTimeMillis()
)

data class Music(
    val id: String,
    val title: String,
    val album: String,
    val artist: String,
    val duration: Long = 0,
    val path: String,
    val artUri: String?,
    val fileSize: Long = 0,
    val fileName: String = "",
    val trackMore: String? = null,
    val comment: String? = null
) {
    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artUri?.let { android.net.Uri.parse(it) })
            .build()
        
        val uri = try {
            if (path.startsWith("content://")) {
                android.net.Uri.parse(path)
            } else {
                android.net.Uri.fromFile(File(path))
            }
        } catch (e: Exception) {
            android.net.Uri.parse(path)
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
