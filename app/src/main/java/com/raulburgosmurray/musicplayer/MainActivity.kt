package com.raulburgosmurray.musicplayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.gun0912.tedpermission.PermissionListener
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.musicListPA
import com.raulburgosmurray.musicplayer.PlayerActivity.Companion.songPosition
import com.raulburgosmurray.musicplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toogle: ActionBarDrawerToggle
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar

    private var backPressedTime = 0L

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_MEDIA_AUDIO
    )

    private val permissionListener = object : PermissionListener {
        override fun onPermissionGranted() {
            // All permissions granted
            initializeLayout()
            permissionPassed()
        }

        override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
            // Handle denied permissions
            showPermissionDeniedDialog(deniedPermissions)
        }
    }

    companion object{
        lateinit var MusicListMA : ArrayList<Music>
        lateinit var musicListSearch: ArrayList<Music>
        lateinit var musicAdapter: MusicAdapter
        var search = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PermissionManager.checkAndRequestPermissions(this, permissionListener)
    }

    fun permissionPassed(){
        val nowPlaying = binding.nowPlaying
        val recyclerView = binding.musicRV

        nowPlaying.viewTreeObserver.addOnGlobalLayoutListener {
            if(nowPlaying.visibility==View.VISIBLE){
                val fragmentHeight = nowPlaying.height
                recyclerView.setPadding(0, 0, 0, fragmentHeight)
            } else {
                recyclerView.setPadding(0, 0, 0, 0)
            }
        }

        binding.shuffleBtn.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("index", 0)
                putExtra("class", "MainActivity")
            }
            startActivity(intent)

        }

        binding.favoritesBtn.setOnClickListener {
            val intent = Intent(this, FavoritesActivity::class.java)
            startActivity(intent)
        }

        binding.playlistBtn.setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
        }

        // Manage clicks on menu elements
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navFeedback -> Toast.makeText(this, "Feedback", Toast.LENGTH_SHORT).show()
                R.id.navSettings -> Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show()
                R.id.navAbout -> Toast.makeText(this, "About", Toast.LENGTH_SHORT).show()
                R.id.navExit -> {
                    val builder = MaterialAlertDialogBuilder(this)
                    builder.setTitle("Exit")
                        .setMessage("Do you want to close app?")
                        .setPositiveButton("Yes"){_,_ ->
                            Music.exitApplication(applicationContext)
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
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
        //Load favorites
        Music.loadFavorites(applicationContext)
        //Load last player audiobook
        val currentId =  Music.getLastPlayedAudioId(applicationContext)?.let {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                val index = musicAdapter.findIndexById(it)
                if (index == -1) Music.exitApplication(applicationContext)
                putExtra("index", musicAdapter.findIndexById(it))
                putExtra("class", "ContinuePlaying")
            }
            startActivity(intent)
        }
    }

    private fun showPermissionDeniedDialog(deniedPermissions: List<String>?) {
        val message = buildString {
            append(getString(R.string.inaccessible_functionality))
            deniedPermissions?.forEach { permission ->
                when (permission) {
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE ->
                        append(getString(R.string.you_will_not_be_able_to_access_audio_files))

                    Manifest.permission.FOREGROUND_SERVICE,
                    Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK ->
                        append(getString(R.string.you_will_not_be_able_to_reproduce_in_background))
                }
            }
            append(getString(R.string.do_you_want_to_configure_permissions_now))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.required_permissions))
            .setMessage(message)
            .setPositiveButton(getString(R.string.settings)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(getString(R.string.not_now), null)
            .show()
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }


    private fun initializeLayout() {
        setTheme(R.style.coolPinkNav)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //For Nav drawer
        //Makes the status bar transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true
        drawerLayout = binding.DrawerLayout
        navigationView = binding.navView
        toolbar = binding.toolbar
        toolbar.setBackgroundColor(0)
        setSupportActionBar(toolbar)

        toogle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toogle)
        toogle.syncState()

        search = false

        //For ReciclerView
        MusicListMA = getAllAudio(this)


        binding.musicRV.setHasFixedSize(true)
        binding.musicRV.setItemViewCacheSize(13)
        binding.musicRV.layoutManager = LinearLayoutManager(this@MainActivity)
        musicAdapter = MusicAdapter(this@MainActivity, MusicListMA)
        binding.musicRV.adapter = musicAdapter
        binding.totalSongs.text = getString(R.string.total_songs) + " " + musicAdapter.itemCount

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.DrawerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


    }

    @SuppressLint("Recycle", "Range")
    fun getAllAudio(context: Context): ArrayList<Music> {
        val tempList = ArrayList<Music>()
        val contentResolver = context.contentResolver

        val selection = MediaStore.Audio.Media.MIME_TYPE + " LIKE 'audio/%' AND " + MediaStore.Audio.Media.ALBUM + " NOT LIKE 'WhatsApp%'"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val cursor = contentResolver.query(
            collection,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.DATE_ADDED + " DESC",
            null
        )

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    val titleC = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                    val idC = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)) ?: "Unknown"
                    val albumC = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
                    val artistC = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
                    val durationC = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val pathC = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    val albumIdC = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)) ?: "0" //Evita errores si albumId es nulo
                    val artUriC = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumIdC.toLong()).toString() //Use ContentUris to build album art Uri

                    if (durationC > 10000 && !pathC.contains("WhatsApp") && File(pathC).exists()) {
                        val music = Music(
                            id = idC,
                            title = titleC,
                            album = albumC,
                            artist = artistC,
                            duration = durationC,
                            path = pathC,
                            artUri = artUriC
                        )
                        tempList.add(music)
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        tempList.sortBy { it.title }

        // Ahora, extrae los metadatos adicionales de cada archivo
        val musicListWithMetadata = tempList.map { music ->
            // Lanza una Coroutine para leer los metadatos de forma asíncrona
            CoroutineScope(Dispatchers.IO).async {
                try {
                    val audioFile = AudioFileIO.read(File(music.path))
                    val tag = audioFile.tag

                    // Extrae el comentario
                    val comment = tag.getFirst(FieldKey.COMMENT)

                    // Intenta extraer "TRACK_MORE" (puede que no sea un FieldKey estándar)
                    val trackMore = try {
                        tag.getFirst("Track_More")
                    } catch (e: Exception) {
                        null // Si no existe el campo, devuelve null
                    }

                    // Devuelve un nuevo objeto Music con los metadatos adicionales

                    music.copy(comment = comment, trackMore = trackMore)
                } catch (e: Exception) {
                    Log.e("AudioMetadata", "Error reading metadata from ${music.path}: ${e.message}")
                    music // En caso de error, devuelve el objeto original sin metadatos
                }
            }
        }.map { deferred ->
            runBlocking { deferred.await() } // Espera a que cada coroutine termine
        }

        return ArrayList(musicListWithMetadata) // Devuelve la lista actualizada
    }

    override fun onResume() {

        PlayerActivity.musicService?.mediaPlayer?.let { player ->

            Music.savePlaybackState(
                applicationContext,
                musicListPA[songPosition].id,
                player.currentPosition
            )
            Music.saveFavoriteSongs(applicationContext)
            Thread.sleep(500)
        }

        super.onResume()
    }

    override fun onBackPressed() {

        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
            Music.exitApplication(applicationContext)
        } else {
            Toast.makeText(this, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
        }
        backPressedTime = System.currentTimeMillis()
    }

    override fun onDestroy() {
        Music.exitApplication(applicationContext)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search_view_menu, menu)
        val searchView = menu?.findItem(R.id.searchView)?.actionView as SearchView
        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean = true

            override fun onQueryTextChange(newText: String?): Boolean {
                musicListSearch = ArrayList()
                if(newText != null){
                    val userInput = newText.lowercase()
                    for(song in MusicListMA) {
                        if (song.title.lowercase().contains(userInput)) {
                            musicListSearch.add(song)
                        }
                    }
                    search = true
                    musicAdapter.updateMusicList(searchList = musicListSearch)
                }
                return true
            }

        })
        return super.onCreateOptionsMenu(menu)
    }
}
