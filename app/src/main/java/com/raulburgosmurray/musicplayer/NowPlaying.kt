package com.raulburgosmurray.musicplayer

import android.content.Intent
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicListPA
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicService
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.songPosition
import com.raulburgosmurray.musicplayer.databinding.FragmentNowPlayingBinding

class NowPlaying : Fragment() {

    companion object {
        lateinit var binding: FragmentNowPlayingBinding

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_now_playing, container, false)
        binding = FragmentNowPlayingBinding.bind(view)
        binding.root.visibility = View.GONE
        binding.playPauseBtnNP.setOnClickListener{
            if(PlayerActivity.isPlaying) {
                val context = context
                context?.let {
                    musicService!!.mediaPlayer?.let { player ->
                        Music.savePlaybackState(context,musicListPA[songPosition].id, player.currentPosition)
                    }
                }
                pauseMusic()
            } else {
                val context = context
                context?.let {
                    musicService!!.mediaPlayer?.let { player ->
                        Music.restorePlaybackState(context, musicListPA[songPosition].id)
                    }
                }
                playMusic()
            }

        }
        binding.nextBtnNP.setOnClickListener{
            try {
                if (MainActivity.MusicListMA.size > 1) {
                    Music.setSongPosition(increment = true)
                    PlayerActivity.musicService!!.createMediaPlayer()
                    Glide.with(this)
                        .load(musicListPA[songPosition].artUri)
                        .apply(
                            RequestOptions().placeholder(R.drawable.ic_audiobook_cover)
                                .centerInside()
                        )
                        .into(binding.songImgNP)
                    binding.songNameNP.text =
                        PlayerActivity.musicListPA[PlayerActivity.songPosition].title
                    PlayerActivity.musicService!!.showNotification(R.drawable.pause_icon)
                    if(!MainActivity.search){
                        MainActivity.musicAdapter.updateMusicList(MainActivity.MusicListMA)
                    }
                    playMusic()
                }
            } catch (_: Exception){}

        }
        binding.root.setOnClickListener{
            val intent = Intent(requireContext(), PlayerActivity::class.java)
            intent.putExtra("index", PlayerActivity.songPosition)
            intent.putExtra("class", "NowPlaying")
            ContextCompat.startActivity(requireContext(), intent, null)
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        if(PlayerActivity.musicService != null){
            binding.root.visibility = View.VISIBLE
            binding.songNameNP.isSelected = true
            Glide.with(this)
                .load(musicListPA[songPosition].artUri)
                .apply(RequestOptions().placeholder(R.drawable.ic_audiobook_cover).centerInside())
                .into(binding.songImgNP)
            binding.songNameNP.text = PlayerActivity.musicListPA[PlayerActivity.songPosition].title
            if(PlayerActivity.isPlaying) {
                binding.playPauseBtnNP.setIconResource(R.drawable.pause_icon)
            } else {
                binding.playPauseBtnNP.setIconResource(R.drawable.play_icon)
            }
        }
    }

    private fun pauseMusic() {
        PlayerActivity.musicService!!.mediaPlayer.pause()
        binding.playPauseBtnNP.setIconResource(R.drawable.play_icon)
        PlayerActivity.musicService!!.showNotification(R.drawable.play_icon)
        PlayerActivity.binding.nextBtnPA.setIconResource(R.drawable.play_icon)
        PlayerActivity.isPlaying = false
    }

    private fun playMusic(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val playbackParams = PlaybackParams()
            playbackParams.speed = PlayerActivity.speed
            PlayerActivity.musicService!!.mediaPlayer.playbackParams = playbackParams
        }
        PlayerActivity.musicService!!.mediaPlayer.start()
        binding.playPauseBtnNP.setIconResource(R.drawable.pause_icon)
        PlayerActivity.musicService!!.showNotification(R.drawable.pause_icon)
        PlayerActivity.binding.nextBtnPA.setIconResource(R.drawable.pause_icon)
        PlayerActivity.isPlaying = true

    }


}