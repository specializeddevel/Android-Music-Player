package com.raulburgosmurray.musicplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

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
    navController: androidx.navigation.NavController
) {
val books by mainViewModel.books.collectAsState()
    val favoriteIds by mainViewModel.favoriteIds.collectAsState()
    val bookProgress by mainViewModel.bookProgress.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val playbackState by playbackViewModel.uiState.collectAsState()
    val currentSortOrder by mainViewModel.sortOrder.collectAsState()
    val layoutMode by settingsViewModel.layoutMode.collectAsState()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedBookForDetails by remember { mutableStateOf<Music?>(null) }
    val detailsSheetState = rememberModalBottomSheetState()
    var showDetailsSheet by remember { mutableStateOf(false) }
    
    val filteredBooks = remember(books, searchQuery) { 
        if (searchQuery.isEmpty()) books 
        else books.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) } 
    }

    val favoriteIdsSet = remember(favoriteIds) { favoriteIds.toSet() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = {
                    if (com.raulburgosmurray.musicplayer.FeatureFlags.P2P_TRANSFER) {
                        IconButton(onClick = onReceiveClick) { Icon(Icons.Default.Wifi, contentDescription = stringResource(R.string.receive)) }
                    }
                    IconButton(onClick = { mainViewModel.loadBooks(settingsViewModel.libraryRootUri.value) }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.open)) }
                    IconButton(onClick = { settingsViewModel.setLayoutMode(if (layoutMode == LayoutMode.LIST) LayoutMode.GRID else LayoutMode.LIST) }) { Icon(if (layoutMode == LayoutMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList, contentDescription = "Vista") }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort_title)) }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.sort_title)) }, leadingIcon = { if (currentSortOrder == SortOrder.TITLE) Icon(Icons.Default.Check, null) }, onClick = { settingsViewModel.setSortOrder(SortOrder.TITLE); showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.sort_artist)) }, leadingIcon = { if (currentSortOrder == SortOrder.ARTIST) Icon(Icons.Default.Check, null) }, onClick = { settingsViewModel.setSortOrder(SortOrder.ARTIST); showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.sort_progress)) }, leadingIcon = { if (currentSortOrder == SortOrder.PROGRESS) Icon(Icons.Default.Check, null) }, onClick = { settingsViewModel.setSortOrder(SortOrder.PROGRESS); showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.sort_recent)) }, leadingIcon = { if (currentSortOrder == SortOrder.RECENT) Icon(Icons.Default.Check, null) }, onClick = { settingsViewModel.setSortOrder(SortOrder.RECENT); showSortMenu = false })
                        }
                    }
                    IconButton(onClick = onFavoritesClick) { Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.favourites_btn), tint = if (favoriteIds.isNotEmpty()) androidx.compose.ui.graphics.Color.Red else LocalContentColor.current) }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) }
                }
            )
        },
        bottomBar = { if (playbackState.currentMediaItem != null) { with(sharedTransitionScope) { MiniPlayer(state = playbackState, animatedVisibilityScope = animatedVisibilityScope, onTogglePlay = { playbackViewModel.togglePlayPause() }, onClick = onMiniPlayerClick) } } }
) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchBar(inputField = { SearchBarDefaults.InputField(query = searchQuery, onQueryChange = { searchQuery = it }, onSearch = { }, expanded = false, onExpandedChange = { }, placeholder = { Text(stringResource(R.string.search_placeholder)) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }) }, expanded = false, onExpandedChange = { }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {}
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading && books.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        val (current, total) = scanProgress
                        if (total > 0) {
                            Text("Escaneando: $current / $total archivos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { current.toFloat() / total.toFloat() }, modifier = Modifier.width(200.dp))
                        } else {
                            Text("Cargando biblioteca...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                } else if (books.isEmpty()) {
                    // Empty state when no books are found
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
                            text = "No hay audiolibros",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Agrega archivos de audio a tu carpeta seleccionada para comenzar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { mainViewModel.loadBooks(settingsViewModel.libraryRootUri.value) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Escanear carpeta")
                        }
                    }
                } else {
                    if (layoutMode == LayoutMode.LIST) {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(
                                items = filteredBooks,
                                key = { it.id }
                            ) { book -> with(sharedTransitionScope) { BookListItem(book = book, isFavorite = favoriteIdsSet.contains(book.id), progress = bookProgress[book.id] ?: 0f, animatedVisibilityScope = animatedVisibilityScope, onAddToQueue = { playbackViewModel.addToQueue(book); scope.launch { snackbarHostState.showSnackbar(message = context.getString(R.string.added_to_queue, book.title), duration = SnackbarDuration.Short) } }, onLongClick = { selectedBookForDetails = book; showDetailsSheet = true }, onClick = { onBookClick(book) }) } }
                        }
                    } else {
                        // AJUSTE DINÁMICO DE COLUMNAS: Solo 4 si es tableta Y horizontal. En móvil horizontal 3.
                        val columns = when {
                            isTablet && isLandscape -> 4
                            isTablet -> 3
                            isLandscape -> 3
                            else -> 2
                        }
                        LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(
                                items = filteredBooks,
                                key = { it.id }
                            ) { book -> with(sharedTransitionScope) { BookGridItem(book = book, isFavorite = favoriteIdsSet.contains(book.id), progress = bookProgress[book.id] ?: 0f, animatedVisibilityScope = animatedVisibilityScope, onAddToQueue = { playbackViewModel.addToQueue(book); scope.launch { snackbarHostState.showSnackbar(message = context.getString(R.string.added_to_queue, book.title), duration = SnackbarDuration.Short) } }, onLongClick = { selectedBookForDetails = book; showDetailsSheet = true }, onClick = { onBookClick(book) }) } }
                        }
                    }
                }
                if (isLoading && books.isNotEmpty()) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
    if (showDetailsSheet && selectedBookForDetails != null) { ModalBottomSheet(onDismissRequest = { showDetailsSheet = false }, sheetState = detailsSheetState) { BookDetailsContent(book = selectedBookForDetails!!, allBooks = books, onEditMetadata = { bookId -> navController.navigate("metadata_editor?bookId=${encodeBookId(bookId)}") }) } }
}

