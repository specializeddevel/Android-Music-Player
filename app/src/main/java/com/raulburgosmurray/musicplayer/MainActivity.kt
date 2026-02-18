package com.raulburgosmurray.musicplayer

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.net.Uri
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import kotlinx.coroutines.launch
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.media3.common.MediaItem
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.raulburgosmurray.musicplayer.ui.*
import com.raulburgosmurray.musicplayer.ui.theme.MusicPlayerTheme
import java.io.File

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
class MainActivity : ComponentActivity() {

    private lateinit var playbackViewModel: PlaybackViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private var backPressedTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        playbackViewModel = ViewModelProvider(this)[PlaybackViewModel::class.java]
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        
        playbackViewModel.initController(this)

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        TedPermission.create()
            .setPermissionListener(object : PermissionListener {
                override fun onPermissionGranted() {
                    mainViewModel.loadBooks()
                    // Observamos cuando los libros se cargan para restaurar la cola guardada
                    lifecycleScope.launch {
                        mainViewModel.books.collect { books ->
                            if (books.isNotEmpty()) {
                                playbackViewModel.loadPersistedQueue(books)
                            }
                        }
                    }
                    startUI()
                }

                override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                    Toast.makeText(this@MainActivity, "Permiso denegado. La app no puede funcionar.", Toast.LENGTH_LONG).show()
                    finish()
                }
            })
            .setDeniedMessage("Si rechazas el permiso, no podremos encontrar tus audiolibros.\n\nPor favor, actívalo en Ajustes")
            .setPermissions(*permissions)
            .check()
    }

    private fun startUI() {
        setContent {
            val playbackState by playbackViewModel.uiState.collectAsState()
            val isDynamicEnabled by settingsViewModel.isDynamicThemingEnabled.collectAsState()
            val historyLimit by settingsViewModel.historyLimit.collectAsState()
            
            // Sincronizar límite
            playbackViewModel.historyLimit = historyLimit
            
            MusicPlayerTheme(
                dynamicColor = isDynamicEnabled,
                seedColor = if (isDynamicEnabled && playbackState.dominantColor != null) Color(playbackState.dominantColor!!) else null
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    BackHandler(enabled = currentRoute == "main") {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - backPressedTime < 2000) {
                            // 1. Detener el servicio de forma explícita
                            val stopIntent = android.content.Intent(this@MainActivity, PlaybackService::class.java).apply {
                                action = ApplicationClass.EXIT
                            }
                            startService(stopIntent)
                            
                            // 2. Liberar el controlador y cerrar actividad
                            playbackViewModel.releaseController()
                            finish()
                        } else {
                            backPressedTime = currentTime
                            Toast.makeText(this@MainActivity, "Pulsa otra vez para cerrar la aplicación", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    androidx.compose.animation.SharedTransitionLayout {
                        NavHost(
                            navController = navController,
                            startDestination = "main"
                        ) {
                            composable("main") {
                                MainScreen(
                                    mainViewModel = mainViewModel,
                                    playbackViewModel = playbackViewModel,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    onBookClick = { book ->
                                        handleBookClick(book, mainViewModel.books.value, playbackViewModel, navController, "list")
                                    },
                                    onMiniPlayerClick = {
                                        navController.navigate("player/mini")
                                    },
                                    onFavoritesClick = {
                                        navController.navigate("favorites")
                                    },
                                    onSettingsClick = {
                                        navController.navigate("settings")
                                    }
                                )
                            }

                            composable("favorites") {
                                FavoritesScreen(
                                    mainViewModel = mainViewModel,
                                    playbackViewModel = playbackViewModel,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    onBack = { navController.popBackStack() },
                                    onBookClick = { book ->
                                        handleBookClick(book, mainViewModel.favoriteBooks.value, playbackViewModel, navController, "fav")
                                    },
                                    onMiniPlayerClick = {
                                        navController.navigate("player/mini")
                                    }
                                )
                            }

                            composable("settings") {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            
                            composable(
                                route = "player/{from}",
                                arguments = listOf(navArgument("from") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val from = backStackEntry.arguments?.getString("from") ?: "list"
                                PlayerScreen(
                                    viewModel = playbackViewModel,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@composable,
                                    origin = from,
                                    onBack = { 
                                        if (navController.currentDestination?.route?.startsWith("player") == true) {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleBookClick(
        book: Music, 
        currentList: List<Music>, 
        playbackViewModel: PlaybackViewModel, 
        navController: androidx.navigation.NavController,
        from: String
    ) {
        val playbackState = playbackViewModel.uiState.value
        if (playbackState.currentMediaItem?.mediaId == book.id) {
            navController.navigate("player/$from")
            return
        }

        // Ahora solo reproducimos el libro seleccionado, no toda la lista
        val mediaItem = book.toMediaItem()
        playbackViewModel.playPlaylist(listOf(mediaItem), 0)
        navController.navigate("player/$from")
    }
}
