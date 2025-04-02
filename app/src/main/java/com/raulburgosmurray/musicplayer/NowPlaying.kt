package com.raulburgosmurray.musicplayer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicListPA
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
                pauseMusic()
            } else {
                playMusic()
            }

        }
        return view
    }

    override fun onResume() {
        super.onResume()
        if(PlayerActivity.musicService != null){
            binding.root.visibility = View.VISIBLE
            Glide.with(this)
                .load(musicListPA[songPosition].artUri)
                .apply(RequestOptions().placeholder(R.drawable.ic_audiobook_cover).centerCrop())
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
        PlayerActivity.musicService!!.mediaPlayer.start()
        binding.playPauseBtnNP.setIconResource(R.drawable.pause_icon)
        PlayerActivity.musicService!!.showNotification(R.drawable.pause_icon)
        PlayerActivity.binding.nextBtnPA.setIconResource(R.drawable.pause_icon)
        PlayerActivity.isPlaying = true

    }


}