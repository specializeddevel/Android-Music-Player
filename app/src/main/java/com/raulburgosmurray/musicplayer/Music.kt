package com.raulburgosmurray.musicplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.KEY_LAST_AUDIO
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.KEY_LAST_POSITION
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.PREFS_NAME
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicService
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

data class Music(
    val id: String,
    val title: String,
    val album: String,
    val artist: String,
    val duration: Long = 0,
    val path: String,
    val artUri: String,
    val uri:Uri
) {
    companion object {

        var backInMiliseconds = 5000;

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

        fun exitApplication(context: Context) {

            if (PlayerActivity.musicService != null) {
                PlayerActivity.musicService?.mediaPlayer?.let { player ->
                    musicService!!.stopSeekBarUpdates()
                    if (player.isPlaying) {
                        player.stop()
                    }
                    PlayerActivity.musicService!!.mediaPlayer?.let { player ->
                        Music.savePlaybackState(context , PlayerActivity.musicListPA[PlayerActivity.songPosition].id, player.currentPosition)
                    }
                    Thread.sleep(500)
                    player.release()
                }
                PlayerActivity.musicService!!.stopForeground(true)
                PlayerActivity.musicService = null
            }
            exitProcess(0)
        }

        fun setSongPosition(increment: Boolean){
            if(!PlayerActivity.repeat) {
                if(increment){
                    if(PlayerActivity.musicListPA.size-1 == PlayerActivity.songPosition) {
                        PlayerActivity.songPosition = 0
                    } else {
                        ++PlayerActivity.songPosition
                    }
                } else {
                    if(PlayerActivity.songPosition == 0) {
                        PlayerActivity.songPosition = PlayerActivity.musicListPA.size-1
                    } else {
                        --PlayerActivity.songPosition
                    }
                }
            }
        }

        // Save the current position
        fun savePlaybackState(context: Context, audioId: String, position: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_LAST_AUDIO, audioId)
                .putInt(KEY_LAST_POSITION, position- backInMiliseconds)
                .apply()
        }

        fun restorePlaybackState(context: Context, audioId: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val lastAudioId = prefs.getString(KEY_LAST_AUDIO, null)
            val lastPosition = prefs.getInt(KEY_LAST_POSITION, 0)

            if (lastAudioId == audioId && lastPosition > 0) {
                PlayerActivity.musicService!!.mediaPlayer.seekTo(lastPosition)
            }
        }

    }
}