package com.raulburgosmurray.musicplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicListPA
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicService
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.songPosition
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

        fun formatTime(millis: Long): String {
            val minutes = millis / 1000 / 60
            val seconds = (millis / 1000) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }


        fun getImgArt(path: String): ByteArray? {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            return retriever.embeddedPicture
        }

        fun getDominantColor(bitmap: Bitmap, sampleSize: Int = 10): Int {
            // Reduce size for analysis but maintain a representative sample
            val scaledWidth = bitmap.width / sampleSize
            val scaledHeight = bitmap.height / sampleSize

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false)

            // Map to count the frequency of each color
            val colorFrequency = mutableMapOf<Int, Int>()
            var dominantColor = 0
            var maxCount = 0

            // Analyze pixels of the scaled image
            for (x in 0 until scaledWidth) {
                for (y in 0 until scaledHeight) {
                    val pixel = scaledBitmap.getPixel(x, y)

                    // Ignore completely transparent pixels
                    if (Color.alpha(pixel) < 50) continue

                    // Round the color to group similar tones
                    val roundedColor = roundColor(pixel)

                    // Update the frequency counter
                    val count = colorFrequency.getOrDefault(roundedColor, 0) + 1
                    colorFrequency[roundedColor] = count

                    // Update the dominant color if we find one more frequent
                    if (count > maxCount) {
                        maxCount = count
                        dominantColor = roundedColor
                    }
                }
            }

            scaledBitmap.recycle()
            return dominantColor
        }

        private fun roundColor(color: Int): Int {
            // Group color components to consider similar tones as equals
            val tolerance = 20
            val a = Color.alpha(color)
            val r = (Color.red(color) / tolerance) * tolerance
            val g = (Color.green(color) / tolerance) * tolerance
            val b = (Color.blue(color) / tolerance) * tolerance
            return Color.argb(a, r, g, b)
        }

        fun getInverseColor(color: Int): Int {
            val alpha = Color.alpha(color)
            val red = 255 - Color.red(color)
            val green = 255 - Color.green(color)
            val blue = 255 - Color.blue(color)
            return Color.argb(alpha, red, green, blue)
        }

        fun getOptimalContrastColor(backgroundColor: Int): Int {
            // Calcular luminancia relativa (según WCAG 2.1)
            val luminance = calculateLuminance(backgroundColor)

            // Determinar color óptimo basado en umbral de luminancia
            return if (luminance > 0.179) {
                Color.BLACK  // Usar negro para fondos claros
            } else {
                Color.WHITE  // Usar blanco para fondos oscuros
            }
        }

        fun calculateLuminance(color: Int): Double {
            val red = Color.red(color) / 255.0
            val green = Color.green(color) / 255.0
            val blue = Color.blue(color) / 255.0

            // Aplicar factores de corrección gamma
            val r = if (red <= 0.03928) red / 12.92 else Math.pow((red + 0.055) / 1.055, 2.4)
            val g = if (green <= 0.03928) green / 12.92 else Math.pow((green + 0.055) / 1.055, 2.4)
            val b = if (blue <= 0.03928) blue / 12.92 else Math.pow((blue + 0.055) / 1.055, 2.4)

            // Fórmula de luminancia relativa WCAG
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        fun getIntermediateColor(color1: Int, color2: Int, ratio: Float = 0.5f): Int {
            // Validar que el ratio esté entre 0 y 1
            val clampedRatio = ratio.coerceIn(0f, 1f)

            // Obtener componentes ARGB del primer color
            val a1 = Color.alpha(color1)
            val r1 = Color.red(color1)
            val g1 = Color.green(color1)
            val b1 = Color.blue(color1)

            // Obtener componentes ARGB del segundo color
            val a2 = Color.alpha(color2)
            val r2 = Color.red(color2)
            val g2 = Color.green(color2)
            val b2 = Color.blue(color2)

            // Calcular componentes intermedios
            val a = (a1 + (a2 - a1) * clampedRatio).toInt()
            val r = (r1 + (r2 - r1) * clampedRatio).toInt()
            val g = (g1 + (g2 - g1) * clampedRatio).toInt()
            val b = (b1 + (b2 - b1) * clampedRatio).toInt()

            // Devolver el color resultante
            return Color.argb(a, r, g, b)
        }

        fun decodeImage(context: Context, image: ByteArray?): Bitmap {
            val img = Music.getImgArt(musicListPA[songPosition].path)
            val image = if (img != null) {
                BitmapFactory.decodeByteArray(img, 0, img.size)
            } else {
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.music_player_icon_splash_screen
                )
            }
            return image
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