package com.raulburgosmurray.musicplayer

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.raulburgosmurray.musicplayer.Music.Companion.formatDuration


class MusicService: Service(), AudioManager.OnAudioFocusChangeListener {

    private var myBinder = MyBinder()
    val mediaPlayer: MediaPlayer by lazy { MediaPlayer() }
    private lateinit var mediaSession : MediaSessionCompat
    private lateinit var runnable: Runnable
    lateinit var audioManager: AudioManager

    override fun onBind(intent: Intent?): IBinder? {
        mediaSession = MediaSessionCompat(baseContext, "My Music")
        return myBinder
    }

    inner class MyBinder: Binder(){
        fun currentService(): MusicService {
            return this@MusicService
        }
    }

    fun showNotification(playPauseBtn: Int){

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val prevIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.PREVIUS)
        val prevPendingIntent = PendingIntent.getBroadcast(baseContext, 0, prevIntent, flag)

        val playIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.PLAY)
        val playPendingIntent = PendingIntent.getBroadcast(baseContext, 0, playIntent, flag)

        val nextIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.NEXT)
        val nextPendingIntent = PendingIntent.getBroadcast(baseContext, 0, nextIntent, flag)

        val forwardIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.FORWARD)
        val forwardPendingIntent = PendingIntent.getBroadcast(baseContext, 0, forwardIntent, flag)

        val exitIntent = Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.EXIT)
        val exitPendingIntent = PendingIntent.getBroadcast(baseContext, 0, exitIntent, flag)

        //Extract the background image for notification from song art
        val imgArt = Music.getImgArt(PlayerActivity.musicListPA[PlayerActivity.songPosition].path)
        val image = if (imgArt != null) {
            BitmapFactory.decodeByteArray(imgArt, 0, imgArt.size)
        } else {
            BitmapFactory.decodeResource(resources, R.drawable.music_player_icon_splash_screen)
        }

        val notification = NotificationCompat.Builder(baseContext, ApplicationClass.CHANNEL_ID)
            .setContentTitle(PlayerActivity.musicListPA[PlayerActivity.songPosition].title)
            .setContentText(PlayerActivity.musicListPA[PlayerActivity.songPosition].artist)
            .setSmallIcon(R.drawable.playlist_icon)
            .setLargeIcon(image)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.previous_icon, "Previus", prevPendingIntent)
            .addAction(playPauseBtn, "Play", playPendingIntent)
            .addAction(R.drawable.next_icon, "Next", nextPendingIntent)
            .addAction(R.drawable.add_icon, "+10", forwardPendingIntent)
            .addAction(R.drawable.exit_icon, "Exit", exitPendingIntent)
            .build()
        startForeground(13, notification)
    }

    private fun playMusic(){
        //play music
        PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
        //NowPlaying.binding.playPauseBtnNP.setIconResource(R.drawable.pause_icon)
        PlayerActivity.isPlaying = true
        //Music.restorePlaybackState(baseContext,PlayerActivity.musicListPA[songPosition].id)
        mediaPlayer?.start()
        showNotification(R.drawable.pause_icon)
    }

    private fun pauseMusic(){
        //pause music
        PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        //NowPlaying.binding.playPauseBtnNP.setIconResource(R.drawable.play_icon)
        PlayerActivity.isPlaying = false
        //Music.savePlaybackState(baseContext, PlayerActivity.musicListPA[songPosition].id, PlayerActivity.musicService!!.mediaPlayer.currentPosition)
        mediaPlayer!!.pause()
        showNotification(R.drawable.play_icon)
    }



    fun createMediaPlayer(){
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(PlayerActivity.musicListPA[PlayerActivity.songPosition].path)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val playbackParams = PlaybackParams()
                playbackParams.speed = PlayerActivity.speed
                mediaPlayer.playbackParams = playbackParams
            }
            mediaPlayer.prepare()
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
            showNotification(R.drawable.pause_icon)
            PlayerActivity.binding.tvSeekBarStart.text = formatDuration(mediaPlayer.currentPosition.toLong())
            PlayerActivity.binding.tvSeekBarEnd.text = formatDuration(mediaPlayer.duration.toLong())
            PlayerActivity.binding.seekBarPA.progress = 0
            PlayerActivity.binding.seekBarPA.max = mediaPlayer.duration
            //Music.restorePlaybackState(baseContext, PlayerActivity.musicListPA[songPosition].id)
            PlayerActivity.musicService!!.mediaPlayer?.let { player ->
                Music.restorePlaybackState(applicationContext, PlayerActivity.musicListPA[PlayerActivity.songPosition].id)
            }
        } catch (e: Exception) {
            return
        }
    }

    fun seekBarSetup(){
        runnable = Runnable {
            PlayerActivity.binding.tvSeekBarStart.text =
                formatDuration(mediaPlayer.currentPosition.toLong())
            PlayerActivity.binding.seekBarPA.progress = mediaPlayer!!.currentPosition
            Handler(Looper.getMainLooper()).postDelayed(runnable, 200)
        }
        Handler(Looper.getMainLooper()).postDelayed(runnable, 0)
    }

    fun getPlayBackState(): PlaybackStateCompat {
        val playbackSpeed = if (PlayerActivity.isPlaying) 1F else 0F

        return PlaybackStateCompat.Builder().setState(
            if (mediaPlayer?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            mediaPlayer!!.currentPosition.toLong(), playbackSpeed)
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .build()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange <= 0) {
            PlayerActivity.musicService!!.mediaPlayer?.let { player ->
                Music.savePlaybackState(applicationContext , PlayerActivity.musicListPA[PlayerActivity.songPosition].id, player.currentPosition)
            }
            pauseMusic()
        }
    }


}