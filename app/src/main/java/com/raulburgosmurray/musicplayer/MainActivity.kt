package com.raulburgosmurray.musicplayer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.raulburgosmurray.musicplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    private lateinit var toogle: ActionBarDrawerToggle
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MusicPlayer)

        permissionManager = PermissionManager(this@MainActivity)
        permissionManager.requestPermissions()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)



        //For Nav drawer
        // Hace la barra de estado transparente
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = true
        drawerLayout = binding.DrawerLayout
        navigationView = binding.navigationView
        toolbar = binding.toolbar
        toolbar.setBackgroundColor(0)
        setSupportActionBar(toolbar)

        toogle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toogle)
        toogle.syncState()


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.DrawerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.shuffleBtn.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }

        binding.favoritesBtn.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

        binding.playlistBtn.setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
        }

        // Manejar clics en los elementos del menú
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> Toast.makeText(this, "Inicio seleccionado", Toast.LENGTH_SHORT).show()
                R.id.nav_settings -> Toast.makeText(this, "Configuraciones seleccionadas", Toast.LENGTH_SHORT).show()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(toogle.onOptionsItemSelected(item))
            return true
        return super.onOptionsItemSelected(item)
    }
}
