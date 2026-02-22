package com.raulburgosmurray.musicplayer.ui

import android.net.Uri
import android.content.res.Configuration
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
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
import com.raulburgosmurray.musicplayer.Constants
import com.raulburgosmurray.musicplayer.encodeBookId
import com.raulburgosmurray.musicplayer.ui.PlaybackUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    from: String,
    onBack: () -> Unit,
    onTransferClick: (String) -> Unit,
    navController: androidx.navigation.NavController
) {
    val state by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    
    // DETECCION DE TABLETA: Ancho mínimo de 600dp (~7 pulgadas)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val shouldUseLandscapeLayout = isLandscape && isTablet

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val speedSheetState = rememberModalBottomSheetState()
    var showSpeedSheet by remember { mutableStateOf(false) }
    val timerSheetState = rememberModalBottomSheetState()
    var showTimerSheet by remember { mutableStateOf(false) }
    val historySheetState = rememberModalBottomSheetState()
    var showHistorySheet by remember { mutableStateOf(false) }
    val queueSheetState = rememberModalBottomSheetState()
    var showQueueSheet by remember { mutableStateOf(false) }
    val detailsSheetState = rememberModalBottomSheetState()
    var showDetailsSheet by remember { mutableStateOf(false) }
    val bookmarkSheetState = rememberModalBottomSheetState()
    var showBookmarkSheet by remember { mutableStateOf(false) }

    var showShareFileConfirmation by remember { mutableStateOf(false) }

    Scaffold { padding ->
        if (shouldUseLandscapeLayout) {
            LandscapePlayerContent(
                state = state, viewModel = viewModel, sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope, from = from, onBack = onBack,
                onTransferClick = onTransferClick, onShowHistory = { showHistorySheet = true },
                onShowQueue = { showQueueSheet = true }, onShowDetails = { showDetailsSheet = true },
                onShowShare = { showShareFileConfirmation = true }, onShowSpeed = { showSpeedSheet = true },
                onShowTimer = { showTimerSheet = true }, onShowBookmark = { showBookmarkSheet = true }
            )
        } else {
            PortraitPlayerContent(
                state = state, viewModel = viewModel, sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope, from = from, onBack = onBack,
                onTransferClick = onTransferClick, onShowHistory = { showHistorySheet = true },
                onShowQueue = { showQueueSheet = true }, onShowDetails = { showDetailsSheet = true },
                onShowShare = { showShareFileConfirmation = true }, onShowSpeed = { showSpeedSheet = true },
                onShowTimer = { showTimerSheet = true }, onShowBookmark = { showBookmarkSheet = true }
            )
        }
    }

    if (showSpeedSheet) { ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, sheetState = speedSheetState) { SpeedSelectorContent(currentSpeed = state.playbackSpeed, onSpeedSelected = { viewModel.setPlaybackSpeed(it); showSpeedSheet = false }) } }
    if (showTimerSheet) { ModalBottomSheet(onDismissRequest = { showTimerSheet = false }, sheetState = timerSheetState) { TimerSelectorContent(activeTimerMinutes = state.sleepTimerMinutes, onTimerSelected = { viewModel.startSleepTimer(it); showTimerSheet = false }, onCancelTimer = { viewModel.cancelSleepTimer(); showTimerSheet = false }) } }
    if (showHistorySheet) { ModalBottomSheet(onDismissRequest = { showHistorySheet = false }, sheetState = historySheetState) { HistorySelectorContent(history = state.history, onActionSelected = { viewModel.seekTo(it.audioPositionMs); showHistorySheet = false }) } }
    if (showQueueSheet) { ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, sheetState = queueSheetState) { QueueSelectorContent(playlist = state.playlist, currentIndex = state.currentIndex, onItemClicked = { index -> viewModel.skipToQueueItem(index); showQueueSheet = false }, onRemoveItem = { viewModel.removeItemFromQueue(it) }, onShowDetails = { showDetailsSheet = true }) } }
    if (showDetailsSheet && state.currentMusicDetails != null) { ModalBottomSheet(onDismissRequest = { showDetailsSheet = false }, sheetState = detailsSheetState) { BookDetailsContent(book = state.currentMusicDetails!!, allBooks = emptyList(), onEditMetadata = { bookId -> navController.navigate("metadata_editor?bookId=${encodeBookId(bookId)}") }) } }
    if (showBookmarkSheet) { ModalBottomSheet(onDismissRequest = { showBookmarkSheet = false }, sheetState = bookmarkSheetState) { BookmarkSelectorContent(bookmarks = state.bookmarks, onBookmarkSelected = { viewModel.seekTo(it.position); showBookmarkSheet = false }, onDeleteBookmark = { id -> viewModel.deleteBookmark(id) }) } }
    if (showShareFileConfirmation) {
        AlertDialog(onDismissRequest = { showShareFileConfirmation = false }, title = { Text(stringResource(R.string.share_file_warning_title)) }, text = { Text(stringResource(R.string.share_file_warning_message)) }, confirmButton = { Button(onClick = { showShareFileConfirmation = false; viewModel.shareFile(context) }) { Text(stringResource(R.string.confirm)) } }, dismissButton = { TextButton(onClick = { showShareFileConfirmation = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PortraitPlayerContent(state: PlaybackUiState, viewModel: PlaybackViewModel, sharedTransitionScope: androidx.compose.animation.SharedTransitionScope, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, from: String, onBack: () -> Unit, onTransferClick: (String) -> Unit, onShowHistory: () -> Unit, onShowQueue: () -> Unit, onShowDetails: () -> Unit, onShowShare: () -> Unit, onShowSpeed: () -> Unit, onShowTimer: () -> Unit, onShowBookmark: () -> Unit) {
    val currentItem = state.currentMediaItem
    var pressedArea by remember { mutableStateOf<CoverTapArea?>(null) }
    
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_btn)) }
            Row {
                IconButton(onClick = { viewModel.toggleFavorite() }) { Icon(if (state.isFavorite) Icons.Filled.Favorite else Icons.Default.Favorite, contentDescription = stringResource(R.string.favourites_btn), tint = if (state.isFavorite) Color.Red else LocalContentColor.current) }
                IconButton(onClick = onShowHistory) { Icon(Icons.Default.History, null) }
                IconButton(onClick = onShowQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) }
                IconButton(onClick = onShowBookmark) { Icon(Icons.Default.Bookmark, null) }
                IconButton(onClick = onShowDetails) { Icon(Icons.Default.Info, null) }
                IconButton(onClick = onShowShare) { Icon(Icons.Default.Share, null) }
                if (com.raulburgosmurray.musicplayer.FeatureFlags.P2P_TRANSFER) {
                    IconButton(onClick = { currentItem?.mediaId?.let { onTransferClick(it) } }) { Icon(Icons.Default.Wifi, null) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            with(sharedTransitionScope) {
                if (currentItem?.mediaMetadata?.artworkUri != null) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(currentItem.mediaMetadata.artworkUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize().sharedElement(rememberSharedContentState(key = "${from}_cover_${currentItem.mediaId}"), animatedVisibilityScope = animatedVisibilityScope), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().sharedElement(rememberSharedContentState(key = "${from}_cover_${currentItem?.mediaId}"), animatedVisibilityScope = animatedVisibilityScope)) {
                        BookPlaceholder(title = currentItem?.mediaMetadata?.title?.toString() ?: "A", modifier = Modifier.fillMaxSize())
                    }
                }
            }
            // Touch controls overlay
            CoverTouchControls(
                modifier = Modifier.fillMaxSize(),
                pressedArea = pressedArea,
                onAreaPressed = { pressedArea = it },
                onAreaReleased = { pressedArea = null },
                onLeftTap = { viewModel.skipBackward(Constants.SKIP_BACKWARD_MS) },
                onCenterTap = { viewModel.togglePlayPause() },
                onRightTap = { viewModel.skipForward(Constants.SKIP_FORWARD_MS) }
            )
        }
        Spacer(Modifier.height(32.dp))
        PlayerControls(state, viewModel, onShowSpeed, onShowTimer)
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LandscapePlayerContent(state: PlaybackUiState, viewModel: PlaybackViewModel, sharedTransitionScope: androidx.compose.animation.SharedTransitionScope, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, from: String, onBack: () -> Unit, onTransferClick: (String) -> Unit, onShowHistory: () -> Unit, onShowQueue: () -> Unit, onShowDetails: () -> Unit, onShowShare: () -> Unit, onShowSpeed: () -> Unit, onShowTimer: () -> Unit, onShowBookmark: () -> Unit) {
    val currentItem = state.currentMediaItem
    var pressedArea by remember { mutableStateOf<CoverTapArea?>(null) }
    
    Row(modifier = Modifier.fillMaxSize().padding(16.dp).statusBarsPadding().navigationBarsPadding()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            with(sharedTransitionScope) {
                if (currentItem?.mediaMetadata?.artworkUri != null) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(currentItem.mediaMetadata.artworkUri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize().sharedElement(rememberSharedContentState(key = "${from}_cover_${currentItem.mediaId}"), animatedVisibilityScope = animatedVisibilityScope), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().sharedElement(rememberSharedContentState(key = "${from}_cover_${currentItem?.mediaId}"), animatedVisibilityScope = animatedVisibilityScope)) {
                        BookPlaceholder(title = currentItem?.mediaMetadata?.title?.toString() ?: "A", modifier = Modifier.fillMaxSize())
                    }
                }
            }
            // Touch controls overlay
            CoverTouchControls(
                modifier = Modifier.fillMaxSize(),
                pressedArea = pressedArea,
                onAreaPressed = { pressedArea = it },
                onAreaReleased = { pressedArea = null },
                onLeftTap = { viewModel.skipBackward(Constants.SKIP_BACKWARD_MS) },
                onCenterTap = { viewModel.togglePlayPause() },
                onRightTap = { viewModel.skipForward(Constants.SKIP_FORWARD_MS) }
            )
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
        }
        Spacer(Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { viewModel.toggleFavorite() }) { Icon(if (state.isFavorite) Icons.Filled.Favorite else Icons.Default.Favorite, contentDescription = stringResource(R.string.favourites_btn), tint = if (state.isFavorite) Color.Red else LocalContentColor.current) }
                IconButton(onClick = onShowHistory) { Icon(Icons.Default.History, null) }
                IconButton(onClick = onShowQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null) }
                IconButton(onClick = onShowBookmark) { Icon(Icons.Default.Bookmark, null) }
                IconButton(onClick = onShowDetails) { Icon(Icons.Default.Info, null) }
                IconButton(onClick = onShowShare) { Icon(Icons.Default.Share, null) }
                if (com.raulburgosmurray.musicplayer.FeatureFlags.P2P_TRANSFER) {
                    IconButton(onClick = { currentItem?.mediaId?.let { onTransferClick(it) } }) { Icon(Icons.Default.Wifi, null) }
                }
            }
            PlayerControls(state, viewModel, onShowSpeed, onShowTimer)
        }
    }
}

