package com.raulburgosmurray.musicplayer.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.raulburgosmurray.musicplayer.data.MetadataHelper
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.raulburgosmurray.musicplayer.Music
import com.raulburgosmurray.musicplayer.R
import com.raulburgosmurray.musicplayer.encodeBookId
import com.raulburgosmurray.musicplayer.ui.PlaybackUiState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

import androidx.documentfile.provider.DocumentFile


internal enum class AudioFileLocation { MEDIA_STORE, DOCUMENT, FILE }

internal fun classifyAudioFileLocation(path: String): AudioFileLocation {
    if (!path.startsWith("content://")) return AudioFileLocation.FILE
    val authority = path.removePrefix("content://").substringBefore('/')
    return if (authority == MediaStore.AUTHORITY) {
        AudioFileLocation.MEDIA_STORE
    } else {
        AudioFileLocation.DOCUMENT
    }
}

private sealed interface AudioDeleteResult {
    data object Deleted : AudioDeleteResult
    data object Failed : AudioDeleteResult
    data class NeedsConfirmation(
        val request: IntentSenderRequest,
        val retryAfterApproval: Boolean
    ) : AudioDeleteResult
}

private fun deleteAudioFile(context: Context, path: String): AudioDeleteResult {
    return try {
        when (classifyAudioFileLocation(path)) {
            AudioFileLocation.MEDIA_STORE -> deleteMediaStoreAudio(context, Uri.parse(path))
            AudioFileLocation.DOCUMENT -> {
                if (DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete() == true) {
                    AudioDeleteResult.Deleted
                } else {
                    AudioDeleteResult.Failed
                }
            }
            AudioFileLocation.FILE -> {
                val filePath = if (path.startsWith("file://")) Uri.parse(path).path else path
                if (filePath != null && java.io.File(filePath).delete()) {
                    AudioDeleteResult.Deleted
                } else {
                    AudioDeleteResult.Failed
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("MainScreen", "Error deleting file: $path", e)
        AudioDeleteResult.Failed
    }
}

private fun deleteMediaStoreAudio(context: Context, uri: Uri): AudioDeleteResult {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
        return AudioDeleteResult.NeedsConfirmation(
            request = IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
            retryAfterApproval = false
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return deleteMediaStoreAudioOnAndroid10(context, uri)
    }

    return if (context.contentResolver.delete(uri, null, null) > 0) {
        AudioDeleteResult.Deleted
    } else {
        AudioDeleteResult.Failed
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun deleteMediaStoreAudioOnAndroid10(context: Context, uri: Uri): AudioDeleteResult {
    return try {
        if (context.contentResolver.delete(uri, null, null) > 0) {
            AudioDeleteResult.Deleted
        } else {
            AudioDeleteResult.Failed
        }
    } catch (error: RecoverableSecurityException) {
        AudioDeleteResult.NeedsConfirmation(
            request = IntentSenderRequest.Builder(error.userAction.actionIntent.intentSender).build(),
            retryAfterApproval = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    playbackViewModel: PlaybackViewModel,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onBookClick: (Music) -> Unit,
    onMiniPlayerClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onReceiveClick: () -> Unit = {},
    onSelectFolder: () -> Unit = {},
    navController: androidx.navigation.NavController
) {
    val books by mainViewModel.books.collectAsState()
    val favoriteIds by mainViewModel.favoriteIds.collectAsState()
    val bookProgress by mainViewModel.bookProgress.collectAsState()
    val bookReadStatus by mainViewModel.bookReadStatus.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val playbackState by playbackViewModel.uiState.collectAsState()
    val currentSortOrder by mainViewModel.sortOrder.collectAsState()
    val layoutMode by settingsViewModel.layoutMode.collectAsState()
    val libraryRootUris by settingsViewModel.libraryRootUris.collectAsState()
    val scanAllMemory by settingsViewModel.scanAllMemory.collectAsState()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    
    val context = LocalContext.current
    val currentFilter by mainViewModel.bookFilter.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingDeletion by remember { mutableStateOf<Music?>(null) }
    var retryDeletionAfterApproval by remember { mutableStateOf(false) }

    fun refreshLibraryAfterDeletion(book: Music) {
        playbackViewModel.cleanupAfterDeletion(book.id)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.delete_success),
                duration = SnackbarDuration.Short
            )
        }
        val uris = settingsViewModel.libraryRootUris.value
        val scanAll = settingsViewModel.scanAllMemory.value
        mainViewModel.loadBooks(if (scanAll) emptyList() else uris, scanAll)
    }

    val deleteConfirmationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val book = pendingDeletion
        pendingDeletion = null
        if (result.resultCode == Activity.RESULT_OK && book != null) {
            scope.launch {
                val deleted = if (retryDeletionAfterApproval) {
                    withContext(Dispatchers.IO) {
                        deleteAudioFile(context, book.path) == AudioDeleteResult.Deleted
                    }
                } else {
                    true
                }
                retryDeletionAfterApproval = false
                if (deleted) {
                    refreshLibraryAfterDeletion(book)
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.delete_error))
                }
            }
        } else {
            retryDeletionAfterApproval = false
        }
    }

    var selectedBookForDetails by remember { mutableStateOf<Music?>(null) }
    val detailsSheetState = rememberModalBottomSheetState()
    var showDetailsSheet by remember { mutableStateOf(false) }
    
    var isChangingLayout by remember { mutableStateOf(false) }
    var displayedBooks by remember { mutableStateOf<List<Music>>(emptyList()) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // Scroll to top whenever the book list becomes non-empty (e.g. on first load or after scan)
    LaunchedEffect(displayedBooks.isNotEmpty()) {
        if (displayedBooks.isNotEmpty() && searchQuery.isEmpty()) {
            listState.scrollToItem(0)
            gridState.scrollToItem(0)
        }
    }

    // Debounce search: wait for the user to stop typing before filtering.
    // Books list and layout changes apply immediately; only searchQuery is debounced.
    LaunchedEffect(books, layoutMode) {
        isChangingLayout = true
        displayedBooks = if (searchQuery.isEmpty()) books
        else books.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
        isChangingLayout = false
    }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty()) {
            displayedBooks = books
        } else {
            delay(300)
            displayedBooks = books.filter {
                it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    LaunchedEffect(showSearchBar) {
        if (!showSearchBar) {
            searchQuery = ""
        }
    }

    val favoriteIdsSet = remember(favoriteIds) { favoriteIds.toSet() }
    val readStatusSet = remember(bookReadStatus) { bookReadStatus.filter { it.value }.keys }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) { 
                        Icon(
                            Icons.Default.Search, 
                            contentDescription = stringResource(R.string.search_placeholder), 
                            tint = if (showSearchBar || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        ) 
                    }
                    Box {
                        val currentFilter = mainViewModel.bookFilter.collectAsState().value
                        val hasActiveFilter = currentFilter != com.raulburgosmurray.musicplayer.ui.BookFilter.ALL
                        IconButton(onClick = { showSortMenu = true }) { 
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_title),
                                tint = if (hasActiveFilter) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            ) 
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            Text(
                                "Ordenar por",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_title)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                                trailingIcon = { if (currentSortOrder == SortOrder.TITLE) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { settingsViewModel.setSortOrder(SortOrder.TITLE); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_artist)) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                trailingIcon = { if (currentSortOrder == SortOrder.ARTIST) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { settingsViewModel.setSortOrder(SortOrder.ARTIST); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_progress)) },
                                leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                                trailingIcon = { if (currentSortOrder == SortOrder.PROGRESS) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { settingsViewModel.setSortOrder(SortOrder.PROGRESS); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_recent)) },
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                trailingIcon = { if (currentSortOrder == SortOrder.RECENT) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { settingsViewModel.setSortOrder(SortOrder.RECENT); showSortMenu = false }
                            )
                        }
                    }
                    IconButton(onClick = { settingsViewModel.setLayoutMode(if (layoutMode == LayoutMode.LIST) LayoutMode.GRID else LayoutMode.LIST) }) { Icon(if (layoutMode == LayoutMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList, contentDescription = stringResource(R.string.layout_toggle)) }
                    Box {
                        var showMainMenuMore by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMainMenuMore = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                        DropdownMenu(expanded = showMainMenuMore, onDismissRequest = { showMainMenuMore = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.favourites_btn)) },
                                leadingIcon = { Icon(Icons.Default.Favorite, null, tint = if (favoriteIds.isNotEmpty()) androidx.compose.ui.graphics.Color.Red else LocalContentColor.current) },
                                onClick = { showMainMenuMore = false; onFavoritesClick() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.open)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = {
                                    showMainMenuMore = false
                                    val uris = settingsViewModel.libraryRootUris.value
                                    val scanAll = settingsViewModel.scanAllMemory.value
                                    mainViewModel.loadBooks(if (scanAll) emptyList() else uris, scanAll)
                                }
                            )
                            if (com.raulburgosmurray.musicplayer.FeatureFlags.P2P_TRANSFER) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.receive)) },
                                    leadingIcon = { Icon(Icons.Default.Wifi, null) },
                                    onClick = { showMainMenuMore = false; onReceiveClick() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { showMainMenuMore = false; onSettingsClick() }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = { if (playbackState.currentMediaItem != null) { with(sharedTransitionScope) { MiniPlayer(state = playbackState, animatedVisibilityScope = animatedVisibilityScope, onTogglePlay = { playbackViewModel.togglePlayPause() }, onClick = onMiniPlayerClick) } } }
) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            androidx.compose.animation.AnimatedVisibility(visible = showSearchBar) {
                SearchBar(inputField = { SearchBarDefaults.InputField(query = searchQuery, onQueryChange = { searchQuery = it }, onSearch = { }, expanded = false, onExpandedChange = { }, placeholder = { Text(stringResource(R.string.search_placeholder)) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }) }, expanded = false, onExpandedChange = { }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {}
            }

            val allUnfilteredBooks by mainViewModel.allBooksUnfiltered.collectAsState()
            if (allUnfilteredBooks.isNotEmpty()) {
                LibraryFilterChipsRow(
                    allBooks = allUnfilteredBooks,
                    progressMap = bookProgress,
                    readStatusMap = bookReadStatus,
                    currentFilter = currentFilter,
                    onFilterSelected = { mainViewModel.setFilter(it) }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && books.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        val (current, total) = scanProgress
                        if (total > 0) {
                            Text(stringResource(R.string.scanning_progress, current, total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { current.toFloat() / total.toFloat() }, modifier = Modifier.width(200.dp))
                        } else {
                            Text(stringResource(R.string.loading_library), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                } else if (books.isEmpty()) {
                    if (allUnfilteredBooks.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val emptyIcon = when (currentFilter) {
                                com.raulburgosmurray.musicplayer.ui.BookFilter.LOCAL_ONLY -> Icons.Default.CloudDownload
                                com.raulburgosmurray.musicplayer.ui.BookFilter.SERVER_ONLY -> Icons.Default.CloudOff
                                com.raulburgosmurray.musicplayer.ui.BookFilter.COMPLETED -> Icons.Default.CheckCircleOutline
                                else -> Icons.Default.FolderOpen
                            }
                            val emptyTitle = when (currentFilter) {
                                com.raulburgosmurray.musicplayer.ui.BookFilter.LOCAL_ONLY -> "Sin audiolibros descargados"
                                com.raulburgosmurray.musicplayer.ui.BookFilter.SERVER_ONLY -> "Sin audiolibros en el servidor"
                                com.raulburgosmurray.musicplayer.ui.BookFilter.COMPLETED -> "Sin audiolibros terminados"
                                else -> "Sin resultados"
                            }
                            val emptyMsg = when (currentFilter) {
                                com.raulburgosmurray.musicplayer.ui.BookFilter.LOCAL_ONLY -> "No tienes audiolibros en este dispositivo. Explora la pestaña 'Audiobookshelf' para transmitir o descargar."
                                com.raulburgosmurray.musicplayer.ui.BookFilter.SERVER_ONLY -> "No hay audiolibros remotos en Audiobookshelf. Conecta tu servidor en Ajustes."
                                else -> "No hay audiolibros que coincidan con este filtro."
                            }

                            Icon(emptyIcon, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(20.dp))
                            Text(emptyTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(10.dp))
                            Text(emptyMsg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))

                            if (currentFilter == com.raulburgosmurray.musicplayer.ui.BookFilter.LOCAL_ONLY) {
                                Button(onClick = { mainViewModel.setFilter(com.raulburgosmurray.musicplayer.ui.BookFilter.SERVER_ONLY) }) {
                                    Icon(Icons.Default.Cloud, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ver catálogo Audiobookshelf")
                                }
                            } else {
                                Button(onClick = { mainViewModel.setFilter(com.raulburgosmurray.musicplayer.ui.BookFilter.ALL) }) {
                                    Text("Ver todos los libros")
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.no_audiobooks),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.no_audiobooks_in_folder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(32.dp))
                            if (com.raulburgosmurray.musicplayer.FeatureFlags.SCAN_ALL_MEMORY) {
                                Button(
                                    onClick = { mainViewModel.loadBooks(emptyList(), true) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.scan_memory_btn))
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            OutlinedButton(
                                onClick = onSelectFolder,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.select_folder))
                            }
                        }
                    }
                } else {
                    val onQueueClick: (Music) -> Unit = { book ->
                        playbackViewModel.addToQueue(book)
                        scope.launch { snackbarHostState.showSnackbar(message = context.getString(R.string.added_to_queue, capitalizeWords(book.title)), duration = SnackbarDuration.Short) }
                    }
                    val onBookLongClick: (Music) -> Unit = { book ->
                        selectedBookForDetails = book
                        showDetailsSheet = true
                    }
                    
                    if (layoutMode == LayoutMode.LIST) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(
                                items = displayedBooks,
                                key = { it.id },
                                contentType = { "book" }
                            ) { book -> with(sharedTransitionScope) { BookListItem(book = book, isFavorite = favoriteIdsSet.contains(book.id), isRead = readStatusSet.contains(book.id), progress = bookProgress[book.id] ?: 0f, isPlaying = playbackState.isPlaying && playbackState.currentMediaItem?.mediaId == book.id, animatedVisibilityScope = animatedVisibilityScope, onAddToQueue = { onQueueClick(book) }, onLongClick = { onBookLongClick(book) }, onClick = { onBookClick(book) }) } }
                        }
                    } else {
                        // AJUSTE DINÁMICO DE COLUMNAS: Solo 4 si es tableta Y horizontal. En móvil horizontal 3.
                        val columns = when {
                            isTablet && isLandscape -> 4
                            isTablet -> 3
                            isLandscape -> 3
                            else -> 2
                        }
                        LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(
                                items = displayedBooks,
                                key = { it.id },
                                contentType = { "book" }
                            ) { book -> with(sharedTransitionScope) { BookGridItem(book = book, keyPrefix = "list", isFavorite = favoriteIdsSet.contains(book.id), isRead = readStatusSet.contains(book.id), progress = bookProgress[book.id] ?: 0f, isPlaying = playbackState.isPlaying && playbackState.currentMediaItem?.mediaId == book.id, animatedVisibilityScope = animatedVisibilityScope, onAddToQueue = { onQueueClick(book) }, onLongClick = { onBookLongClick(book) }, onClick = { onBookClick(book) }) } }
                        }
                    }
                }
                // Scrollbar indicator (visible only when there are enough items to scroll)
                if (displayedBooks.size > 6 && !isChangingLayout) {
                    val firstVisible = if (layoutMode == LayoutMode.LIST) listState.firstVisibleItemIndex else gridState.firstVisibleItemIndex
                    val visibleCount = if (layoutMode == LayoutMode.LIST) listState.layoutInfo.visibleItemsInfo.size else gridState.layoutInfo.visibleItemsInfo.size
                    val total = displayedBooks.size
                    val progress = if (firstVisible + visibleCount >= total) {
                        1f
                    } else {
                        (firstVisible.toFloat() / (total - visibleCount).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                    }
                    BoxWithConstraints(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 2.dp, top = 8.dp, bottom = 8.dp)
                            .width(3.dp)
                            .fillMaxHeight()
                    ) {
                        val trackHeight = this.maxHeight
                        val thumbHeight = trackHeight * 0.08f
                        val thumbOffset = (trackHeight - thumbHeight) * progress
                        // Track
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(1.5.dp)))
                        // Thumb
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(thumbHeight)
                                .offset(y = thumbOffset)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), RoundedCornerShape(1.5.dp))
                        )
                    }
                }
                if (isChangingLayout) { 
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                if (isLoading && books.isNotEmpty()) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
    if (showDetailsSheet && selectedBookForDetails != null) { 
        val currentBook = selectedBookForDetails!!
        val isBookRead = readStatusSet.contains(currentBook.id)
        ModalBottomSheet(onDismissRequest = { showDetailsSheet = false }, sheetState = detailsSheetState) { 
            BookDetailsContent(
                book = currentBook,
                allBooks = books,
                onEditMetadata = { bookId -> navController.navigate("metadata_editor?bookId=${encodeBookId(bookId)}") },
                isRead = isBookRead,
                onToggleRead = { mainViewModel.toggleReadStatus(currentBook.id) },
                onDelete = {
                    val bookToDelete = selectedBookForDetails!!
                    scope.launch {
                        val deleteResult = withContext(Dispatchers.IO) {
                            deleteAudioFile(context, bookToDelete.path)
                        }
                        showDetailsSheet = false
                        when (deleteResult) {
                            AudioDeleteResult.Deleted -> refreshLibraryAfterDeletion(bookToDelete)
                            AudioDeleteResult.Failed -> snackbarHostState.showSnackbar(
                                message = context.getString(R.string.delete_error),
                                duration = SnackbarDuration.Short
                            )
                            is AudioDeleteResult.NeedsConfirmation -> {
                                pendingDeletion = bookToDelete
                                retryDeletionAfterApproval = deleteResult.retryAfterApproval
                                deleteConfirmationLauncher.launch(deleteResult.request)
                            }
                        }
                    }
                },
                playbackViewModel = playbackViewModel
            ) 
        } 
    }
}

