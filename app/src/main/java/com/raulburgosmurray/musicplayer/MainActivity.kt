package com.raulburgosmurray.musicplayer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.raulburgosmurray.musicplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val REQUEST_CODE_WRITE_STORAGE_PERMISSION = 13
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermission()
        setTheme(R.style.Theme_MusicPlayer)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.shuffleBtn.setOnClickListener{
            //Toast.makeText(this@MainActivity, "Button clicked", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
            startActivity(intent)
        }

        binding.favoritesBtn.setOnClickListener{
            startActivity(Intent(this@MainActivity, FavoritesActivity::class.java))
        }

        binding.playlistBtn.setOnClickListener{
            startActivity(Intent(this@MainActivity, PlaylistActivity::class.java))
        }

    }

    //For request permission
    private fun requestRuntimePermission(){
        if(ActivityCompat.checkSelfPermission(this,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_STORAGE_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        when (requestCode) {
            REQUEST_CODE_WRITE_STORAGE_PERMISSION -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this,"Permission Granted", Toast.LENGTH_SHORT).show()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_STORAGE_PERMISSION)
                }
            }
        }
    }
}