package com.raulburgosmurray.musicplayer

import android.app.ComponentCaller
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.raulburgosmurray.musicplayer.Music.Companion.formatDuration
import com.raulburgosmurray.musicplayer.Music.Companion.setSongPosition
import com.raulburgosmurray.musicplayer.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity(), ServiceConnection, MediaPlayer.OnCompletionListener {

    companion object {
        lateinit var musicListPA : ArrayList<Music>
        var songPosition : Int = 0
        var isPlaying = false
        var musicService : MusicService? = null
        lateinit var binding: ActivityPlayerBinding
        var repeat = false
        var min15 = false
        var min30 = false
        var min60 = false
        var speed = 1.0f
        var nowPlayingId: String = ""
        // Constants for Sharedpreferences
        val PREFS_NAME = "AudioPrefs"
        val KEY_LAST_AUDIO = "last_audio"
        val KEY_LAST_POSITION = "last_position"
        var isResumed = false
        var isFavorite = false
        var fIndex = -1
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.coolPink)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.DrawerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initializeLayout()
        binding.backBtnPA.setOnClickListener{
            finish()
        }
        binding.playPauseBtnPA.setOnClickListener{
            if(isPlaying) {
                pauseMusic()
                musicService!!.mediaPlayer?.let { player ->
                    Music.savePlaybackState(applicationContext,musicListPA[songPosition].id, player.currentPosition)
                }

            } else {
                musicService!!.mediaPlayer?.let { _ ->
                    Music.restorePlaybackState(applicationContext ,musicListPA[songPosition].id)
                }
                playMusic()
            }
        }
        binding.previusBtnPA.setOnClickListener{
            prevNextSong(false)
        }

        binding.nextBtnPA.setOnClickListener{
            prevNextSong(true)
        }
        binding.seekBarPA.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if(fromUser) {
                    musicService!!.mediaPlayer.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        binding.repeatBtnPA.setOnClickListener{
            if(!repeat){
                repeat = true
                binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(this,R.color.purple_500))
            } else {
                repeat = false
                binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(this,R.color.cool_pink))
            }
        }

        binding.equalizerBtnPA.setOnClickListener {
            try {
                val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, musicService!!.mediaPlayer.audioSessionId)
                eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, baseContext.packageName)
                eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                startActivityForResult(eqIntent, 13)
            } catch (e: Exception){
                Toast.makeText(this, "Equalizer feature not supported!", Toast.LENGTH_SHORT).show()
            }
        }
        binding.timerBtnPA.setOnClickListener {
            val timer = min15 || min30 || min60
            if(!timer){
                showBottomSheetDialog()
            }
            else {
                val builder = MaterialAlertDialogBuilder(this)
                builder.setTitle("Stop timer")
                    .setMessage("Do you want to stop timer?")
                    .setPositiveButton("Yes"){_,_ ->
                        min15 = false
                        min30 = false
                        min60 = false
                        binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.cool_pink))
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                val customDialog = builder.create()
                customDialog.show()
                customDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
                customDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.RED)
            }
        }

        binding.shareBtnPA.setOnClickListener {
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.type = "audio/*"
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(musicListPA[songPosition].path))
            startActivity(Intent.createChooser(shareIntent, "Sharing your music file!"))
        }

        binding.speedBtnPA.setOnClickListener {
            showBottomSheetSpeedDialog()
        }

        binding.favoriteBtnPA.setOnClickListener {
            if(isFavorite) {
                fIndex = Music.favoriteChecker(musicListPA[songPosition].id)
                isFavorite = false
                binding.favoriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)
                FavoritesActivity.favoriteSongs.removeAt(fIndex)
            } else {
                isFavorite = true
                binding.favoriteBtnPA.setImageResource(R.drawable.favourite_icon)
                FavoritesActivity.favoriteSongs.add(musicListPA[songPosition])
            }
        }
    }

    private fun setLayout(){
        fIndex = Music.favoriteChecker(musicListPA[songPosition].id)
        Glide.with(this)
            .load(musicListPA[songPosition].artUri)
            .apply(RequestOptions().placeholder(R.drawable.ic_audiobook_cover).centerInside())
            .into(binding.songImgPA)
        binding.songNamePA.text = musicListPA[songPosition].title
        if(repeat) binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(this,R.color.purple_500))
        if(min15 || min30 || min60) binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple_500))
        if(isFavorite) binding.favoriteBtnPA.setImageResource(R.drawable.favourite_icon)
        else binding.favoriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)
    }

    private fun createMediaPlayer(){
        try {

            musicService!!.mediaPlayer.reset()

            musicService!!.mediaPlayer.setDataSource(musicListPA[songPosition].path)
            musicService!!.mediaPlayer.prepare()

            Music.restorePlaybackState(applicationContext, musicListPA[songPosition].id)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val playbackParams = PlaybackParams()
                playbackParams.speed = speed
                musicService!!.mediaPlayer.playbackParams = playbackParams
            }
            musicService!!.mediaPlayer.pause()
            if(!isResumed){
                musicService!!.mediaPlayer.start()
                isPlaying = true
                binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
                musicService!!.showNotification(R.drawable.pause_icon)
                binding.seekBarPA.progress = 0

            } else {
                isPlaying = false
                binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
                musicService!!.showNotification(R.drawable.play_icon)
                binding.seekBarPA.progress = musicService!!.mediaPlayer.currentPosition

            }
            binding.tvSeekBarStart.text = formatDuration(musicService!!.mediaPlayer.currentPosition.toLong())
            binding.tvSeekBarEnd.text = formatDuration(musicService!!.mediaPlayer.duration.toLong())

            binding.seekBarPA.max = musicService!!.mediaPlayer.duration

            musicService!!.mediaPlayer.setOnCompletionListener(this)
            nowPlayingId = musicListPA[songPosition].id

        } catch (e: Exception) {
            return
        }
    }

    private fun initializeLayout(){
        songPosition = intent.getIntExtra("index", 0)
        when(intent.getStringExtra("class")){
            "FavoriteAdapter" -> {
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(FavoritesActivity.favoriteSongs)
                isResumed = false
                setLayout()
            }
            "NowPlaying" -> {
                setLayout()
                isResumed = false
                binding.tvSeekBarStart.text = formatDuration(musicService!!.mediaPlayer.currentPosition.toLong())
                binding.tvSeekBarEnd.text = formatDuration(musicService!!.mediaPlayer.duration.toLong())
                binding.seekBarPA.max = musicService!!.mediaPlayer.duration
                binding.seekBarPA.progress = musicService!!.mediaPlayer.currentPosition
                if(isPlaying) {
                    binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)}
                else {
                    binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)}
            }
            "MusicAdapterSearch" -> {
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(MainActivity.musicListSearch)
                isResumed = false
                setLayout()
            }
            "MusicAdapter" -> {
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(MainActivity.MusicListMA)
                isResumed = false
                setLayout()

            }
            "MainActivity" -> {
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(MainActivity.MusicListMA)
                musicListPA.shuffle()
                setLayout()
                isResumed = false
                createMediaPlayer()

            }
            "ContinuePlaying" -> {
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(MainActivity.MusicListMA)
                setLayout()
                isResumed = true
                createMediaPlayer()


            }
        }
    }

    private fun playMusic(){
        binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
        musicService!!.showNotification(R.drawable.pause_icon)
        isPlaying = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val playbackParams = PlaybackParams()
            playbackParams.speed = speed
            musicService!!.mediaPlayer.playbackParams = playbackParams
        }
        musicService!!.mediaPlayer.start()
    }

    private fun pauseMusic(){
        binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        musicService!!.showNotification(R.drawable.play_icon)
        isPlaying = false
        musicService!!.mediaPlayer.pause()
    }

    private fun prevNextSong(increment: Boolean){
        setSongPosition(increment)
        setLayout()
        createMediaPlayer()
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

    private fun skipBackward(miliSeconds: Int) {
        musicService!!.mediaPlayer?.let { player ->
            val newPosition = player.currentPosition - miliSeconds
            if (newPosition >= 0) {
                player.seekTo(newPosition)
            } else {
                player.seekTo(0)
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        var binder = service as MusicService.MyBinder
        musicService = binder.currentService()
        createMediaPlayer()
        musicService!!.seekBarSetup()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        musicService = null
    }

    override fun onCompletion(mp: MediaPlayer?) {
        try {
            setSongPosition(increment = true)
            createMediaPlayer()
            setLayout()
        } catch (e: Exception) {
            return
        }
    }

    override fun onDestroy() {
        if(!MainActivity.search){
            MainActivity.musicAdapter.updateMusicList(MainActivity.MusicListMA)
        }
        super.onDestroy()

    }
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        caller: ComponentCaller
    ) {
        super.onActivityResult(requestCode, resultCode, data, caller)
        if(requestCode == 13 || resultCode == RESULT_OK)
            return
    }

    private fun showBottomSheetDialog(){
        val dialog = BottomSheetDialog(this@PlayerActivity)
        dialog.setContentView(R.layout.bottom_sheet_dialog)
        dialog.show()
        dialog.findViewById<LinearLayout>(R.id.min_15)?.setOnClickListener{
            Toast.makeText(baseContext, R.string.min_15_lit, Toast.LENGTH_SHORT).show()
            binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple_500))
            min15 = true
            Handler(Looper.getMainLooper()).postDelayed({
                if (min15) {
                    min15=false
                    binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.cool_pink))
                    pauseMusic()
                }
            }, (15*60000L))
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.min_30)?.setOnClickListener{
            Toast.makeText(baseContext, R.string.min_30_lit, Toast.LENGTH_SHORT).show()
            binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple_500))
            min30 = true
            Handler(Looper.getMainLooper()).postDelayed({
                if (min30) {
                    min30=false
                    binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.cool_pink))
                    pauseMusic()
                }
            }, (30*60000L))
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.min_60)?.setOnClickListener{
            Toast.makeText(baseContext, R.string.min_60_lit, Toast.LENGTH_SHORT).show()
            binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple_500))
            min60 = true
            Handler(Looper.getMainLooper()).postDelayed({
                if (min60) {
                    min60=false
                    binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.cool_pink))
                    pauseMusic()
                }
            }, (60*60000L))
            dialog.dismiss()
        }
    }


    private fun showBottomSheetSpeedDialog(){
        val dialog = BottomSheetDialog(this@PlayerActivity)
        dialog.setContentView(R.layout.bottom_speed_dialog)
        val speed1xText = dialog.findViewById<TextView>(R.id.speed1xText)
        val speed15xText = dialog.findViewById<TextView>(R.id.speed15xText)
        val speed2xText = dialog.findViewById<TextView>(R.id.speed2xText)
        when (speed) {
            1.0f -> {
                speed15xText?.setTextColor(ContextCompat.getColor(this, R.color.black))
                speed2xText?.setTextColor(ContextCompat.getColor(this, R.color.black))
                speed1xText?.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
            }
            1.5f -> {



                speed15xText?.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
                speed2xText?.setTextColor(ContextCompat.getColor(this, R.color.black))
                speed1xText?.setTextColor(ContextCompat.getColor(this, R.color.black))

            }
            2.0f -> {


                speed15xText?.setTextColor(ContextCompat.getColor(this, R.color.black))
                speed2xText?.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
                speed1xText?.setTextColor(ContextCompat.getColor(this, R.color.black))

            }
        }

        dialog.show()
        dialog.findViewById<LinearLayout>(R.id.speed1x)?.setOnClickListener{
            Toast.makeText(baseContext, R.string.play_speed_at_1x_lit, Toast.LENGTH_SHORT).show()
            speed=1.0f
            playMusic()
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.speed15x)?.setOnClickListener{
            Toast.makeText(baseContext, R.string.play_speed_at_15x_lit, Toast.LENGTH_SHORT).show()
            speed=1.5f
            playMusic()
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.speed2x)?.setOnClickListener{
            Toast.makeText(baseContext, R.string.play_speed_at_2x_lit, Toast.LENGTH_SHORT).show()
            speed=2.0f
            playMusic()
            dialog.dismiss()
        }

    }
}