@Composable
fun BookDetailsContent(
    book: Music,
    allBooks: List<Music> = emptyList(),
    onEditMetadata: (String) -> Unit = {},
    isRead: Boolean = false,
    onToggleRead: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    playbackViewModel: PlaybackViewModel? = null
) {
    val siblingCount = allBooks.count { it.album == book.album && it.id != book.id }
    val context = LocalContext.current
    val metadata = remember { com.raulburgosmurray.musicplayer.data.MetadataJsonHelper.loadMetadata(context, book.id) }
    val rawTitle = metadata?.title?.takeIf { it.isNotBlank() } ?: book.title
    val displayTitle = capitalizeWords(rawTitle)
    val displayArtist = capitalizeWords(book.artist)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInUseDialog by remember { mutableStateOf(false) }

    val synopsisState = produceState<String?>(
        initialValue = metadata?.comment?.takeIf { it.isNotBlank() } ?: book.description?.takeIf { it.isNotBlank() },
        key1 = book.id
    ) {
        if (value.isNullOrBlank()) {
            if (book.id.startsWith("abs_")) {
                val absRepo = com.raulburgosmurray.musicplayer.data.AudiobookshelfRepository(context)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    absRepo.resolveStreamUrl(book.id)
                }
                val updatedBook = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.raulburgosmurray.musicplayer.data.AppDatabase.getDatabase(context).cachedBookDao().getBookById(book.id)
                }
                if (updatedBook != null && !updatedBook.description.isNullOrBlank()) {
                    value = updatedBook.description
                }
            } else if (com.raulburgosmurray.musicplayer.data.DescriptionExtractor.isSupported() && book.path.isNotBlank()) {
                val extracted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.raulburgosmurray.musicplayer.data.DescriptionExtractor.extract(context, book.path)
                }
                if (!extracted.isNullOrBlank()) {
                    value = extracted
                }
            }
        }
    }
    val synopsis = synopsisState.value

    val isBookInUse = playbackViewModel?.uiState?.collectAsState()?.value?.currentMediaItem?.mediaId == book.id

    if (showInUseDialog) {
        BookInUseDeleteDialog(
            bookTitle = displayTitle,
            onCloseAndDelete = {
                showInUseDialog = false
                playbackViewModel?.clearCurrentPlayback()
                onDelete?.invoke()
            },
            onDeleteAnyway = {
                showInUseDialog = false
                onDelete?.invoke()
            },
            onDismiss = { showInUseDialog = false }
        )
    }

    if (showDeleteDialog) {
        BookDeleteConfirmationDialog(
            bookTitle = displayTitle,
            onConfirm = {
                showDeleteDialog = false
                onDelete?.invoke()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(width = 104.dp, height = 136.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = 3.dp
            ) {
                if (MetadataHelper.isArtUriValid(context, book.artUri)) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(book.artUri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    BookPlaceholder(title = displayTitle, modifier = Modifier.fillMaxSize())
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (siblingCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.files_in_collection, siblingCount),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = { openFolder(context, book.path) },
                modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.view_in_folder),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = { onEditMetadata(book.id) },
                modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.edit_metadata),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val isRemoteBook = book.path.startsWith("http://") || book.path.startsWith("https://")
        if (isRemoteBook) {
            Button(
                onClick = {
                    val downloadId = com.raulburgosmurray.musicplayer.data.BookDownloader.downloadBook(context, book)
                    if (downloadId != null) {
                        android.widget.Toast.makeText(context, "Descargando '${displayTitle}' en segundo plano...", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "No se pudo iniciar la descarga", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Descargar para escuchar offline")
            }
        } else if (book.id.startsWith("abs_")) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = "Descargado • Disponible sin conexión",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        if (onToggleRead != null) {
            OutlinedButton(
                onClick = onToggleRead,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isRead) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if (isRead) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRead) stringResource(R.string.mark_as_unread) else stringResource(R.string.mark_as_read))
            }
        }

        if (!synopsis.isNullOrBlank()) {
            SynopsisAccordion(description = synopsis, initiallyExpanded = true)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.book_details),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DetailRow(
                        icon = if (isRemoteBook) Icons.Default.Cloud else Icons.Default.SdCard,
                        label = "Origen del audio",
                        value = if (isRemoteBook) "Servidor Audiobookshelf (Online)" else "Almacenamiento Local (Offline)"
                    )
                    DetailRow(icon = Icons.Default.Description, label = stringResource(R.string.detail_file_name), value = book.fileName)
                    DetailRow(icon = Icons.Default.Folder, label = stringResource(R.string.detail_location), value = book.path)
                    DetailRow(icon = Icons.Default.Timer, label = stringResource(R.string.detail_duration), value = formatDuration(book.duration))
                    DetailRow(icon = Icons.Default.SdCard, label = stringResource(R.string.detail_size), value = formatFileSize(book.fileSize))
                    DetailRow(icon = Icons.Default.AudioFile, label = stringResource(R.string.detail_format), value = book.path.substringAfterLast(".").uppercase())
                    if (metadata != null) {
                        metadata.album?.let { DetailRow(icon = Icons.Default.Album, label = stringResource(R.string.detail_series), value = it) }
                        metadata.year?.let { DetailRow(icon = Icons.Default.CalendarToday, label = stringResource(R.string.detail_year), value = it) }
                        metadata.genre?.let { DetailRow(icon = Icons.Default.Category, label = stringResource(R.string.detail_genre), value = it) }
                        metadata.trackNumber?.let { DetailRow(icon = Icons.Default.Numbers, label = stringResource(R.string.detail_volume), value = it.toString()) }
                    }
                }
            }
        }

        if (onDelete != null) {
            OutlinedButton(
                onClick = {
                    if (isBookInUse) showInUseDialog = true else showDeleteDialog = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_book))
            }
        }
    }
}

