package com.raulburgosmurray.musicplayer

import android.media.MediaMetadataRetriever
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicListPA
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.songPosition
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

data class Music(
    val id: String,
    val title: String,
    val album: String,
    val artist: String,
    val duration: Long = 0,
    val path: String,
    val artUri: String
) {
    companion object {
        fun formatDuration(duration: Long): String {
            val hours = TimeUnit.HOURS.convert(duration, TimeUnit.MILLISECONDS)
            val minutes = TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS) % 60
            val seconds = TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS) % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        fun getImgArt(path: String): ByteArray? {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            return retriever.embeddedPicture
        }

        fun exitApplication() {
            if (PlayerActivity.musicService != null) {
            PlayerActivity.musicService!!.audioManager.abandonAudioFocus(PlayerActivity.musicService)
            PlayerActivity.musicService!!.stopForeground(true)
            PlayerActivity.musicService!!.mediaPlayer!!.release()
            PlayerActivity.musicService = null
            }
            exitProcess(1)
        }

        fun setSongPosition(increment: Boolean){
            if(increment){
                if(musicListPA.size-1 == songPosition) {
                    songPosition = 0
                } else {
                    ++songPosition
                }
            } else {
                if(songPosition == 0) {
                    songPosition = musicListPA.size-1
                } else {
                    --songPosition
                }
            }
        }
    }
}