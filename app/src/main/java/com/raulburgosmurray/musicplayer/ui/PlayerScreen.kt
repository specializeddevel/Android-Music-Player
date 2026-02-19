package com.raulburgosmurray.musicplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.raulburgosmurray.musicplayer.Music
import com.raulburgosmurray.musicplayer.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    from: String,
    onBack: () -> Unit,
    onTransferClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val isPlaying = state.isPlaying
    val progress = if (state.duration > 0) state.currentPosition.toFloat() / state.duration.toFloat() else 0f
    val duration = state.duration
    val position = state.currentPosition
    val currentItem = state.currentMediaItem
    val activeTimerMinutes = state.sleepTimerMinutes

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    val detailsSheetState = rememberModalBottomSheetState()
    var showDetailsSheet by remember { mutableStateOf(false) }

    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var capturedPositionForBookmark by remember { mutableStateOf(0L) }
    var showShareFileConfirmation by remember { mutableStateOf(false) }

    val playIcon = Icons.Default.PlayArrow
    val pauseIcon = Icons.Default.Pause
    val speedIcon = Icons.Default.Speed
    val timerIcon = Icons.Default.Timer
    val timerOffIcon = Icons.Default.TimerOff
    
    // Usar iconos estÃ¡ndar de Material que siempre funcionan
    val skipNextIcon = Icons.Default.FastForward
    val skipPreviousIcon = Icons.Default.FastRewind
    val undoIcon = Icons.AutoMirrored.Filled.Undo

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "AtrÃ¡s") }
            Row {
                IconButton(onClick = { showHistorySheet = true }) { Icon(Icons.Default.History, contentDescription = "Historial") }
                IconButton(onClick = { showQueueSheet = true }) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Cola") }
                IconButton(onClick = { showDetailsSheet = true }) { Icon(Icons.Default.Info, contentDescription = "Detalles") }
                IconButton(onClick = { showShareFileConfirmation = true }) { Icon(Icons.Default.Share, "Compartir Archivo") }
                IconButton(onClick = { currentItem?.mediaId?.let { onTransferClick(it) } }) { Icon(Icons.Default.Wifi, "Transferir") }
            }
        }

        Spacer(Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            with(sharedTransitionScope) {
                if (currentItem?.mediaMetadata?.artworkUri != null) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(currentItem.mediaMetadata.artworkUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize().sharedElement(rememberSharedContentState(key = "${from}_cover_${currentItem.mediaId}"), animatedVisibilityScope = animatedVisibilityScope), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().sharedElement(rememberSharedContentState(key = "${from}_cover_${currentItem?.mediaId}"), animatedVisibilityScope = animatedVisibilityScope), contentAlignment = Alignment.Center) {
                        BookPlaceholder(title = currentItem?.mediaMetadata?.title?.toString() ?: "A", modifier = Modifier.size(120.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = currentItem?.mediaMetadata?.title?.toString() ?: stringResource(R.string.unknown_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = currentMediaItemArtist(currentItem), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { viewModel.toggleFavorite() }) { Icon(if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorito", tint = if (state.isFavorite) Color.Red else LocalContentColor.current) }
            }

            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(value = progress.coerceIn(0f, 1f), onValueChange = { viewModel.seekTo((it * duration).toLong()) }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatTime(position), style = MaterialTheme.typography.labelSmall)
                    if (state.lastPositionBeforeSeek != null) {
                        TextButton(onClick = { viewModel.undoSeek() }, modifier = Modifier.height(32.dp)) {
                            Icon(undoIcon, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.undo_seek), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(text = formatTime(state.duration), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.skipBackward(30000L) }) { Icon(painter = painterResource(id = R.drawable.rewind_30), contentDescription = null, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { viewModel.skipBackward(10000L) }) { Icon(skipPreviousIcon, contentDescription = null, modifier = Modifier.size(44.dp)) }
                FilledIconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(80.dp), shape = RoundedCornerShape(24.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Icon(if (isPlaying) pauseIcon else playIcon, contentDescription = null, modifier = Modifier.size(44.dp)) }
                IconButton(onClick = { viewModel.skipForward(10000L) }) { Icon(skipNextIcon, contentDescription = null, modifier = Modifier.size(44.dp)) }
                IconButton(onClick = { viewModel.skipForward(30000L) }) { Icon(painter = painterResource(id = R.drawable.fast_forward_10), contentDescription = null, modifier = Modifier.size(36.dp)) }
            }

            Spacer(Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = { showSpeedSheet = true }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.height(56.dp).weight(1f).padding(horizontal = 8.dp)) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(speedIcon, null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp))
                        Text(text = "${state.playbackSpeed}x", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(onClick = { showTimerSheet = true }, shape = RoundedCornerShape(20.dp), color = if (activeTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (activeTimerMinutes > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(56.dp).weight(1f).padding(horizontal = 8.dp)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (activeTimerMinutes > 0) timerIcon else timerOffIcon, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = if (activeTimerMinutes > 0) "${activeTimerMinutes}m" else "Timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (state.isShakeWaiting) {
                            Text(
                                text = "¡Sacude para ampliar!",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSpeedSheet) { ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, sheetState = speedSheetState) { SpeedSelectorContent(currentSpeed = state.playbackSpeed, onSpeedSelected = { viewModel.setPlaybackSpeed(it); showSpeedSheet = false }) } }
    if (showTimerSheet) { ModalBottomSheet(onDismissRequest = { showTimerSheet = false }, sheetState = timerSheetState) { TimerSelectorContent(activeTimerMinutes = activeTimerMinutes, onTimerSelected = { viewModel.startSleepTimer(it); showTimerSheet = false }, onCancelTimer = { viewModel.cancelSleepTimer(); showTimerSheet = false }) } }
    if (showChapterSheet) { ModalBottomSheet(onDismissRequest = { showChapterSheet = false }, sheetState = chapterSheetState) { ChapterSelectorContent(chapters = state.chapters, currentPosition = state.currentPosition, onChapterSelected = { viewModel.seekTo(it.startMs); showChapterSheet = false }) } }
    if (showHistorySheet) { ModalBottomSheet(onDismissRequest = { showHistorySheet = false }, sheetState = historySheetState) { HistorySelectorContent(history = state.history, onActionSelected = { viewModel.seekTo(it.audioPositionMs); showHistorySheet = false }) } }
    if (showQueueSheet) { ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, sheetState = queueSheetState) { QueueSelectorContent(playlist = state.playlist, currentIndex = state.currentIndex, onItemClicked = { index -> viewModel.skipToQueueItem(index); showQueueSheet = false }, onRemoveItem = { viewModel.removeItemFromQueue(it) }, onMoveUp = { if (it > 0) viewModel.moveItemInQueue(it, it - 1) }, onMoveDown = { if (it < state.playlist.size - 1) viewModel.moveItemInQueue(it, it + 1) }) } }
    if (showBookmarkSheet) { ModalBottomSheet(onDismissRequest = { showBookmarkSheet = false }, sheetState = bookmarkSheetState) { BookmarkSelectorContent(bookmarks = state.bookmarks, onBookmarkSelected = { viewModel.seekTo(it.position); showBookmarkSheet = false }, onDeleteBookmark = { id -> viewModel.deleteBookmark(id) }) } }
    if (showAddBookmarkDialog) { AddBookmarkDialog(currentPosition = capturedPositionForBookmark, onDismiss = { showAddBookmarkDialog = false }, onConfirm = { note -> viewModel.addBookmark(note, capturedPositionForBookmark); showAddBookmarkDialog = false }) }
    if (showDetailsSheet && state.currentMusicDetails != null) { ModalBottomSheet(onDismissRequest = { showDetailsSheet = false }, sheetState = detailsSheetState) { BookDetailsContent(book = state.currentMusicDetails!!, allBooks = emptyList()) } }
    if (showShareFileConfirmation) {
        AlertDialog(onDismissRequest = { showShareFileConfirmation = false }, title = { Text(stringResource(R.string.share_file_warning_title)) }, text = { Text(stringResource(R.string.share_file_warning_message)) }, confirmButton = { Button(onClick = { showShareFileConfirmation = false; viewModel.shareFile(context) }) { Text(stringResource(R.string.confirm)) } }, dismissButton = { TextButton(onClick = { showShareFileConfirmation = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
fun currentMediaItemArtist(item: androidx.media3.common.MediaItem?): String {
    return item?.mediaMetadata?.artist?.toString() ?: "Desconocido"
}

@Composable
fun SpeedSelectorContent(currentSpeed: Float, onSpeedSelected: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Velocidad de reproducciÃ³n", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        speeds.forEach { speed ->
            Surface(onClick = { onSpeedSelected(speed) }, color = if (speed == currentSpeed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${speed}x", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (speed == currentSpeed) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun TimerSelectorContent(activeTimerMinutes: Int, onTimerSelected: (Int) -> Unit, onCancelTimer: () -> Unit) {
    val options = listOf(5, 15, 30, 45, 60)
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Temporizador de apagado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        options.forEach { mins ->
            Surface(onClick = { onTimerSelected(mins) }, color = if (mins == activeTimerMinutes) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$mins minutos", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (mins == activeTimerMinutes) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (activeTimerMinutes > 0) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCancelTimer, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text("Cancelar Temporizador") }
        }
    }
}

@Composable
fun HistorySelectorContent(history: List<com.raulburgosmurray.musicplayer.HistoryAction>, onActionSelected: (com.raulburgosmurray.musicplayer.HistoryAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Historial reciente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (history.isEmpty()) { Text("No hay acciones recientes", color = MaterialTheme.colorScheme.secondary) }
        else {
            LazyColumn { items(history) { action ->
                Surface(onClick = { onActionSelected(action) }, shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(action.label, fontWeight = FontWeight.Bold); Text(formatDuration(action.audioPositionMs), style = MaterialTheme.typography.bodySmall) }
                        Icon(Icons.AutoMirrored.Filled.Undo, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            } }
        }
    }
}

@Composable
fun QueueSelectorContent(playlist: List<androidx.media3.common.MediaItem>, currentIndex: Int, onItemClicked: (Int) -> Unit, onRemoveItem: (Int) -> Unit, onMoveUp: (Int) -> Unit, onMoveDown: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Cola de reproducciÃ³n", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f, false).heightIn(max = 400.dp)) {
            items(playlist.size) { index ->
                val item = playlist[index]
                Surface(onClick = { onItemClicked(index) }, color = if (index == currentIndex) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall)
                        Column(modifier = Modifier.weight(1f)) { Text(item.mediaMetadata.title?.toString() ?: "Sin tÃ­tulo", fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal); Text(item.mediaMetadata.artist?.toString() ?: "Desconocido", style = MaterialTheme.typography.bodySmall) }
                        IconButton(onClick = { onRemoveItem(index) }) { Icon(Icons.Default.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error) }
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

@Composable
fun BookPlaceholder(title: String, modifier: Modifier = Modifier) {
    val initial = title.firstOrNull()?.uppercase() ?: "?"
    Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(text = initial, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BookmarkSelectorContent(bookmarks: List<com.raulburgosmurray.musicplayer.data.Bookmark>, onBookmarkSelected: (com.raulburgosmurray.musicplayer.data.Bookmark) -> Unit, onDeleteBookmark: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("Marcadores manuales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (bookmarks.isEmpty()) { Text("No hay marcadores aÃºn", color = MaterialTheme.colorScheme.secondary) }
        else {
            LazyColumn { items(bookmarks) { bookmark ->
                Surface(onClick = { onBookmarkSelected(bookmark) }, shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(formatDuration(bookmark.position), fontWeight = FontWeight.Bold); if (bookmark.note.isNotEmpty()) Text(bookmark.note) }
                        IconButton(onClick = { onDeleteBookmark(bookmark.id) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
            } }
        }
    }
}

@Composable
fun ChapterSelectorContent(chapters: List<com.raulburgosmurray.musicplayer.Chapter>, currentPosition: Long, onChapterSelected: (com.raulburgosmurray.musicplayer.Chapter) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text("CapÃ­tulos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (chapters.isEmpty()) { Text("No se detectaron capÃ­tulos", color = MaterialTheme.colorScheme.secondary) }
        else {
            LazyColumn { items(chapters) { chapter ->
                Surface(onClick = { onChapterSelected(chapter) }, shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column { Text(chapter.title, fontWeight = FontWeight.Bold); Text(formatDuration(chapter.startMs)) }
                    }
                }
            } }
        }
    }
}

@Composable
fun AddBookmarkDialog(currentPosition: Long, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("AÃ±adir Marcador") },
        text = { Column { Text("Guardar posiciÃ³n: ${formatDuration(currentPosition)}"); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { onConfirm(note) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