@Composable
private fun BookDeleteConfirmationDialog(
    bookTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp).size(32.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.delete_confirmation_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.delete_confirmation_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_book))
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun BookInUseDeleteDialog(
    bookTitle: String,
    onCloseAndDelete: () -> Unit,
    onDeleteAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp).size(32.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.delete_book_in_use_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.delete_book_in_use_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onCloseAndDelete,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.close_and_delete))
                }

                OutlinedButton(
                    onClick = onDeleteAnyway,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_anyway))
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
fun LibraryFilterChipsRow(
    allBooks: List<Music>,
    progressMap: Map<String, Float>,
    readStatusMap: Map<String, Boolean>,
    currentFilter: BookFilter,
    onFilterSelected: (BookFilter) -> Unit
) {
    val totalCount = allBooks.size
    val localCount = remember(allBooks) { allBooks.count { !it.path.startsWith("http://") && !it.path.startsWith("https://") } }
    val serverCount = remember(allBooks) { allBooks.count { it.path.startsWith("http://") || it.path.startsWith("https://") || it.id.startsWith("abs_") } }
    val inProgressCount = remember(allBooks, progressMap, readStatusMap) { allBooks.count { (progressMap[it.id] ?: 0f) > 0f && readStatusMap[it.id] != true } }
    val completedCount = remember(allBooks, readStatusMap) { allBooks.count { readStatusMap[it.id] == true } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = currentFilter == BookFilter.ALL,
            onClick = { onFilterSelected(BookFilter.ALL) },
            label = { Text("Todos ($totalCount)") },
            leadingIcon = { Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        FilterChip(
            selected = currentFilter == BookFilter.LOCAL_ONLY,
            onClick = { onFilterSelected(BookFilter.LOCAL_ONLY) },
            label = { Text("En este dispositivo ($localCount)") },
            leadingIcon = { Icon(Icons.Default.SdCard, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        if (serverCount > 0) {
            FilterChip(
                selected = currentFilter == BookFilter.SERVER_ONLY,
                onClick = { onFilterSelected(BookFilter.SERVER_ONLY) },
                label = { Text("Audiobookshelf ($serverCount)") },
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        FilterChip(
            selected = currentFilter == BookFilter.IN_PROGRESS,
            onClick = { onFilterSelected(BookFilter.IN_PROGRESS) },
            label = { Text("En progreso ($inProgressCount)") },
            leadingIcon = { Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        FilterChip(
            selected = currentFilter == BookFilter.COMPLETED,
            onClick = { onFilterSelected(BookFilter.COMPLETED) },
            label = { Text("Terminados ($completedCount)") },
            leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                softWrap = true,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun openFolder(context: android.content.Context, path: String) {
    try {
        val intent = if (path.startsWith("content://")) {
            android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(android.net.Uri.parse(path), "vnd.android.document/directory"); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        } else {
            val file = java.io.File(path)
            android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply { setDataAndType(android.net.Uri.parse(file.parent ?: ""), "*/*"); addCategory(android.content.Intent.CATEGORY_OPENABLE) }
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.select_explorer)))
    } catch (e: Exception) { android.widget.Toast.makeText(context, context.getString(R.string.no_explorer_found), android.widget.Toast.LENGTH_SHORT).show() }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun androidx.compose.animation.SharedTransitionScope.BookGridItem(book: Music, isFavorite: Boolean, isRead: Boolean, progress: Float, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, keyPrefix: String = "grid", isPlaying: Boolean = false, onAddToQueue: () -> Unit, onLongClick: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val displayTitle = remember(book.title) { capitalizeWords(book.title) }
    val displayArtist = remember(book.artist) { capitalizeWords(book.artist) }
    val artRequest = remember(book.artUri) { ImageRequest.Builder(context).data(book.artUri).placeholder(R.drawable.ic_audiobook_cover).error(R.drawable.ic_audiobook_cover).build() }
    val coverAlpha = if (isRead) 0.7f else 1f
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(BookCoverShape)
            .then(if (isPlaying) Modifier.border(2.dp, primaryColor, BookCoverShape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Cover image or placeholder — fills entire card
        Box(modifier = Modifier.fillMaxSize().sharedBookCover(this@BookGridItem, "${keyPrefix}_cover_${book.id}", animatedVisibilityScope)) {
            if (!book.artUri.isNullOrBlank()) {
                AsyncImage(model = artRequest, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer(alpha = coverAlpha))
            } else {
                BookPlaceholder(title = displayTitle, modifier = Modifier.fillMaxSize().graphicsLayer(alpha = coverAlpha))
            }
        }

        // Bottom text overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                .padding(8.dp)
        ) {
            if (isPlaying) Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            Text(text = displayTitle, style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = displayArtist, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val currentPos = remember(progress, book.duration) { (progress * book.duration).toLong() }
            Text(text = if (progress > 0f) "${formatDuration(currentPos)} / ${formatDuration(book.duration)}" else formatDuration(book.duration), style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // Queue button
        IconButton(onClick = onAddToQueue, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
        }
        val isRemoteBook = book.path.startsWith("http://") || book.path.startsWith("https://")
        if (isRemoteBook) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(20.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                    .padding(3.dp)
            )
        }
        if (isFavorite) Icon(Icons.Default.Favorite, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.align(Alignment.TopStart).padding(if (isRemoteBook) 32.dp else 8.dp).size(16.dp))
        if (isRead) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = primaryColor, modifier = Modifier.align(Alignment.TopStart).padding(if (isRemoteBook && isFavorite) 52.dp else if (isRemoteBook || isFavorite) 32.dp else 8.dp).size(16.dp))
        if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter), color = primaryColor, trackColor = androidx.compose.ui.graphics.Color.Transparent)
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun androidx.compose.animation.SharedTransitionScope.MiniPlayer(state: PlaybackUiState, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, onTogglePlay: () -> Unit, onClick: () -> Unit) {
    val currentItem = state.currentMediaItem ?: return
    val context = LocalContext.current
    val unknownArtistText = stringResource(R.string.unknown_artist)
    val metadata = remember(currentItem.mediaId) { currentItem.mediaId?.let { com.raulburgosmurray.musicplayer.data.MetadataJsonHelper.loadMetadata(context, it) } }
    val rawTitle = metadata?.title?.takeIf { it.isNotBlank() } ?: currentItem.mediaMetadata.title?.toString() ?: stringResource(R.string.unknown_title)
    val displayTitle = remember(rawTitle) { capitalizeWords(rawTitle) }
    val rawArtist = currentItem.mediaMetadata.artist?.toString() ?: unknownArtistText
    val displayArtist = remember(rawArtist) { capitalizeWords(rawArtist) }
    val displayLetter = remember(displayTitle) { displayTitle.firstOrNull()?.uppercase() ?: "A" }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .sharedBookCover(this@MiniPlayer, "mini_cover_${currentItem.mediaId}", animatedVisibilityScope)
            ) {
                val artworkUri = currentItem.mediaMetadata.artworkUri?.toString()
                if (!artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artworkUri)
                            .crossfade(true)
                            .placeholder(R.drawable.ic_audiobook_cover)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CompactBookPlaceholder(title = displayLetter, modifier = Modifier.fillMaxSize())
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }

            FilledTonalIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = if (state.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.pause_play_btn),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
        }

        val progress = if (state.duration > 0) state.currentPosition.toFloat() / state.duration.toFloat() else 0f
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomStart
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun androidx.compose.animation.SharedTransitionScope.BookListItem(book: Music, isFavorite: Boolean, isRead: Boolean, progress: Float, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, keyPrefix: String = "list", isPlaying: Boolean = false, onAddToQueue: () -> Unit, onLongClick: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val displayTitle = remember(book.title) { capitalizeWords(book.title) }
    val displayArtist = remember(book.artist) { capitalizeWords(book.artist) }
    val artRequest = remember(book.artUri) { ImageRequest.Builder(context).data(book.artUri).crossfade(true).placeholder(R.drawable.ic_audiobook_cover).error(R.drawable.ic_audiobook_cover).build() }
    val bgColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .then(if (isPlaying) Modifier.border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(60.dp).sharedBookCover(this@BookListItem, "${keyPrefix}_cover_${book.id}", animatedVisibilityScope)
            ) {
                if (!book.artUri.isNullOrBlank()) AsyncImage(model = artRequest, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else CompactBookPlaceholder(title = displayTitle, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (isPlaying) Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.labelSmall, color = primaryColor, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, false), maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (isRead) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                    val isRemoteBook = book.path.startsWith("http://") || book.path.startsWith("https://")
                    if (isRemoteBook) { Spacer(modifier = Modifier.width(4.dp)); Icon(Icons.Default.Cloud, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp)) }
                    if (isFavorite) { Spacer(modifier = Modifier.width(4.dp)); Icon(Icons.Default.Favorite, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.size(16.dp)) }
                    if (isRead) { Spacer(modifier = Modifier.width(4.dp)); Icon(Icons.Default.CheckCircle, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp)) }
                }
                Text(text = displayArtist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                val currentPos = remember(progress, book.duration) { (progress * book.duration).toLong() }
                Text(text = if (progress > 0f) "${formatDuration(currentPos)} / ${formatDuration(book.duration)}" else formatDuration(book.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onAddToQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.playlist_btn), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = primaryColor, modifier = Modifier.size(32.dp))
        }
        if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = primaryColor, trackColor = androidx.compose.ui.graphics.Color.Transparent)
    }
}
