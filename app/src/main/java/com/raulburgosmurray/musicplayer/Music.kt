package com.raulburgosmurray.musicplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.KEY_LAST_AUDIO
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.KEY_LAST_POSITION
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.PREFS_NAME
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicService
import java.io.File
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

) {
    companion object {

        var backInMiliseconds = 5000;
        private const val FOLDER_NAME = "playback_states"
        private val gson = Gson()

        // Obtiene el directorio donde se guardarán los estados
        private fun getPlaybackStatesDir(context: Context): File {
            return File(context.filesDir, FOLDER_NAME).apply {
                if (!exists()) mkdir()
            }
        }

        // Guarda el estado en un archivo JSON
        fun savePlaybackState(context: Context, audioId: String, position: Int) {
            try {
                val state = PlaybackState(audioId, position - backInMiliseconds)
                val file = File(getPlaybackStatesDir(context), "$audioId.json")
                file.writeText(gson.toJson(state))
            } catch (e: Exception) {
                Log.e("PlaybackStateManager", "Error saving playback state", e)
            }
        }

        // Save the current position in shared preferences
        fun savePlaybackStateInShared(context: Context, audioId: String, position: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_LAST_AUDIO, audioId)
                .putInt(KEY_LAST_POSITION, position- backInMiliseconds)
                .apply()
        }



        // Recupera el estado desde el archivo JSON
        fun restorePlaybackState(context: Context, audioId: String): Int {
            return try {
                val file = File(getPlaybackStatesDir(context), "$audioId.json")
                if (file.exists()) {
                    val state = gson.fromJson(file.readText(), PlaybackState::class.java)
                    if (state.position > 0) {
                        PlayerActivity.musicService!!.mediaPlayer.seekTo(state.position)
                    }
                    state.position

                } else {
                    0
                }
            } catch (e: Exception) {
                Log.e("PlaybackStateManager", "Error restoring playback state", e)
                0
            }
        }

        fun restorePlaybackStateInShared(context: Context, audioId: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val lastAudioId = prefs.getString(KEY_LAST_AUDIO, null)
            val lastPosition = prefs.getInt(KEY_LAST_POSITION, 0)

            if (lastAudioId == audioId && lastPosition > 0) {
                PlayerActivity.musicService!!.mediaPlayer.seekTo(lastPosition)
            }
        }

        // Obtiene el último audio reproducido (opcional)
        fun getLastPlayedAudioId(context: Context): String? {
            val files = getPlaybackStatesDir(context).listFiles()
            return files?.maxByOrNull { it.lastModified() }?.nameWithoutExtension
        }

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

        fun checkPlaylist(playlist: ArrayList<Music>) : ArrayList<Music>{
            playlist.forEachIndexed { index, music ->
                val file = File(music.path)
                if(!file.exists())
                    playlist.removeAt(index)
            }
            return playlist
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



        fun saveFavoriteSongs(context: Context) {
            val editor = context.getSharedPreferences("FAVORITES", MODE_PRIVATE).edit()
            val jsonString = GsonBuilder().create().toJson(FavoritesActivity.favoriteSongs)
            editor.putString("FavoriteSongs", jsonString)
            editor.apply()
        }

        fun loadFavorites(context: Context) {
            val editor = context.getSharedPreferences("FAVORITES", MODE_PRIVATE)
            val jsonString = editor.getString("FavoriteSongs", null)
            val typeToken = object : TypeToken<ArrayList<Music>>() {}.type
            if (jsonString != null) {
                FavoritesActivity.favoriteSongs = GsonBuilder().create().fromJson(jsonString, typeToken)
            }
        }



        fun loadPlaybackState(context: Context) : String? {
           return Music.getLastPlayedAudioId(context)


        }

        fun favoriteChecker(id: String): Int {
            PlayerActivity.isFavorite = false
            FavoritesActivity.favoriteSongs.forEachIndexed { index, music ->
                if (id == music.id) {
                    PlayerActivity.isFavorite = true
                    return index
                }
            }
            return -1
        }
    }
}