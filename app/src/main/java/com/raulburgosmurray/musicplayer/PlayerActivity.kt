package com.raulburgosmurray.musicplayer

import android.app.ComponentCaller
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.text.Html
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.raulburgosmurray.musicplayer.Music.Companion.formatDuration
import com.raulburgosmurray.musicplayer.Music.Companion.setSongPosition
import com.raulburgosmurray.musicplayer.databinding.ActivityPlayerBinding
import com.raulburgosmurray.musicplayer.databinding.BsSpeedSelectionBinding

class PlayerActivity : AppCompatActivity(), ServiceConnection, MediaPlayer.OnCompletionListener {

    companion object {
        lateinit var musicListPA : ArrayList<Music>
        var songPosition : Int = 0
        var isPlaying = false
        var musicService : MusicService? = null
        lateinit var binding: ActivityPlayerBinding
        var repeat = false
        var sleepMins = 0
        var speed = 1.0f
        var nowPlayingId: String = ""
        var isResumed = false
        var isFavorite = false
        var fIndex = -1
        private var sleepTimer: CountDownTimer? = null
    }

    enum class SleepTimer(@StringRes val labelResId: Int, val millis: Long, val minutes: Int) {
        MIN_15(R.string.min_15, 15 * 60 * 1000L, 15),
        MIN_30(R.string.min_30, 30 * 60 * 1000L, 30),
        MIN_60(R.string.min_60, 60 * 60 * 1000L, 60);

