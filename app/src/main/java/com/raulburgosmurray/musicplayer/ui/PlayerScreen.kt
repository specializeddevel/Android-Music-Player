package com.raulburgosmurray.musicplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulburgosmurray.musicplayer.R
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    origin: String = "list",
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val activeTimerMinutes = state.sleepTimerMinutes
    
    // Asignación explícita de iconos para evitar errores de compilación
    val skipPreviousIcon = Icons.Default.SkipPrevious
    val skipNextIcon = Icons.Default.SkipNext
    val playIcon = Icons.Rounded.PlayArrow
    val pauseIcon = Icons.Rounded.Pause
    val favoriteIcon = Icons.Default.Favorite
    val favoriteBorderIcon = Icons.Default.FavoriteBorder
    val historyIcon = Icons.Default.History
    val bookmarkIcon = Icons.Default.Bookmark
    val bookmarkAddIcon = Icons.Default.BookmarkAdd
    val chaptersIcon = Icons.AutoMirrored.Filled.List
    val backIcon = Icons.Default.KeyboardArrowDown
    val menuIcon = Icons.Default.MoreVert
    val undoIcon = Icons.AutoMirrored.Filled.Undo
    val speedIcon = Icons.Default.Speed
    val timerIcon = Icons.Default.Timer
    val timerOffIcon = Icons.Default.TimerOff

    val speedSheetState = rememberModalBottomSheetState()
    var showSpeedSheet by remember { mutableStateOf(false) }
    
    val timerSheetState = rememberModalBottomSheetState()
    var showTimerSheet by remember { mutableStateOf(false) }

    val chapterSheetState = rememberModalBottomSheetState()
    var showChapterSheet by remember { mutableStateOf(false) }

    val historySheetState = rememberModalBottomSheetState()
    var showHistorySheet by remember { mutableStateOf(false) }

    val queueSheetState = rememberModalBottomSheetState()
    var showQueueSheet by remember { mutableStateOf(false) }

    val bookmarkSheetState = rememberModalBottomSheetState()
    var showBookmarkSheet by remember { mutableStateOf(false) }
    
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var capturedPositionForBookmark by remember { mutableLongStateOf(0L) }
    
    val currentMediaItem = state.currentMediaItem
    val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Sin título"
    val author = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Autor desconocido"
    val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri
    
    val isPlaying = state.isPlaying
    val position = state.currentPosition
    val duration = if (state.duration > 0) state.duration else 1L
    val progress = position.toFloat() / duration.toFloat()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(backIcon, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        capturedPositionForBookmark = state.currentPosition
                        showAddBookmarkDialog = true 
                    }) {
                        Icon(bookmarkAddIcon, contentDescription = "Añadir Marcador")
                    }
                    IconButton(onClick = { showBookmarkSheet = true }) {
                        Icon(bookmarkIcon, contentDescription = "Marcadores")
                    }
                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Cola de reproducción")
                    }
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(historyIcon, contentDescription = "Historial")
                    }
                    IconButton(onClick = { showChapterSheet = true }) {
                        Icon(chaptersIcon, contentDescription = "Capítulos")
                    }
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (state.isFavorite) favoriteIcon else favoriteBorderIcon,
                            contentDescription = "Favorite",
                            tint = if (state.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { /* Menú */ }) {
                        Icon(menuIcon, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Portada Dinámica
            Surface(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .then(
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                rememberSharedContentState(key = "${origin}_cover_${state.currentMediaItem?.mediaId}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    ),
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (artworkUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artworkUri).crossfade(true).placeholder(R.drawable.ic_audiobook_cover).error(R.drawable.ic_audiobook_cover).build(),
                        contentDescription = "Cover", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                } else {
                    BookPlaceholder(title = title, author = author, modifier = Modifier.fillMaxSize(), isLarge = true)
                }
            }

            // Título y Autor
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }

            // Barra de progreso
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = formatTime(position), style = MaterialTheme.typography.labelSmall)
                    
                    if (state.lastPositionBeforeSeek != null) {
                        TextButton(
                            onClick = { viewModel.undoSeek() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(undoIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("DESHACER SALTO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(text = formatTime(state.duration), style = MaterialTheme.typography.labelSmall)
                }
            }

            // Controles
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.skipBackward(30000L) }) {
                    Icon(painter = painterResource(id = R.drawable.rewind_30), contentDescription = "Rewind 30s", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { viewModel.skipBackward(10000L) }) {
                    Icon(skipPreviousIcon, contentDescription = "Previous", modifier = Modifier.size(44.dp))
                }
                
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(if (isPlaying) pauseIcon else playIcon, contentDescription = "Play/Pause", modifier = Modifier.size(44.dp))
                }

                IconButton(onClick = { viewModel.skipForward(10000L) }) {
                    Icon(skipNextIcon, contentDescription = "Next", modifier = Modifier.size(44.dp))
                }
                IconButton(onClick = { viewModel.skipForward(30000L) }) {
                    Icon(painter = painterResource(id = R.drawable.fast_forward_10), contentDescription = "Forward 30s", modifier = Modifier.size(36.dp))
                }
            }

            // Adicionales
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = { showSpeedSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(speedIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${state.playbackSpeed}x", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showTimerSheet = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTimerMinutes > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (activeTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(if (activeTimerMinutes > 0) timerIcon else timerOffIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (activeTimerMinutes > 0) "${activeTimerMinutes}m" else "Timer", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal Bottom Sheets
    if (showSpeedSheet) {
        ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, sheetState = speedSheetState) {
            SpeedSelectorContent(currentSpeed = state.playbackSpeed, onSpeedSelected = { viewModel.setPlaybackSpeed(it); showSpeedSheet = false })
        }
    }

    if (showTimerSheet) {
        ModalBottomSheet(onDismissRequest = { showTimerSheet = false }, sheetState = timerSheetState) {
            TimerSelectorContent(activeTimerMinutes = activeTimerMinutes, onTimerSelected = { viewModel.startSleepTimer(it); showTimerSheet = false }, onCancelTimer = { viewModel.cancelSleepTimer(); showTimerSheet = false })
        }
    }

    if (showChapterSheet) {
        ModalBottomSheet(onDismissRequest = { showChapterSheet = false }, sheetState = chapterSheetState) {
            ChapterSelectorContent(chapters = state.chapters, currentPosition = state.currentPosition, onChapterSelected = { viewModel.seekTo(it.startMs); showChapterSheet = false })
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }, sheetState = historySheetState) {
            HistorySelectorContent(history = state.history, onActionSelected = { viewModel.seekTo(it.audioPositionMs); showHistorySheet = false })
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, sheetState = queueSheetState) {
            QueueSelectorContent(
                playlist = state.playlist,
                currentIndex = state.currentIndex,
                onItemClicked = { index -> viewModel.skipToQueueItem(index); showQueueSheet = false },
                onRemoveItem = { viewModel.removeItemFromQueue(it) },
                onMoveUp = { if (it > 0) viewModel.moveItemInQueue(it, it - 1) },
                onMoveDown = { if (it < state.playlist.size - 1) viewModel.moveItemInQueue(it, it + 1) }
            )
        }
    }

    if (showBookmarkSheet) {
        ModalBottomSheet(onDismissRequest = { showBookmarkSheet = false }, sheetState = bookmarkSheetState) {
            BookmarkSelectorContent(
                bookmarks = state.bookmarks, 
                onBookmarkSelected = { viewModel.seekTo(it.position); showBookmarkSheet = false },
                onDeleteBookmark = { viewModel.deleteBookmark(it.id) }
            )
        }
    }

    if (showAddBookmarkDialog) {
        AddBookmarkDialog(
            currentPosition = capturedPositionForBookmark,
            onDismiss = { showAddBookmarkDialog = false },
            onConfirm = { note -> 
                viewModel.addBookmark(note, capturedPositionForBookmark)
                showAddBookmarkDialog = false
            }
        )
    }
}

