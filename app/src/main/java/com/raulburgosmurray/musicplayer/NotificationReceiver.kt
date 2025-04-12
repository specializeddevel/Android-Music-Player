package com.raulburgosmurray.musicplayer

import android.app.Service.MODE_PRIVATE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.PlaybackParams
import android.os.Build
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.raulburgosmurray.musicplayer.NowPlaying.Companion.binding
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.KEY_LAST_AUDIO
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.KEY_LAST_POSITION
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicListPA
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicService
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.songPosition

class NotificationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return // Manejar el caso de contexto nulo

        when(intent?.action) {
            ApplicationClass.PREVIUS -> if(PlayerActivity.musicListPA.size > 1) prevNextSong(increment = false, context = context)
            ApplicationClass.PLAY -> {
                if(PlayerActivity.isPlaying) {
                    pauseMusic()
                    PlayerActivity.musicService!!.mediaPlayer?.let { player ->
                        Music.savePlaybackState(context, PlayerActivity.musicListPA[PlayerActivity.songPosition].id, player.currentPosition)
                    }
                } else {
                    Music.restorePlaybackState(context, PlayerActivity.musicListPA[songPosition].id)
                    playMusic()
                }
            }
            ApplicationClass.NEXT -> if(PlayerActivity.musicListPA.size > 1) prevNextSong(increment = true, context = context)
            ApplicationClass.EXIT -> {
                // Guardar posición antes de salir

                Music.exitApplication(context)
            }
        }
    }

    private fun playMusic(){
        PlayerActivity.isPlaying = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val playbackParams = PlaybackParams()
            playbackParams.speed = PlayerActivity.speed
            PlayerActivity.musicService!!.mediaPlayer.playbackParams = playbackParams
        }
        PlayerActivity.musicService!!.mediaPlayer.start()
        PlayerActivity.musicService!!.showNotification(R.drawable.pause_icon)
        PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
        try{ NowPlaying.binding.playPauseBtnNP.setIconResource(R.drawable.pause_icon) }catch (_: Exception){}
    }

    private fun pauseMusic(){
        PlayerActivity.isPlaying = false
        PlayerActivity.musicService!!.mediaPlayer.pause()
        PlayerActivity.musicService!!.showNotification(R.drawable.play_icon)
        PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        try{ NowPlaying.binding.playPauseBtnNP.setIconResource(R.drawable.play_icon) }catch (_: Exception){}
        //skipBackward(2000)
    }

    private fun prevNextSong(increment: Boolean, context: Context){
        Music.setSongPosition(increment=increment)
        PlayerActivity.musicService!!.createMediaPlayer()
        Glide.with(context)
            .load(musicListPA[songPosition].artUri)
            .apply(RequestOptions().placeholder(R.drawable.music_player_icon_splash_screen).centerInside())
            .into(PlayerActivity.binding.songImgPA)
        PlayerActivity.binding.songNamePA.text = PlayerActivity.musicListPA[PlayerActivity.songPosition].title
        Glide.with(context)
            .load(musicListPA[songPosition].artUri)
            .apply(RequestOptions().placeholder(R.drawable.ic_audiobook_cover).centerInside())
            .into(binding.songImgNP)
        binding.songNameNP.text = PlayerActivity.musicListPA[PlayerActivity.songPosition].title
        playMusic()
        PlayerActivity.fIndex = Music.favoriteChecker(PlayerActivity.musicListPA[songPosition].id)
        if(PlayerActivity.isFavorite) PlayerActivity.binding.favoriteBtnPA.setImageResource(R.drawable.favourite_icon)
        else PlayerActivity.binding.favoriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)
    }

    private fun skipForward(miliSeconds:Int){
        PlayerActivity.musicService!!.mediaPlayer?.let { player ->
            val newPosition = player.currentPosition + miliSeconds
            if (newPosition <= player.duration) {
                player.seekTo(newPosition)
            } else {
                player.seekTo(player.duration)
            }
        }
    }

    fun skipBackward(miliSeconds: Int) {
        PlayerActivity.musicService!!.mediaPlayer?.let { player ->
            val newPosition = player.currentPosition - miliSeconds
            if (newPosition >= 0) {
                player.seekTo(newPosition)
            } else {
                player.seekTo(0)
            }
        }
    }


}