package com.raulburgosmurray.musicplayer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.raulburgosmurray.musicplayer.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity(), ServiceConnection {

    companion object {
        lateinit var musicListPA : ArrayList<Music>
        var songPosition : Int = 0
        var isPlaying = false
        var musicService : MusicService? = null
    }


    private lateinit var binding: ActivityPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.coolPink)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        //For Starting service
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, this, BIND_AUTO_CREATE)
        startService(intent)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.DrawerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initializeLayout()
        binding.playPauseBtnPA.setOnClickListener{
            if(isPlaying) {
                pauseMusic()
            } else {
                playMusic()
            }
        }
        binding.previusBtnPA.setOnClickListener{
            prevNextSong(false)
        }

        binding.nextBtnPA.setOnClickListener{
            prevNextSong(true)
        }

    }

    private fun setLayout(){
        Glide.with(this)
            .load(musicListPA[songPosition].artUri)
            .apply(RequestOptions().placeholder(R.drawable.music_player_icon_splash_screen).centerCrop())
            .into(binding.songImgPA)
        binding.songNamePA.text = musicListPA[songPosition].title
    }

    private fun createMediaPlayer(){
        try {
            musicService!!.mediaPlayer.reset()
            musicService!!.mediaPlayer.setDataSource(musicListPA[songPosition].path)
            musicService!!.mediaPlayer.prepare()
            musicService!!.mediaPlayer.start()
            isPlaying = true
            binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
        } catch (e: Exception) {
            return
        }
    }

    private fun initializeLayout(){
        songPosition = intent.getIntExtra("index", 0)
        when(intent.getStringExtra("class")){
            "MusicAdapter" -> {
                musicListPA = ArrayList()
                musicListPA.addAll(MainActivity.MusicListMA)
                setLayout()

            }
            "MainActivity" -> {
                musicListPA = ArrayList()
                musicListPA.addAll(MainActivity.MusicListMA)
                musicListPA.shuffle()
                setLayout()
                createMediaPlayer()
            }
        }
    }

    private fun playMusic(){
        binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
        isPlaying = true
        musicService!!.mediaPlayer.start()
    }

    private fun pauseMusic(){
        binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        isPlaying = false
        musicService!!.mediaPlayer.pause()
    }

    private fun prevNextSong(increment: Boolean){
        setSongPosition(increment)
        setLayout()
        createMediaPlayer()
    }

    private fun setSongPosition(increment: Boolean){
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

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        var binder = service as MusicService.MyBinder
        musicService = binder.currentService()
        createMediaPlayer()
        musicService!!.showNotification()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        musicService = null
    }
}