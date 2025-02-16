package com.raulburgosmurray.musicplayer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
        setTheme(R.style.Theme_MusicPlayer)
        checkAndRequestPermissions()
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

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.entries.all { it.value == true }

        if (granted) {
            Toast.makeText(this@MainActivity, "Permisos concedidos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this@MainActivity, "Permisos denegados", Toast.LENGTH_SHORT).show()
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setMessage("Anteriormente se han rechazado los permisos\nDesea ir a la configuración para concederlos ahora?")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, id ->
                openSettings()
            }
            .setNegativeButton("No") { dialog, id ->
                dialog.dismiss()
            }
        val alert = builder.create()
        alert.show()
    }

    private fun openSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("package:$packageName")
        }.run(::startActivity)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        // Permiso de cámara (siempre necesario en todas las versiones)
        permissions.add(android.Manifest.permission.CAMERA)

        // Permisos de almacenamiento según la versión de Android
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Android 12 o inferior usa READ_EXTERNAL_STORAGE
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Android 13+ usa los nuevos permisos específicos por tipo de archivo
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)

        }

        requestPermission.launch(permissions.toTypedArray())
    }


}