@Composable
fun BookDetailsContent(book: Music, allBooks: List<Music> = emptyList(), onEditMetadata: (String) -> Unit = {}) {
    val siblingCount = allBooks.count { it.album == book.album && it.id != book.id }
    val context = LocalContext.current
    val metadata = remember { com.raulburgosmurray.musicplayer.data.MetadataJsonHelper.loadMetadata(context, book.id) }
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(120.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            if (book.artUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(book.artUri).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop)
            else BookPlaceholder(title = book.title, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(16.dp))
        Text(text = book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(text = book.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        if (siblingCount > 0) { Surface(modifier = Modifier.padding(top = 8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)) { Text(text = "+ $siblingCount archivos en esta colección", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { openFolder(context, book.path) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer), shape = RoundedCornerShape(16.dp)) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.view_in_folder)) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onEditMetadata(book.id) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Editar metadatos") }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow(icon = Icons.Default.Description, label = "Nombre del archivo", value = book.fileName)
                DetailRow(icon = Icons.Default.Folder, label = "Ubicación", value = book.path)
                DetailRow(icon = Icons.Default.SdCard, label = "Tamaño", value = formatFileSize(book.fileSize))
                DetailRow(icon = Icons.Default.Timer, label = "Duración", value = formatDuration(book.duration))
                DetailRow(icon = Icons.Default.AudioFile, label = "Formato", value = book.path.substringAfterLast(".").uppercase())
                if (metadata != null) {
                    if (metadata.album != null) DetailRow(icon = Icons.Default.Album, label = "Serie", value = metadata.album)
                    if (metadata.year != null) DetailRow(icon = Icons.Default.CalendarToday, label = "Año", value = metadata.year)
                    if (metadata.genre != null) DetailRow(icon = Icons.Default.Category, label = "Género", value = metadata.genre)
                    if (metadata.trackNumber != null) DetailRow(icon = Icons.Default.Numbers, label = "Nº Volumen", value = metadata.trackNumber.toString())
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column { Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(text = value, style = MaterialTheme.typography.bodyMedium, softWrap = true) }
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
        context.startActivity(android.content.Intent.createChooser(intent, "Selecciona un explorador"))
    } catch (e: Exception) { android.widget.Toast.makeText(context, "No se encontró un explorador compatible", android.widget.Toast.LENGTH_SHORT).show() }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun androidx.compose.animation.SharedTransitionScope.BookGridItem(book: Music, isFavorite: Boolean, progress: Float, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, keyPrefix: String = "grid", onAddToQueue: () -> Unit, onLongClick: () -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().height(240.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize().sharedElement(rememberSharedContentState(key = "${keyPrefix}_cover_${book.id}"), animatedVisibilityScope = animatedVisibilityScope), color = MaterialTheme.colorScheme.surface) {
                if (book.artUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(book.artUri).crossfade(true).placeholder(R.drawable.ic_audiobook_cover).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else BookPlaceholder(title = book.title, modifier = Modifier.fillMaxSize())
            }
            Surface(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter), color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = book.title, style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = book.artist, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val currentPos = (progress * book.duration).toLong()
                    Text(
                        text = if (progress > 0f) "${formatDuration(currentPos)} / ${formatDuration(book.duration)}" else formatDuration(book.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onAddToQueue, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp)) }
            if (isFavorite) Icon(Icons.Default.Favorite, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(16.dp))
            if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.primary, trackColor = androidx.compose.ui.graphics.Color.Transparent)
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun androidx.compose.animation.SharedTransitionScope.MiniPlayer(state: PlaybackUiState, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, onTogglePlay: () -> Unit, onClick: () -> Unit) {
    val currentItem = state.currentMediaItem ?: return
    Surface(modifier = Modifier.fillMaxWidth().padding(8.dp).height(72.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 8.dp) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.size(56.dp).sharedElement(rememberSharedContentState(key = "mini_cover_${currentItem.mediaId}"), animatedVisibilityScope = animatedVisibilityScope)) {
                if (currentItem.mediaMetadata.artworkUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(currentItem.mediaMetadata.artworkUri).crossfade(true).placeholder(R.drawable.ic_audiobook_cover).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else CompactBookPlaceholder(title = currentItem.mediaMetadata.title?.toString() ?: "A", modifier = Modifier.fillMaxSize())
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                Text(text = currentItem.mediaMetadata.title?.toString() ?: stringResource(R.string.unknown_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                Text(text = currentItem.mediaMetadata.artist?.toString() ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.basicMarquee())
            }
            IconButton(onClick = onTogglePlay) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = stringResource(R.string.pause_play_btn), modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        val progress = if (state.duration > 0) state.currentPosition.toFloat() / state.duration.toFloat() else 0f
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = MaterialTheme.colorScheme.primary, trackColor = androidx.compose.ui.graphics.Color.Transparent) }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun androidx.compose.animation.SharedTransitionScope.BookListItem(book: Music, isFavorite: Boolean, progress: Float, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, keyPrefix: String = "list", onAddToQueue: () -> Unit, onLongClick: () -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column {
            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
Surface(modifier = Modifier.size(60.dp).sharedElement(rememberSharedContentState(key = "${keyPrefix}_cover_${book.id}"), animatedVisibilityScope = animatedVisibilityScope), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                    if (book.artUri != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(book.artUri).crossfade(true).placeholder(R.drawable.ic_audiobook_cover).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else CompactBookPlaceholder(title = book.title, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, false).heightIn(max = 48.dp).verticalScroll(rememberScrollState()))
                        if (isFavorite) { Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.Favorite, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.size(16.dp)) }
                    }
                    Text(text = book.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                    val currentPos = (progress * book.duration).toLong()
                    Text(
                        text = if (progress > 0f) "${formatDuration(currentPos)} / ${formatDuration(book.duration)}" else formatDuration(book.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onAddToQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.playlist_btn), tint = MaterialTheme.colorScheme.primary) }
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = androidx.compose.ui.graphics.Color.Transparent)
        }
    }
}