@Composable
fun PlayerControls(state: PlaybackUiState, viewModel: PlaybackViewModel, onShowSpeed: () -> Unit, onShowTimer: () -> Unit) {
    val currentItem = state.currentMediaItem
    val isPlaying = state.isPlaying
    val progress = if (state.duration > 0) state.currentPosition.toFloat() / state.duration.toFloat() else 0f
    val duration = state.duration
    val position = state.currentPosition
    val activeTimerMinutes = state.sleepTimerMinutes
    val showUndoButton = state.lastPositionBeforeSeek != null

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = currentItem?.mediaMetadata?.title?.toString() ?: stringResource(R.string.unknown_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = currentMediaItemArtist(currentItem), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp))
        Slider(value = progress.coerceIn(0f, 1f), onValueChange = { viewModel.seekTo((it * duration).toLong()) }, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatDuration(position), style = MaterialTheme.typography.labelSmall)
            Text(text = formatDuration(state.duration), style = MaterialTheme.typography.labelSmall)
        }
        if (showUndoButton) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(
                    onClick = { viewModel.undoSeek() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Deshacer", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.skipBackward(Constants.SKIP_BACKWARD_MS) }) { Icon(painter = painterResource(id = R.drawable.rewind_30), null, modifier = Modifier.size(32.dp)) }
            IconButton(onClick = { viewModel.skipBackward(Constants.SKIP_FORWARD_MS) }) { Icon(Icons.Default.Replay10, null, modifier = Modifier.size(40.dp)) }
            FilledIconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(20.dp)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(32.dp)) }
            IconButton(onClick = { viewModel.skipForward(Constants.SKIP_FORWARD_MS) }) { Icon(Icons.Default.Forward10, null, modifier = Modifier.size(40.dp)) }
            IconButton(onClick = { viewModel.skipForward(Constants.SKIP_BACKWARD_MS) }) { Icon(painter = painterResource(id = R.drawable.fast_forward_10), null, modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(onClick = onShowSpeed, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.height(52.dp).weight(1f)) {
                Box(contentAlignment = Alignment.Center) { Text("${state.playbackSpeed}x", fontWeight = FontWeight.Bold) }
            }
            Surface(onClick = onShowTimer, shape = RoundedCornerShape(16.dp), color = if (activeTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(52.dp).weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (activeTimerMinutes > 0) "${activeTimerMinutes}m" else stringResource(R.string.timer_btn), fontWeight = FontWeight.Bold)
                    if (state.isShakeWaiting) Text(stringResource(R.string.shake_visual_prompt), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun currentMediaItemArtist(item: androidx.media3.common.MediaItem?): String {
    return item?.mediaMetadata?.artist?.toString() ?: stringResource(R.string.unknown_artist)
}

@Composable
fun SpeedSelectorContent(currentSpeed: Float, onSpeedSelected: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text(stringResource(R.string.playback_speed_selector), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            items(speeds) { speed ->
                val isSelected = speed == currentSpeed
                Surface(onClick = { onSpeedSelected(speed) }, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(60.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("${speed}x", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun TimerSelectorContent(activeTimerMinutes: Int, onTimerSelected: (Int) -> Unit, onCancelTimer: () -> Unit) {
    val options = listOf(5, 10, 15, 30, 45, 60)
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text(stringResource(R.string.sleep_timer_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            items(options) { mins ->
                val isSelected = mins == activeTimerMinutes
                Surface(onClick = { onTimerSelected(mins) }, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(60.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("${mins}m", fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (activeTimerMinutes > 0) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCancelTimer, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.stop_sleep_timer), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun HistorySelectorContent(history: List<com.raulburgosmurray.musicplayer.HistoryAction>, onActionSelected: (com.raulburgosmurray.musicplayer.HistoryAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text(stringResource(R.string.activity_history_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (history.isEmpty()) { Text(stringResource(R.string.no_activity_yet), color = MaterialTheme.colorScheme.secondary) }
        else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { action ->
                    Card(onClick = { onActionSelected(action) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) { Text(action.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge); Text(formatDuration(action.audioPositionMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                            Icon(Icons.AutoMirrored.Filled.Undo, null, tint = MaterialTheme.colorScheme.primary)
}
    }
}
            }
        }
    }
}

@Composable
fun QueueSelectorContent(playlist: List<androidx.media3.common.MediaItem>, currentIndex: Int, onItemClicked: (Int) -> Unit, onRemoveItem: (Int) -> Unit, onShowDetails: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text(stringResource(R.string.playback_queue_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f, false).heightIn(max = 400.dp)) {
            items(playlist.size) { index ->
                val item = playlist[index]
                Surface(onClick = { onItemClicked(index) }, color = if (index == currentIndex) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", modifier = Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall)
                        Column(modifier = Modifier.weight(1f)) { Text(item.mediaMetadata.title?.toString() ?: stringResource(R.string.unknown_title), fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal); Text(item.mediaMetadata.artist?.toString() ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodySmall) }
                        IconButton(onClick = { onRemoveItem(index) }) { Icon(Icons.Default.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkSelectorContent(bookmarks: List<com.raulburgosmurray.musicplayer.data.Bookmark>, onBookmarkSelected: (com.raulburgosmurray.musicplayer.data.Bookmark) -> Unit, onDeleteBookmark: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringResource(R.string.manual_bookmarks), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (bookmarks.isEmpty()) { Text(stringResource(R.string.no_bookmarks_yet), color = MaterialTheme.colorScheme.secondary) }
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
        Text(stringResource(R.string.chapters), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (chapters.isEmpty()) { Text(stringResource(R.string.no_chapters_detected), color = MaterialTheme.colorScheme.secondary) }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.add_bookmark)) },
        text = { Column { Text(stringResource(R.string.save_position_label, formatDuration(currentPosition))); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.note_label)) }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { onConfirm(note) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

enum class CoverTapArea { LEFT, CENTER, RIGHT }

@Composable
fun CoverTouchControls(
    modifier: Modifier = Modifier,
    pressedArea: CoverTapArea?,
    onAreaPressed: (CoverTapArea) -> Unit,
    onAreaReleased: () -> Unit,
    onLeftTap: () -> Unit,
    onCenterTap: () -> Unit,
    onRightTap: () -> Unit
) {
    Box(modifier = modifier.pointerInput(Unit) {
        detectTapGestures(
            onPress = { offset ->
                val width = size.width
                val x = offset.x
                val area = when {
                    x < width * 0.33f -> CoverTapArea.LEFT
                    x < width * 0.67f -> CoverTapArea.CENTER
                    else -> CoverTapArea.RIGHT
                }
                onAreaPressed(area)
                tryAwaitRelease()
                onAreaReleased()
            },
            onTap = { offset ->
                val width = size.width
                val x = offset.x
                when {
                    x < width * 0.33f -> onLeftTap()
                    x < width * 0.67f -> onCenterTap()
                    else -> onRightTap()
                }
            }
        )
    }) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left area indicator
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (pressedArea == CoverTapArea.LEFT) 
                            Color.White.copy(alpha = 0.3f) 
                        else 
                            Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rewind_30),
                    contentDescription = "Retroceder 30s",
                    tint = Color.White.copy(alpha = if (pressedArea == CoverTapArea.LEFT) 1f else 0.5f),
                    modifier = Modifier.size(48.dp)
                )
            }
            
            // Center area indicator
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (pressedArea == CoverTapArea.CENTER) 
                            Color.White.copy(alpha = 0.3f) 
                        else 
                            Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Pausar/Reproducir",
                    tint = Color.White.copy(alpha = if (pressedArea == CoverTapArea.CENTER) 1f else 0.5f),
                    modifier = Modifier.size(64.dp)
                )
            }
            
            // Right area indicator
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (pressedArea == CoverTapArea.RIGHT) 
                            Color.White.copy(alpha = 0.3f) 
                        else 
                            Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.fast_forward_30),
                    contentDescription = "Adelantar 30s",
                    tint = Color.White.copy(alpha = if (pressedArea == CoverTapArea.RIGHT) 1f else 0.5f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