@Composable
fun BookmarkSelectorContent(
    bookmarks: List<com.raulburgosmurray.musicplayer.data.Bookmark>, 
    onBookmarkSelected: (com.raulburgosmurray.musicplayer.data.Bookmark) -> Unit,
    onDeleteBookmark: (com.raulburgosmurray.musicplayer.data.Bookmark) -> Unit
) {
    val bookmarkIcon = Icons.Default.Bookmark
    val deleteIcon = Icons.Default.Delete

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Marcadores Manuales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        if (bookmarks.isEmpty()) {
            Text("No has guardado marcadores todavía.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(bookmarks) { bookmark ->
                    Surface(
                        onClick = { onBookmarkSelected(bookmark) }, 
                        shape = RoundedCornerShape(12.dp), 
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(bookmarkIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = formatDuration(bookmark.position), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                if (bookmark.note.isNotEmpty()) {
                                    Text(text = bookmark.note, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(text = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(bookmark.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(onClick = { onDeleteBookmark(bookmark) }) {
                                Icon(deleteIcon, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddBookmarkDialog(
    currentPosition: Long,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir Marcador") },
        text = {
            Column {
                Text(
                    "Se guardará la posición: ${formatDuration(currentPosition)}", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota (opcional)") },
                    placeholder = { Text("Ej: Cita importante") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(note) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ChapterSelectorContent(chapters: List<com.raulburgosmurray.musicplayer.Chapter>, currentPosition: Long, onChapterSelected: (com.raulburgosmurray.musicplayer.Chapter) -> Unit) {
    val playIcon = Icons.Default.PlayArrow
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Capítulos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        if (chapters.isEmpty()) {
            Text("No se han detectado capítulos.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chapters) { chapter ->
                    val isPlaying = currentPosition >= chapter.startMs && (chapters.getOrNull(chapters.indexOf(chapter) + 1)?.startMs ?: Long.MAX_VALUE) > currentPosition
                    Surface(onClick = { onChapterSelected(chapter) }, shape = RoundedCornerShape(12.dp), color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = chapter.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal)
                                Text(text = formatDuration(chapter.startMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            if (isPlaying) Icon(playIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistorySelectorContent(history: List<com.raulburgosmurray.musicplayer.HistoryAction>, onActionSelected: (com.raulburgosmurray.musicplayer.HistoryAction) -> Unit) {
    val playIcon = Icons.Default.PlayArrow
    val pauseIcon = Icons.Default.Pause
    val replayIcon = Icons.Default.Replay
    val forwardIcon = Icons.AutoMirrored.Filled.Forward
    val historyIcon = Icons.Default.History

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Historial de Actividad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        if (history.isEmpty()) {
            Text("Aún no hay acciones registradas.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { action ->
                    Surface(onClick = { onActionSelected(action) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = when {
                                action.label.contains("Play") -> playIcon
                                action.label.contains("Reproducir") -> playIcon
                                action.label.contains("Pausa") -> pauseIcon
                                action.label.contains("Retroceder") -> replayIcon
                                action.label.contains("Adelantar") -> forwardIcon
                                else -> historyIcon
                            }
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = action.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(text = "En el audio: ${formatDuration(action.audioPositionMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(action.realTimestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedSelectorContent(currentSpeed: Float, onSpeedSelected: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f)
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Velocidad de Narración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(speeds) { speed ->
                val isSelected = speed == currentSpeed
                Surface(onClick = { onSpeedSelected(speed) }, shape = RoundedCornerShape(16.dp), color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.height(60.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(text = "${speed}x", style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }
    }
}

@Composable
fun TimerSelectorContent(activeTimerMinutes: Int, onTimerSelected: (Int) -> Unit, onCancelTimer: () -> Unit) {
    val times = listOf(5, 15, 30, 45, 60, 90, 120)
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Temporizador de Sueño", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(times) { mins ->
                val isSelected = mins == activeTimerMinutes
                Surface(onClick = { onTimerSelected(mins) }, shape = RoundedCornerShape(16.dp), color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.height(60.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(text = "${mins}m", style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCancelTimer, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), shape = RoundedCornerShape(16.dp), enabled = activeTimerMinutes > 0) {
            Text("Desactivar Temporizador", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QueueSelectorContent(
    playlist: List<androidx.media3.common.MediaItem>,
    currentIndex: Int,
    onItemClicked: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Cola de Reproducción", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        
        if (playlist.isEmpty()) {
            Text("La cola está vacía.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playlist.size) { index ->
                    val item = playlist[index]
                    val isPlaying = index == currentIndex
                    
                    Surface(
                        onClick = { onItemClicked(index) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isPlaying) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Portada pequeña
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(item.mediaMetadata.artworkUri)
                                        .crossfade(true)
                                        .placeholder(R.drawable.ic_audiobook_cover)
                                        .error(R.drawable.ic_audiobook_cover)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.mediaMetadata.title?.toString() ?: "Sin título",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                                Text(
                                    text = item.mediaMetadata.artist?.toString() ?: "Autor desconocido",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Controles de la cola
                            Row {
                                IconButton(onClick = { onMoveUp(index) }, enabled = index > 0) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Subir", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onMoveDown(index) }, enabled = index < playlist.size - 1) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onRemoveItem(index) }, enabled = !isPlaying) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
}

private fun formatDuration(duration: Long): String {
    val totalSeconds = duration / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
}
