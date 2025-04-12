package com.raulburgosmurray.musicplayer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.raulburgosmurray.musicplayer.MainActivity.Companion.MusicListMA
import com.raulburgosmurray.musicplayer.MainActivity.Companion.musicAdapter
import com.raulburgosmurray.musicplayer.databinding.ActivityFavoritesBinding

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding:ActivityFavoritesBinding
    private lateinit var favoriteAdapter: FavoriteAdapter

    companion object{
        var favoriteSongs: ArrayList<Music> = ArrayList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.coolPink)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.DrawerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.backBtnFA.setOnClickListener{
            finish()
        }
        binding.favoriteRV.setHasFixedSize(true)
        binding.favoriteRV.setItemViewCacheSize(13)
        binding.favoriteRV.layoutManager = GridLayoutManager(this@FavoritesActivity,4)
        favoriteAdapter = FavoriteAdapter(this@FavoritesActivity, favoriteSongs)
        binding.favoriteRV.adapter = favoriteAdapter
    }
}