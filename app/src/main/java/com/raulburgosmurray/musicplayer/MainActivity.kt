package com.raulburgosmurray.musicplayer

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.Manifest
import android.os.Build
import android.provider.Settings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.ExperimentalGetImage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.NavType
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.raulburgosmurray.musicplayer.ui.*
import com.raulburgosmurray.musicplayer.ui.theme.MusicPlayerTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class MainViewModelFactory(private val application: android.app.Application, private val settingsViewModel: SettingsViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, settingsViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private data class PermissionPrompt(
    val permission: String,
    @StringRes val titleRes: Int,
    @StringRes val rationaleRes: Int,
    @StringRes val deniedTitleRes: Int,
    @StringRes val deniedMessageRes: Int,
    val onGranted: () -> Unit = {}
)

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Suppress("UnsafeOptInUsageError")
class MainActivity : ComponentActivity() {

    private lateinit var playbackViewModel: PlaybackViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var syncViewModel: SyncViewModel
    private lateinit var transferViewModel: LiteraTransferViewModel
    private var sleepDetectionReceiver: com.raulburgosmurray.musicplayer.sleep.SleepDetectionReceiver? = null
    private var isSyncReceiverRegistered = false
    private var backPressedTime = 0L

    private val permissionRequests = PermissionRequestQueue<PermissionPrompt>()
    private var permissionPrompt by mutableStateOf<PermissionPrompt?>(null)
    private var deniedPermissionPrompt by mutableStateOf<PermissionPrompt?>(null)
    private var activePermissionRequest: PermissionPrompt? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = activePermissionRequest ?: return@registerForActivityResult
        activePermissionRequest = null
        if (granted) {
            request.onGranted()
            completePermissionRequest()
        } else {
            deniedPermissionPrompt = request
        }
    }

    private val syncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PlaybackService.SYNC_ACTION) syncViewModel.uploadOnly()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenOrientationPolicy()
        enableEdgeToEdge()
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        syncViewModel = ViewModelProvider(this)[SyncViewModel::class.java]
        transferViewModel = ViewModelProvider(this)[LiteraTransferViewModel::class.java]
        if (FeatureFlags.CLOUD_SYNC) {
            val filter = IntentFilter(PlaybackService.SYNC_ACTION)
            ContextCompat.registerReceiver(this, syncReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            isSyncReceiverRegistered = true
        }
        val mainViewModelFactory = MainViewModelFactory(application, settingsViewModel)
        mainViewModel = ViewModelProvider(this, mainViewModelFactory)[MainViewModel::class.java]
        playbackViewModel = ViewModelProvider(this)[PlaybackViewModel::class.java]
        playbackViewModel.initController(this)
        if (FeatureFlags.SLEEP_DETECTION) {
            observeSleepDetectionSettings()
        }

        lifecycleScope.launch {
            mainViewModel.books.collect { if (it.isNotEmpty()) playbackViewModel.loadPersistedQueue(it) }
        }

        startUI()
        checkPermissions()
    }

    private fun observeSleepDetectionSettings() {
        lifecycleScope.launch {
            combine(
                settingsViewModel.isSleepDetectionEnabled,
                settingsViewModel.sleepDetectionPort
            ) { enabled, port -> enabled to port }
                .collect { (enabled, port) ->
                    if (enabled) {
                        startSleepDetectionReceiver(port)
                    } else {
                        sleepDetectionReceiver?.stopListening()
                    }
                }
        }
    }

    private fun startSleepDetectionReceiver(port: Int) {
        if (sleepDetectionReceiver == null) {
            sleepDetectionReceiver = com.raulburgosmurray.musicplayer.sleep.SleepDetectionReceiver(
                context = this,
                onSleepDetected = { sleepOnsetMinutes ->
                    runOnUiThread {
                        playbackViewModel.handleSleepDetected(sleepOnsetMinutes)
                    }
                }
            )
        }
        sleepDetectionReceiver?.startListening(port)
    }

    override fun onDestroy() {
        if (isSyncReceiverRegistered) {
            try {
                unregisterReceiver(syncReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w("MainActivity", "syncReceiver was already unregistered", e)
            } finally {
                isSyncReceiverRegistered = false
            }
        }
        sleepDetectionReceiver?.stopListening()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyScreenOrientationPolicy()
    }

    private fun getStoragePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun checkPermissions() {
        val storagePermission = getStoragePermission()

        if (ContextCompat.checkSelfPermission(this, storagePermission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionWithExplanation(audioPermissionPrompt(::loadBooksAfterAudioPermission))
        } else {
            loadBooksAfterAudioPermission()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionWithExplanation(
                PermissionPrompt(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    titleRes = R.string.notification_permission_title,
                    rationaleRes = R.string.notification_permission_rationale,
                    deniedTitleRes = R.string.notification_permission_denied_title,
                    deniedMessageRes = R.string.notification_permission_denied_message
                )
            )
        }
    }

    private fun loadBooksAfterAudioPermission() {
        lifecycleScope.launch {
            val uris = settingsViewModel.libraryRootUris.first()
            val scanAll = settingsViewModel.scanAllMemory.first()
            mainViewModel.smartLoadBooks(if (scanAll) emptyList() else uris, scanAll)
        }
    }

    private fun audioPermissionPrompt(onGranted: () -> Unit): PermissionPrompt = PermissionPrompt(
        permission = getStoragePermission(),
        titleRes = R.string.storage_permission_title,
        rationaleRes = R.string.storage_permission_rationale,
        deniedTitleRes = R.string.storage_permission_denied_title,
        deniedMessageRes = R.string.storage_permission_denied_message,
        onGranted = onGranted
    )

    private fun cameraPermissionPrompt(onGranted: () -> Unit): PermissionPrompt = PermissionPrompt(
        permission = Manifest.permission.CAMERA,
        titleRes = R.string.camera_permission_title,
        rationaleRes = R.string.camera_permission_rationale,
        deniedTitleRes = R.string.camera_permission_denied_title,
        deniedMessageRes = R.string.camera_permission_denied_message,
        onGranted = onGranted
    )

    private fun requestPermissionWithExplanation(request: PermissionPrompt) {
        if (ContextCompat.checkSelfPermission(this, request.permission) == PackageManager.PERMISSION_GRANTED) {
            request.onGranted()
            return
        }

        permissionRequests.enqueue(request)
        if (permissionPrompt == null && activePermissionRequest == null && deniedPermissionPrompt == null) {
            permissionPrompt = permissionRequests.current
        }
    }

    private fun launchCurrentPermissionRequest() {
        val request = permissionPrompt ?: return
        permissionPrompt = null
        activePermissionRequest = request
        permissionLauncher.launch(request.permission)
    }

    private fun completePermissionRequest() {
        deniedPermissionPrompt = null
        permissionPrompt = permissionRequests.advance()
    }

    private fun openAppPermissionSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun startUI() {
        setContent {
            val playbackState by playbackViewModel.uiState.collectAsState()
            val isDynamicEnabled by settingsViewModel.isDynamicThemingEnabled.collectAsState()
            val libraryRootUris by settingsViewModel.libraryRootUris.collectAsState()
            val scanAllMemory by settingsViewModel.scanAllMemory.collectAsState()
            val hasCompletedOnboarding by settingsViewModel.hasCompletedOnboarding.collectAsState()
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

                    permissionPrompt?.let { request ->
                        PermissionExplanationDialog(
                            permission = request.permission,
                            title = stringResource(request.titleRes),
                            message = stringResource(request.rationaleRes),
                            confirmLabel = stringResource(R.string.permission_continue),
                            dismissLabel = stringResource(R.string.not_now),
                            onConfirm = ::launchCurrentPermissionRequest,
                            onDismiss = ::completePermissionRequest
                        )
                    }

                    deniedPermissionPrompt?.let { request ->
                        PermissionExplanationDialog(
                            permission = request.permission,
                            title = stringResource(request.deniedTitleRes),
                            message = stringResource(request.deniedMessageRes),
                            confirmLabel = stringResource(R.string.settings),
                            dismissLabel = stringResource(R.string.not_now),
                            onConfirm = {
                                completePermissionRequest()
                                openAppPermissionSettings()
                            },
                            onDismiss = ::completePermissionRequest
                        )
                    }
                    
                    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                        if (uri != null) {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            settingsViewModel.addLibraryRootUri(uri.toString())
                            settingsViewModel.setScanAllMemory(false)
                            mainViewModel.loadBooks(listOf(uri.toString()), false)
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
                            startDestination = if (!hasCompletedOnboarding) "onboarding" else "main"
                        ) {
                            composable("onboarding") {
                                OnboardingScreen(
                                    onSelectFolder = { folderLauncher.launch(null) },
                                    onScanAllMemory = {
                                        requestPermissionWithExplanation(audioPermissionPrompt {
                                            settingsViewModel.setScanAllMemory(true)
                                            mainViewModel.loadBooks(emptyList(), true)
                                            navController.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                                        })
                                    },
                                    onTransferByQr = if (FeatureFlags.P2P_TRANSFER) {
                                        {
                                            requestPermissionWithExplanation(cameraPermissionPrompt {
                                                navController.navigate("transfer?fromOnboarding=true")
                                            })
                                        }
                                    } else null
                                )
                            }

                            composable("main") {
                                // A NavHost keeps its initial route. If preferences are
                                // restored after that decision, avoid exposing the old
                                // empty-library screen when no folder has been selected.
                                if (libraryRootUris.isEmpty() && !scanAllMemory) {
                                    OnboardingScreen(
                                        onSelectFolder = { folderLauncher.launch(null) },
                                        onScanAllMemory = {
                                            requestPermissionWithExplanation(audioPermissionPrompt {
                                                settingsViewModel.setScanAllMemory(true)
                                                mainViewModel.loadBooks(emptyList(), true)
                                            })
                                        },
                                        onTransferByQr = if (FeatureFlags.P2P_TRANSFER) {
                                            {
                                                requestPermissionWithExplanation(cameraPermissionPrompt {
                                                    navController.navigate("transfer?fromOnboarding=true")
                                                })
                                            }
                                        } else null
                                    )
                                } else {
                                    MainScreen(mainViewModel, settingsViewModel, playbackViewModel, this@SharedTransitionLayout, this@composable, onBookClick = { handleBookClick(it, playbackViewModel, navController, "list") }, onMiniPlayerClick = { navController.navigate("player/mini") }, onFavoritesClick = { navController.navigate("favorites") }, onSettingsClick = { navController.navigate("settings") }, onReceiveClick = { navController.navigate("transfer") }, onSelectFolder = { folderLauncher.launch(null) }, navController = navController)
                                }
                            }
                            composable("favorites") { FavoritesScreen(mainViewModel, playbackViewModel, this@SharedTransitionLayout, this@composable, onBack = { navController.popBackStack() }, onBookClick = { handleBookClick(it, playbackViewModel, navController, "fav") }, onMiniPlayerClick = { navController.navigate("player/mini") }, navController = navController) }
                            composable("settings") { SettingsScreen(settingsViewModel, mainViewModel, syncViewModel, playbackViewModel, onBack = { navController.popBackStack() }) }
                            composable(route = "transfer?bookId={bookId}&fromOnboarding={fromOnboarding}", arguments = listOf(navArgument("bookId") { type = NavType.StringType; nullable = true; defaultValue = null }, navArgument("fromOnboarding") { type = NavType.BoolType; defaultValue = false })) { backStackEntry ->
                                val bookId = backStackEntry.arguments?.getString("bookId")
                                val fromOnboarding = backStackEntry.arguments?.getBoolean("fromOnboarding") ?: false
                                val state by transferViewModel.uiState.collectAsState()
                                var transferScreenInitialized by remember(backStackEntry.id) { mutableStateOf(false) }
                                val context = LocalContext.current
                                var isManualEntry by remember { mutableStateOf(false) }
                                var targetIpText by remember { mutableStateOf("") }
                                var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
                                
                                LaunchedEffect(state.receiveSuccess, transferScreenInitialized) {
                                    if (transferScreenInitialized && transferViewModel.uiState.value.receiveSuccess) {
                                        transferViewModel.clearReceiveSuccess()
                                        if (fromOnboarding) {
                                            navController.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                                            mainViewModel.scanTransferInbox()
                                        } else {
                                            mainViewModel.loadBooks(if (scanAllMemory) emptyList() else libraryRootUris, scanAllMemory)
                                            Toast.makeText(context, context.getString(R.string.book_received), Toast.LENGTH_LONG).show()
                                            navController.navigate("main") { popUpTo("transfer?bookId={bookId}") { inclusive = true } }
                                        }
                                    }
                                }
                                LaunchedEffect(backStackEntry.id, bookId) {
                                    if (bookId != null) transferViewModel.startServer(bookId)
                                    else {
                                        transferViewModel.prepareReceiverScreen()
                                        if (!hasCameraPermission) {
                                            requestPermissionWithExplanation(cameraPermissionPrompt {
                                                hasCameraPermission = true
                                            })
                                        }
                                    }
                                    transferScreenInitialized = true
                                }
                                
                                if (transferScreenInitialized && state.showConflictDialog) {
                                    val firstUri = libraryRootUris.firstOrNull()
                                    TransferConflictDialog(
                                        bookTitle = state.pendingBookTitle.orEmpty(),
                                        onSyncProgress = { transferViewModel.handleUserDecision("PROGRESS", firstUri) },
                                        onDownloadBook = { transferViewModel.handleUserDecision("FILE", firstUri) },
                                        onDismiss = { transferViewModel.handleUserDecision("CANCEL", firstUri) }
                                    )
                                }

                                TransferScreen(
                                    state = state,
                                    isSending = bookId != null,
                                    isInitialized = transferScreenInitialized,
                                    isManualEntry = isManualEntry,
                                    targetAddress = targetIpText,
                                    hasCameraPermission = hasCameraPermission,
                                    onBack = { navController.popBackStack() },
                                    onFinish = {
                                        transferViewModel.stopServer()
                                        navController.popBackStack()
                                    },
                                    onRetry = { bookId?.let(transferViewModel::startServer) },
                                    onManualEntryChange = { isManualEntry = it },
                                    onTargetAddressChange = { targetIpText = it },
                                    onConnect = { transferViewModel.receiveFromIp(targetIpText, libraryRootUris.firstOrNull()) },
                                    onRequestCameraPermission = {
                                        requestPermissionWithExplanation(cameraPermissionPrompt {
                                            hasCameraPermission = true
                                        })
                                    },
                                    cameraContent = {
                                        if (hasCameraPermission && !state.isDownloading && bookId == null) {
                                                val lifecycleOwner = LocalLifecycleOwner.current
                                                val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
                                                var processCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
                                                val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

                                                DisposableEffect(Unit) {
                                                    onDispose {
                                                        try {
                                                            processCameraProvider?.unbindAll()
                                                        } catch (e: Exception) {
                                                            Log.w("MainActivity", "Failed to unbind camera provider on dispose", e)
                                                        }
                                                        try {
                                                            executor.shutdown()
                                                        } catch (e: Exception) {
                                                            Log.w("MainActivity", "Failed to shutdown camera executor", e)
                                                        }
                                                    }
                                                }

                                                LaunchedEffect(state.isDownloading) {
                                                    if (state.isDownloading) {
                                                        try {
                                                            processCameraProvider?.unbindAll()
                                                        } catch (e: Exception) {
                                                            Log.w("MainActivity", "Failed to unbind camera provider when downloading", e)
                                                        }
                                                    }
                                                }

                                            AndroidView(
                                                factory = { ctx ->
                                                    val previewView = PreviewView(ctx).apply {
                                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                                    }
                                                    cameraProviderFuture.addListener({
                                                        try {
                                                            val provider = cameraProviderFuture.get()
                                                            processCameraProvider = provider
                                                            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                                                            val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                                                            val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                                                val mediaImage = imageProxy.image
                                                                if (mediaImage != null) {
                                                                    scanner.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)).addOnSuccessListener { barcodes -> barcodes.forEach { it.rawValue?.let { transferViewModel.processScannedData(it, libraryRootUris.firstOrNull()) } } }.addOnCompleteListener { imageProxy.close() }
                                                                } else { imageProxy.close() }
                                                            }
                                                            provider.unbindAll()
                                                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                                                        } catch (e: Exception) {
                                                            Log.e("MainActivity", "Error al inicializar cámara QR", e)
                                                        }
                                                    }, ContextCompat.getMainExecutor(ctx))
                                                    previewView
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                )
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
