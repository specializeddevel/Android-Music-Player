package com.raulburgosmurray.musicplayer

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.concurrent.TimeUnit

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
        
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(android.net.Uri.fromFile(File(path)))
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        fun formatDuration(duration: Long): String {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        fun getImgArt(path: String): ByteArray? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(path)
                retriever.embeddedPicture
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        }

        fun checkPlaylist(playlist: ArrayList<Music>): ArrayList<Music> {
            val iterator = playlist.iterator()
            while (iterator.hasNext()) {
                val music = iterator.next()
                if (!File(music.path).exists()) iterator.remove()
            }
            return playlist
        }
    }
}