        fun getDisplayName(context: Context): String {
            return context.getString(labelResId)
        }
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
            } else {
                musicService!!.mediaPlayer?.let { _ ->
                    val savedPosition = Music.restorePlaybackState(applicationContext, musicListPA[songPosition].id)
                }
                playMusic()
            }
        }

        binding.replay10PA.setOnClickListener{
            skipBackward(10000)
        }

        binding.replay10PA.setOnLongClickListener {
            skipBackward(30000)
            true
        }

        binding.replay60PA.setOnClickListener{
            skipBackward(60000)
        }

        binding.replay60PA.setOnLongClickListener{
            skipBackward(300000)
            true
        }

        binding.previusBtnPA.setOnClickListener{
            prevNextSong(false)
        }

        binding.nextBtnPA.setOnClickListener{
            prevNextSong(true)
        }

        binding.forward10PA.setOnClickListener{
            skipForward(10000)
        }

        binding.forward10PA.setOnLongClickListener {
            skipForward(30000)
            true
        }

        binding.forward60PA.setOnClickListener{
            skipForward(60000)
        }

        binding.forward60PA.setOnLongClickListener{
            skipForward(300000)
            true
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
                showBottomSheetDialog()
            //showTimmerBottomSheetDialog()
        }

        binding.textTimerOn.setOnClickListener {
                val builder = MaterialAlertDialogBuilder(this)
                builder.setTitle(getString(R.string.stop_timer))
                    .setMessage(getString(R.string.do_you_want_to_cancel_the_timer))
                    .setPositiveButton(R.string.yes){_,_ ->
                        cancelSleepTimer()
                    }
                    .setNegativeButton(R.string.no) { dialog, _ ->
                        dialog.dismiss()
                    }
                val customDialog = builder.create()
                customDialog.show()
                customDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
                customDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.RED)
        }

        binding.shareBtnPA.setOnClickListener {
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.type = "audio/*"
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(musicListPA[songPosition].path))
            startActivity(Intent.createChooser(shareIntent,
                getString(R.string.sharing_your_audiobook_file)))
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
            FavoritesActivity.favoritesChanged = true
        }
    }

    private fun setLayout(){

        fIndex = Music.favoriteChecker(musicListPA[songPosition].id)
        binding.songNamePA.text = musicListPA[songPosition].title
        if(repeat) binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(this,R.color.purple_500))

        if(isFavorite) binding.favoriteBtnPA.setImageResource(R.drawable.favourite_icon)
        else binding.favoriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)

        val img = Music.getImgArt(musicListPA[songPosition].path)
        val image = Music.decodeImage(applicationContext, img)
        binding.songImgPA.setImageBitmap(image)
        val bgColor = Music.getDominantColor(image)
        val optimalContrastColor = Music.getOptimalContrastColor(bgColor)
        val intermediateColor = Music.getIntermediateColor(bgColor,optimalContrastColor)
        val intermediateColor2 = Music.getIntermediateColor(bgColor,optimalContrastColor,0.8f)

        val gradient = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(bgColor,optimalContrastColor))
        binding.root.background = gradient
        window?.statusBarColor = bgColor

        binding.windowTitlePA.setTextColor(bgColor)
        binding.backBtnPA.imageTintList = ColorStateList.valueOf(bgColor)
        binding.favoriteBtnPA.imageTintList = ColorStateList.valueOf(bgColor)
        binding.songNamePA.setTextColor(optimalContrastColor)
        binding.tvSeekBarStart.setTextColor(optimalContrastColor)
        binding.tvSeekBarEnd.setTextColor(optimalContrastColor)
        binding.repeatBtnPA.imageTintList = ColorStateList.valueOf(optimalContrastColor)
        binding.equalizerBtnPA.imageTintList = ColorStateList.valueOf(optimalContrastColor)
        when (speed) {
            0.5f -> { val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_0_5x_icon)
                binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            }
            0.75f -> { val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_0_75x_icon)
                binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            }
            1.0f -> { val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_1x_icon)
                binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            }
            1.5f -> { val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_1_5x_icon)
                binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            }
            2.0f -> { val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_2x_icon)
                binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            }
            else -> {
                val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_icon)
                binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            }
        }
        binding.timerBtnPA.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.timer_icon))
        if(sleepMins==0) {
            binding.timerBtnPA.visibility = View.VISIBLE
            binding.textTimerOn.visibility = View.GONE
        } else {
            binding.timerBtnPA.visibility = View.GONE
            binding.textTimerOn.visibility = View.VISIBLE
        }
        binding.timerBtnPA.imageTintList = ColorStateList.valueOf(optimalContrastColor)
        binding.textTimerOn.setTextColor(optimalContrastColor)
        binding.shareBtnPA.imageTintList = ColorStateList.valueOf(optimalContrastColor)
        binding.speedBtnPA.imageTintList = ColorStateList.valueOf(optimalContrastColor)
        binding.seekBarPA.thumbTintList = ColorStateList.valueOf(optimalContrastColor)
        binding.seekBarPA.progressTintList = ColorStateList.valueOf(optimalContrastColor)
        binding.seekBarPA.progressBackgroundTintList = ColorStateList.valueOf(optimalContrastColor)
        binding.playPauseBtnPA.iconTint = ColorStateList.valueOf(intermediateColor2)
        binding.playPauseBtnPA.setBackgroundColor(intermediateColor)
        binding.forward10PA.iconTint = ColorStateList.valueOf(intermediateColor2)
        binding.forward10PA.setBackgroundColor(intermediateColor)
        binding.forward60PA.iconTint = ColorStateList.valueOf(intermediateColor2)
        binding.forward60PA.setBackgroundColor(intermediateColor)
        binding.replay10PA.iconTint = ColorStateList.valueOf(intermediateColor2)
        binding.replay10PA.setBackgroundColor(intermediateColor)
        binding.replay60PA.iconTint = ColorStateList.valueOf(intermediateColor2)
        binding.replay60PA.setBackgroundColor(intermediateColor)
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
        musicService!!.mediaPlayer?.let { player ->
            val currentPosition = player.currentPosition
            Music.savePlaybackState(applicationContext, musicListPA[songPosition].id, currentPosition)
        }
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
        musicService!!.mediaPlayer?.let { player ->
            Music.savePlaybackState(applicationContext,musicListPA[songPosition].id, player.currentPosition)
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

    private lateinit var bsSheetBinding: BsSpeedSelectionBinding

    private fun showTimmerBottomSheetDialog() {
        bsSheetBinding = BsSpeedSelectionBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this).apply {
            setContentView(bsSheetBinding.root)
        }

        dialog.show()
    }

    private fun showBottomSheetDialog(){
        val dialog = BottomSheetDialog(this@PlayerActivity)
        dialog.setContentView(R.layout.bottom_sheet_dialog)
        dialog.show()
        dialog.findViewById<LinearLayout>(R.id.min_15)?.setOnClickListener {
            // Cancel previous timer if it exists
            cancelSleepTimer()
            binding.timerBtnPA.visibility = View.GONE
            binding.textTimerOn.visibility = View.VISIBLE
            sleepMins=15

            val totalTimeMillis = SleepTimer.MIN_15.millis
            sleepTimer = object : CountDownTimer(totalTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    // Update the textView with the remaining time
                    binding.textTimerOn.text = String.format(Music.formatTime(millisUntilFinished))
                }

                override fun onFinish() {
                    cancelSleepTimer()
                    pauseMusic()
                }
            }.start()

            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.min_30)?.setOnClickListener {
            // Cancel previous timer if it exists
            cancelSleepTimer()
            binding.timerBtnPA.visibility = View.GONE
            binding.textTimerOn.visibility = View.VISIBLE
            sleepMins=30

            val totalTimeMillis = SleepTimer.MIN_30.millis
            sleepTimer = object : CountDownTimer(totalTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    // Update the textView with the remaining time
                    binding.textTimerOn.text = String.format(Music.formatTime(millisUntilFinished))
                }

                override fun onFinish() {
                   cancelSleepTimer()
                    pauseMusic()
                }
            }.start()
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.min_60)?.setOnClickListener {
            // Cancel previous timer if it exists
            cancelSleepTimer()
            binding.timerBtnPA.visibility = View.GONE
            binding.textTimerOn.visibility = View.VISIBLE
            sleepMins=60

            val totalTimeMillis = SleepTimer.MIN_60.millis
            sleepTimer = object : CountDownTimer(totalTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    // Update the textView with the remaining time
                    binding.textTimerOn.text = Music.formatTime(millisUntilFinished)
                }

                override fun onFinish() {
                    cancelSleepTimer()
                    pauseMusic()
                }
            }.start()
            dialog.dismiss()
        }
    }

    fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepMins = 0
        sleepTimer = null
        binding.textTimerOn.text = ""
        val icon = ContextCompat.getDrawable(this, R.drawable.timer_icon)
        binding.timerBtnPA.setImageDrawable(icon)
        binding.textTimerOn.visibility = View.GONE
        binding.timerBtnPA.visibility = View.VISIBLE
    }



    private fun showBottomSheetSpeedDialog(){
        val dialog = BottomSheetDialog(this@PlayerActivity)
        dialog.setContentView(R.layout.bottom_speed_dialog)
        val speed05xText = dialog.findViewById<TextView>(R.id.speed05xText)
        val speed07xText = dialog.findViewById<TextView>(R.id.speed07xText)
        val speed1xText = dialog.findViewById<TextView>(R.id.speed1xText)
        val speed15xText = dialog.findViewById<TextView>(R.id.speed15xText)
        val speed2xText = dialog.findViewById<TextView>(R.id.speed2xText)
        speed05xText?.setTextColor(ContextCompat.getColor(this, R.color.black))
        speed07xText?.setTextColor(ContextCompat.getColor(this, R.color.black))
        speed15xText?.setTextColor(ContextCompat.getColor(this, R.color.black))
        speed2xText?.setTextColor(ContextCompat.getColor(this, R.color.black))
        speed1xText?.setTextColor(ContextCompat.getColor(this, R.color.black))
        when (speed) {
            0.5f -> {
                speed05xText?.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
            }
            0.7f -> {
                speed07xText?.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
            }
            1.0f -> {
                speed1xText?.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
            }
            1.5f -> {
                speed15xText?.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
            }
            2.0f -> {
                speed2xText?.setTextColor(ContextCompat.getColor(this, R.color.purple_700))
            }
        }
        dialog.show()
        dialog.findViewById<LinearLayout>(R.id.speed05x)?.setOnClickListener{
            speed=0.5f
            dialog.dismiss()
            val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_0_5x_icon)
            binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            playMusic()
        }
        dialog.findViewById<LinearLayout>(R.id.speed07x)?.setOnClickListener{
            speed=0.7f
            dialog.dismiss()
            val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_0_7x_icon)
            binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            playMusic()
        }
        dialog.findViewById<LinearLayout>(R.id.speed1x)?.setOnClickListener{
            speed=1.0f
            dialog.dismiss()
            val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_1x_icon)
            binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            playMusic()
        }
        dialog.findViewById<LinearLayout>(R.id.speed15x)?.setOnClickListener{
            speed=1.5f
            dialog.dismiss()
            val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_1_5x_icon)
            binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            playMusic()
        }
        dialog.findViewById<LinearLayout>(R.id.speed2x)?.setOnClickListener{
            speed=2.0f
            dialog.dismiss()
            val playSpeedIcon = ContextCompat.getDrawable(this, R.drawable.speed_2x_icon)
            binding.speedBtnPA.setImageDrawable(playSpeedIcon)
            playMusic()
        }
    }
}