package com.raulburgosmurray.musicplayer

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.Manifest
import android.os.Build
import android.graphics.Bitmap
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.NavType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import com.raulburgosmurray.musicplayer.ui.*
import com.raulburgosmurray.musicplayer.ui.theme.MusicPlayerTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors

class MainViewModelFactory(private val application: android.app.Application, private val settingsViewModel: SettingsViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, settingsViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
class MainActivity : ComponentActivity() {

    private lateinit var playbackViewModel: PlaybackViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var syncViewModel: SyncViewModel
    private lateinit var transferViewModel: LiteraTransferViewModel
    private var backPressedTime = 0L

    private val syncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PlaybackService.SYNC_ACTION) syncViewModel.uploadOnly()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        syncViewModel = ViewModelProvider(this)[SyncViewModel::class.java]
        transferViewModel = ViewModelProvider(this)[LiteraTransferViewModel::class.java]
        val filter = IntentFilter(PlaybackService.SYNC_ACTION)
        ContextCompat.registerReceiver(this, syncReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val mainViewModelFactory = MainViewModelFactory(application, settingsViewModel)
        mainViewModel = ViewModelProvider(this, mainViewModelFactory)[MainViewModel::class.java]
        playbackViewModel = ViewModelProvider(this)[PlaybackViewModel::class.java]
        playbackViewModel.initController(this)
        checkPermissions()
    }

    override fun onDestroy() {
        try { unregisterReceiver(syncReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else { permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE) }
        TedPermission.create().setPermissionListener(object : PermissionListener {
            override fun onPermissionGranted() {
                lifecycleScope.launch { mainViewModel.loadBooks(settingsViewModel.libraryRootUri.first()) }
                lifecycleScope.launch { mainViewModel.books.collect { if (it.isNotEmpty()) playbackViewModel.loadPersistedQueue(it) } }
                startUI()
            }
            override fun onPermissionDenied(deniedPermissions: MutableList<String>?) { startUI() }
        }).setPermissions(*permissions.toTypedArray()).check()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun startUI() {
        setContent {
            val playbackState by playbackViewModel.uiState.collectAsState()
            val isDynamicEnabled by settingsViewModel.isDynamicThemingEnabled.collectAsState()
            val libraryUri by settingsViewModel.libraryRootUri.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                com.raulburgosmurray.musicplayer.ui.ThemeMode.DARK   -> true
                com.raulburgosmurray.musicplayer.ui.ThemeMode.LIGHT  -> false
                com.raulburgosmurray.musicplayer.ui.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            
            // CONECTAR PREFERENCIAS DE SHAKE
            val isShakeEnabled by settingsViewModel.isShakeEnabled.collectAsState()
            val isVibrationEnabled by settingsViewModel.isVibrationEnabled.collectAsState()
            val isSoundEnabled by settingsViewModel.isSoundEnabled.collectAsState()
            
            LaunchedEffect(isShakeEnabled, isVibrationEnabled, isSoundEnabled) {
                playbackViewModel.updateShakePreferences(isShakeEnabled, isVibrationEnabled, isSoundEnabled)
            }

            MusicPlayerTheme(darkTheme = isDark, dynamicColor = isDynamicEnabled, seedColor = if (isDynamicEnabled && playbackState.dominantColor != null) Color(playbackState.dominantColor!!) else null) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    
                    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                        if (uri != null) {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            settingsViewModel.setLibraryRootUri(uri.toString())
                            mainViewModel.loadBooks(uri.toString())
                            navController.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                        }
                    }

                    val exitMessage = stringResource(R.string.press_back_again_to_exit)
                    BackHandler(enabled = currentRoute == "main") {
                        if (System.currentTimeMillis() - backPressedTime < 2000) {
                            startService(android.content.Intent(this@MainActivity, PlaybackService::class.java).apply { action = ApplicationClass.EXIT })
                            playbackViewModel.releaseController()
                            finish()
                        } else { backPressedTime = System.currentTimeMillis(); Toast.makeText(this@MainActivity, exitMessage, Toast.LENGTH_SHORT).show() }
                    }
                    
                    androidx.compose.animation.SharedTransitionLayout {
                        NavHost(
                            navController = navController, 
                            startDestination = if (libraryUri.isNullOrEmpty()) "onboarding" else "main"
                        ) {
                            composable("onboarding") {
                                OnboardingScreen(onSelectFolder = { folderLauncher.launch(null) })
                            }

                            composable("main") { MainScreen(mainViewModel, settingsViewModel, playbackViewModel, this@SharedTransitionLayout, this@composable, onBookClick = { handleBookClick(it, playbackViewModel, navController, "list") }, onMiniPlayerClick = { navController.navigate("player/mini") }, onFavoritesClick = { navController.navigate("favorites") }, onSettingsClick = { navController.navigate("settings") }, onReceiveClick = { navController.navigate("transfer") }, navController = navController) }
                            composable("favorites") { FavoritesScreen(mainViewModel, playbackViewModel, this@SharedTransitionLayout, this@composable, onBack = { navController.popBackStack() }, onBookClick = { handleBookClick(it, playbackViewModel, navController, "fav") }, onMiniPlayerClick = { navController.navigate("player/mini") }, navController = navController) }
                            composable("settings") { SettingsScreen(settingsViewModel, mainViewModel, syncViewModel, onBack = { navController.popBackStack() }) }
                            composable(route = "transfer?bookId={bookId}", arguments = listOf(navArgument("bookId") { type = NavType.StringType; nullable = true; defaultValue = null })) { backStackEntry ->
                                val bookId = backStackEntry.arguments?.getString("bookId")
                                val state by transferViewModel.uiState.collectAsState()
                                val context = LocalContext.current
                                var isManualEntry by remember { mutableStateOf(false) }
                                var targetIpText by remember { mutableStateOf("") }
                                var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
                                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
                                
                                LaunchedEffect(state.transferStatus) {
                                    if (state.transferStatus?.contains("EXITO") == true || state.transferStatus?.contains("recibido") == true || state.transferStatus?.contains("Inbox") == true || state.transferStatus?.contains("Sincronizado") == true) {
                                        delay(Constants.QR_SCAN_DELAY_MS); mainViewModel.loadBooks(settingsViewModel.libraryRootUri.value)
                                    }
                                }
                                LaunchedEffect(bookId) { if (bookId != null) transferViewModel.startServer(bookId) else { if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA); transferViewModel.refreshLocalIp() } }
                                
                                if (state.showConflictDialog) {
                                    AlertDialog(
                                        onDismissRequest = { transferViewModel.handleUserDecision("CANCEL", libraryUri) },
                                        title = { Text(stringResource(R.string.book_detected)) },
                                        text = { Text(stringResource(R.string.existing_book_msg, state.pendingBookTitle ?: "")) },
                                        confirmButton = {
                                            Column {
                                                Button(onClick = { transferViewModel.handleUserDecision("PROGRESS", libraryUri) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_progress_only)) }
                                                Spacer(Modifier.height(8.dp))
                                                Button(onClick = { transferViewModel.handleUserDecision("FILE", libraryUri) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.download_full_book)) }
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { transferViewModel.handleUserDecision("CANCEL", libraryUri) }) { Text(stringResource(R.string.cancel)) }
                                        }
                                    )
                                }

                                Scaffold(topBar = { TopAppBar(title = { Text(if (bookId != null) stringResource(R.string.send) else stringResource(R.string.receive)) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
                                    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        if (state.isDownloading) {
                                            LinearProgressIndicator(progress = { state.downloadProgress }, modifier = Modifier.fillMaxWidth().height(12.dp))
                                            Text("${(state.downloadProgress * 100).toInt()}%")
                                        } else if (bookId != null) {
                                            if (state.error != null) {
                                                Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center); Button(onClick = { transferViewModel.startServer(bookId) }) { Text(stringResource(R.string.retry)) }
                                            } else if (state.isServerRunning) {
                                                state.qrData?.let { data ->
                                                    val qrBitmap = remember(data) {
                                                        try {
                                                            // CONFIGURACION ULTRA-ROBUSTA DE QR
                                                            val hints = mapOf(
                                                                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
                                                                EncodeHintType.CHARACTER_SET to "UTF-8",
                                                                EncodeHintType.MARGIN to 2
                                                            )
                                                            val bitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 512, 512, hints)
                                                            val width = bitMatrix.width
                                                            val height = bitMatrix.height
                                                            val pixels = IntArray(width * height)
                                                            for (y in 0 until height) {
                                                                val offset = y * width
                                                                for (x in 0 until width) {
                                                                    pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                                                                }
                                                            }
                                                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                                            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                                                            bitmap
                                                        } catch (e: Exception) { null }
                                                    }
                                                    qrBitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(300.dp)) }
                                                    if (state.qrPositionMs > 0 && state.qrDurationMs > 0) {
                                                        Spacer(Modifier.height(8.dp))
                                                        val posSec = state.qrPositionMs / 1000
                                                        val durSec = state.qrDurationMs / 1000
                                                        Text(
                                                            "${posSec / 60}:${String.format("%02d", posSec % 60)} / ${durSec / 60}:${String.format("%02d", durSec % 60)}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                Text(stringResource(R.string.qr_generated))
                                            } else { CircularProgressIndicator() }
                                        } else if (isManualEntry) {
                                            OutlinedTextField(value = targetIpText, onValueChange = { targetIpText = it }, label = { Text("IP:Puerto") }, modifier = Modifier.fillMaxWidth())
                                            Button(onClick = { transferViewModel.receiveFromIp(targetIpText, libraryUri) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.connecting)) }
                                            TextButton(onClick = { isManualEntry = false }) { Text(stringResource(R.string.camera)) }
                                        } else {
                                            if (hasCameraPermission) {
                                                Box(modifier = Modifier.size(320.dp).padding(8.dp)) {
                                                    val lifecycleOwner = LocalLifecycleOwner.current
                                                    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
                                                    AndroidView(factory = { ctx ->
                                                        val previewView = PreviewView(ctx); cameraProviderFuture.addListener({
                                                            try {
                                                                val cameraProvider = cameraProviderFuture.get(); val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                                                                val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                                                                val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                                                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                                                    val mediaImage = imageProxy.image
                                                                    if (mediaImage != null) {
                                                                        scanner.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)).addOnSuccessListener { barcodes -> barcodes.forEach { it.rawValue?.let { transferViewModel.processScannedData(it, libraryUri) } } }.addOnCompleteListener { imageProxy.close() }
                                                                    }
                                                                }
                                                                cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                                                            } catch (e: Exception) {
                                                                Log.e("MainActivity", "Error al inicializar cámara QR", e)
                                                            }
                                                        }, ContextCompat.getMainExecutor(ctx)); previewView
                                                    }, modifier = Modifier.fillMaxSize())
                                                }
                                                Button(onClick = { isManualEntry = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manual)) }
                                            } else { Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.permission)) } }
                                        }
                                        Spacer(Modifier.height(24.dp))
                                        Text(stringResource(R.string.your_ip, state.localIp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                        state.transferStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                        Spacer(Modifier.height(32.dp))
                                        Button(
                                            onClick = { transferViewModel.stopServer(); navController.popBackStack() },
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.finish_and_exit), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
composable(route = "player/{from}", arguments = listOf(navArgument("from") { type = NavType.StringType })) { backStackEntry ->
                                val sScope = this@SharedTransitionLayout
                                val aScope = this@composable
                                PlayerScreen(playbackViewModel, sScope, aScope, backStackEntry.arguments?.getString("from") ?: "list", onBack = { if (navController.currentDestination?.route?.startsWith("player") == true) navController.popBackStack() }, onTransferClick = { bookId ->
                                    navController.navigate("transfer?bookId=${Uri.encode(bookId)}")
                                }, navController = navController)
                            }
                            composable(route = "metadata_editor?bookId={bookId}", arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { backStackEntry ->
                                val bookId = decodeBookId(backStackEntry.arguments?.getString("bookId") ?: return@composable)
                                val viewModel: MetadataEditorViewModel = viewModel()
                                LaunchedEffect(bookId) { viewModel.loadMetadata(bookId) }
                                MetadataEditorScreen(viewModel, onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleBookClick(book: Music, playbackViewModel: PlaybackViewModel, navController: androidx.navigation.NavController, from: String) {
        if (playbackViewModel.uiState.value.currentMediaItem?.mediaId == book.id) { navController.navigate("player/$from"); return }
        playbackViewModel.playPlaylist(listOf(book.toMediaItem()), 0)
navController.navigate("player/$from")
    }
}
