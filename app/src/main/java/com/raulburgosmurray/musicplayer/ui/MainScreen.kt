package com.raulburgosmurray.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.raulburgosmurray.musicplayer.Music
import com.raulburgosmurray.musicplayer.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    playbackViewModel: PlaybackViewModel,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onBookClick: (Music) -> Unit,
    onMiniPlayerClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val books by mainViewModel.books.collectAsState()
    val favoriteIds by mainViewModel.favoriteIds.collectAsState()
    val bookProgress by mainViewModel.bookProgress.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val playbackState by playbackViewModel.uiState.collectAsState()
    val currentSortOrder by mainViewModel.sortOrder.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val filteredBooks = if (searchQuery.isEmpty()) {
        books
    } else {
        books.filter { it.title.contains(searchQuery, ignoreCase = true) || it.artist.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mis Audiolibros", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Nombre (A-Z)") },
                                leadingIcon = { if (currentSortOrder == SortOrder.TITLE) Icon(Icons.Default.Check, null) },
                                onClick = { mainViewModel.setSortOrder(SortOrder.TITLE); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Autor") },
                                leadingIcon = { if (currentSortOrder == SortOrder.ARTIST) Icon(Icons.Default.Check, null) },
                                onClick = { mainViewModel.setSortOrder(SortOrder.ARTIST); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Avance") },
                                leadingIcon = { if (currentSortOrder == SortOrder.PROGRESS) Icon(Icons.Default.Check, null) },
                                onClick = { mainViewModel.setSortOrder(SortOrder.PROGRESS); showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Recientes") },
                                leadingIcon = { if (currentSortOrder == SortOrder.RECENT) Icon(Icons.Default.Check, null) },
                                onClick = { mainViewModel.setSortOrder(SortOrder.RECENT); showSortMenu = false }
                            )
                        }
                    }
                    IconButton(onClick = onFavoritesClick) {
                        Icon(
                            Icons.Default.Favorite, 
                            contentDescription = "Favoritos",
                            tint = if (favoriteIds.isNotEmpty()) Color.Red else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        bottomBar = {
            if (playbackState.currentMediaItem != null) {
                with(sharedTransitionScope) {
                    MiniPlayer(
                        state = playbackState,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onTogglePlay = { playbackViewModel.togglePlayPause() },
                        onClick = onMiniPlayerClick
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { },
                        expanded = false,
                        onExpandedChange = { },
                        placeholder = { Text("Buscar libros...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    )
                },
                expanded = false,
                onExpandedChange = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {}

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredBooks) { book ->
                        with(sharedTransitionScope) {
                            BookListItem(
                                book = book, 
                                isFavorite = favoriteIds.contains(book.id),
                                progress = bookProgress[book.id] ?: 0f,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onAddToQueue = {
                                    playbackViewModel.addToQueue(book)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Añadido a la cola: ${book.title}",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                },
                                onClick = { onBookClick(book) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun androidx.compose.animation.SharedTransitionScope.MiniPlayer(
    state: PlaybackState,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit
) {
    val currentItem = state.currentMediaItem ?: return
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .size(56.dp)
                    .sharedElement(
                        rememberSharedContentState(key = "mini_cover_${currentItem.mediaId}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
            ) {
                if (currentItem.mediaMetadata.artworkUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(currentItem.mediaMetadata.artworkUri)
                            .crossfade(true)
                            .placeholder(R.drawable.ic_audiobook_cover)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    BookPlaceholder(
                        title = currentItem.mediaMetadata.title?.toString() ?: "A", 
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentItem.mediaMetadata.title?.toString() ?: "Sin título",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentItem.mediaMetadata.artist?.toString() ?: "Autor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        val progress = if (state.duration > 0) state.currentPosition.toFloat() / state.duration.toFloat() else 0f
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomStart
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun androidx.compose.animation.SharedTransitionScope.BookListItem(
    book: Music, 
    isFavorite: Boolean, 
    progress: Float,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    keyPrefix: String = "list",
    onAddToQueue: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(60.dp)
                        .sharedElement(
                            rememberSharedContentState(key = "${keyPrefix}_cover_${book.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        ), 
                    shape = RoundedCornerShape(12.dp), 
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (book.artUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(book.artUri)
                                .crossfade(true)
                                .placeholder(R.drawable.ic_audiobook_cover)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        BookPlaceholder(title = book.title, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, false))
                        if (isFavorite) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(text = book.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                }
                IconButton(onClick = onAddToQueue) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Añadir a la cola", tint = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
