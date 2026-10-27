package com.raulburgosmurray.musicplayer.ui

import android.net.Uri
import android.content.res.Configuration
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.raulburgosmurray.musicplayer.Music
import com.raulburgosmurray.musicplayer.R
import com.raulburgosmurray.musicplayer.Constants
import com.raulburgosmurray.musicplayer.EqPreset
import com.raulburgosmurray.musicplayer.encodeBookId
import com.raulburgosmurray.musicplayer.ui.PlaybackUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

internal fun parseSeekTimeMs(
    hoursText: String,
    minutesText: String,
    secondsText: String,
    duration: Long
): Long? {
    if (duration < 0L) return null
    fun parsePart(value: String): Long? = if (value.isBlank()) 0L else value.toLongOrNull()
    val hours = parsePart(hoursText) ?: return null
    val minutes = parsePart(minutesText) ?: return null
    val seconds = parsePart(secondsText) ?: return null
    if (hours < 0L || minutes !in 0L..59L || seconds !in 0L..59L) return null

    val positionMs = runCatching {
        val hoursInSeconds = Math.multiplyExact(hours, 3_600L)
        val withMinutes = Math.addExact(hoursInSeconds, Math.multiplyExact(minutes, 60L))
        Math.multiplyExact(Math.addExact(withMinutes, seconds), 1_000L)
    }.getOrNull() ?: return null

    return positionMs.takeIf { it in 0L..duration }
}

internal fun validatedSkipAmountMs(
    currentPosition: Long,
    duration: Long,
    amount: Long,
    unitMultiplier: Long,
    isForward: Boolean
): Long? {
    if (amount <= 0L || unitMultiplier <= 0L || currentPosition !in 0L..duration) return null
    val amountMs = runCatching { Math.multiplyExact(amount, unitMultiplier) }.getOrNull() ?: return null
    val targetPosition = runCatching {
        if (isForward) Math.addExact(currentPosition, amountMs)
        else Math.subtractExact(currentPosition, amountMs)
    }.getOrNull() ?: return null
    return amountMs.takeIf { targetPosition in 0L..duration }
}


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
    
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val shouldUseLandscapeLayout = isLandscape

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
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkPositionAtCreation by remember { mutableStateOf(0L) }
    val eqSheetState = rememberModalBottomSheetState()
    var showEqSheet by remember { mutableStateOf(false) }

    var showShareFileConfirmation by remember { mutableStateOf(false) }
    var showSeekToTimeDialog by remember { mutableStateOf(false) }
    var showSkipByAmountDialog by remember { mutableStateOf(false) }
    var isLocked by rememberSaveable { mutableStateOf(false) }

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (shouldUseLandscapeLayout) {
                LandscapePlayerContent(
                    state = state, viewModel = viewModel, sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope, from = from, onBack = onBack,
                    onTransferClick = onTransferClick, onShowHistory = { showHistorySheet = true },
                    onShowQueue = { showQueueSheet = true }, onShowDetails = { showDetailsSheet = true },
                    onShowShare = { showShareFileConfirmation = true }, onShowSpeed = { showSpeedSheet = true },
                    onShowTimer = { showTimerSheet = true }, onShowBookmark = { showBookmarkSheet = true },
                    onShowEqualizer = { showEqSheet = true }, onShowSeekToTime = { showSeekToTimeDialog = true },
                    onShowSkipByAmount = { showSkipByAmountDialog = true },
                    onLock = { isLocked = true }
                )
            } else {
                PortraitPlayerContent(
                    state = state, viewModel = viewModel, sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope, from = from, onBack = onBack,
                    onTransferClick = onTransferClick, onShowHistory = { showHistorySheet = true },
                    onShowQueue = { showQueueSheet = true }, onShowDetails = { showDetailsSheet = true },
                    onShowShare = { showShareFileConfirmation = true }, onShowSpeed = { showSpeedSheet = true },
                    onShowTimer = { showTimerSheet = true }, onShowBookmark = { showBookmarkSheet = true },
                    onShowEqualizer = { showEqSheet = true }, onShowSeekToTime = { showSeekToTimeDialog = true },
                    onShowSkipByAmount = { showSkipByAmountDialog = true },
                    onLock = { isLocked = true }
                )
            }
            LockOverlay(
                isLocked = isLocked,
                onUnlock = { isLocked = false }
            )
        }
    }

    if (showSpeedSheet) { ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, sheetState = speedSheetState) { SpeedSelectorContent(currentSpeed = state.playbackSpeed, currentPitch = state.pitch, onSpeedSelected = { viewModel.setPlaybackSpeed(it); showSpeedSheet = false }, onPitchSelected = { viewModel.setPitch(it) }) } }
    if (showTimerSheet) { ModalBottomSheet(onDismissRequest = { showTimerSheet = false }, sheetState = timerSheetState) { TimerSelectorContent(activeTimerMinutes = state.sleepTimerMinutes, onTimerSelected = { viewModel.startSleepTimer(it); showTimerSheet = false }, onCancelTimer = { viewModel.cancelSleepTimer(); showTimerSheet = false }) } }
    if (showHistorySheet) { ModalBottomSheet(onDismissRequest = { showHistorySheet = false }, sheetState = historySheetState) { HistorySelectorContent(history = state.history, onActionSelected = { viewModel.seekTo(it.audioPositionMs); showHistorySheet = false }) } }
    if (showQueueSheet) { ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, sheetState = queueSheetState) { QueueSelectorContent(playlist = state.playlist, currentIndex = state.currentIndex, onItemClicked = { index -> viewModel.skipToQueueItem(index); showQueueSheet = false }, onRemoveItem = { viewModel.removeItemFromQueue(it) }, onShowDetails = { showDetailsSheet = true }) } }
    if (showDetailsSheet && state.currentMusicDetails != null) { ModalBottomSheet(onDismissRequest = { showDetailsSheet = false }, sheetState = detailsSheetState) { BookDetailsContent(book = state.currentMusicDetails!!, allBooks = emptyList(), onEditMetadata = { bookId -> navController.navigate("metadata_editor?bookId=${encodeBookId(bookId)}") }) } }
    if (showBookmarkSheet) { ModalBottomSheet(onDismissRequest = { showBookmarkSheet = false }, sheetState = bookmarkSheetState) { BookmarkSelectorContent(bookmarks = state.bookmarks, onBookmarkSelected = { viewModel.seekTo(it.position); showBookmarkSheet = false }, onDeleteBookmark = { id -> viewModel.deleteBookmark(id) }, onAddBookmark = { bookmarkPositionAtCreation = state.currentPosition; showAddBookmarkDialog = true }) } }
    if (showEqSheet) { ModalBottomSheet(onDismissRequest = { showEqSheet = false }, sheetState = eqSheetState) { EqualizerSelectorContent(currentPreset = state.eqPreset, isAvailable = state.eqAvailable, onPresetSelected = { viewModel.setEqPreset(it); showEqSheet = false }) } }
    if (showAddBookmarkDialog) { AddBookmarkDialog(currentPosition = bookmarkPositionAtCreation, onDismiss = { showAddBookmarkDialog = false }, onConfirm = { note -> viewModel.addBookmark(note, bookmarkPositionAtCreation); showAddBookmarkDialog = false }) }
    if (showSeekToTimeDialog) { SeekToTimeDialog(duration = state.duration, onDismiss = { showSeekToTimeDialog = false }, onConfirm = { pos -> viewModel.seekTo(pos); showSeekToTimeDialog = false }) }
    if (showSkipByAmountDialog) { SkipByAmountDialog(currentPosition = state.currentPosition, duration = state.duration, onDismiss = { showSkipByAmountDialog = false }, onConfirm = { amountMs, isForward -> viewModel.skipByAmount(amountMs, isForward); showSkipByAmountDialog = false }) }
    if (showShareFileConfirmation) {
        ShareFileConfirmationDialog(
            onDismiss = { showShareFileConfirmation = false },
            onConfirm = {
                showShareFileConfirmation = false
                viewModel.shareFile(context)
            }
        )
    }
}

@Composable
fun LockOverlay(
    isLocked: Boolean,
    onUnlock: () -> Unit
) {
    if (!isLocked) return

    BackHandler(enabled = true) {
        // Intercept system back gestures while screen is locked
    }

    val haptic = LocalHapticFeedback.current
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = if (isHolding) holdProgress else 0f,
        animationSpec = if (isHolding) tween(durationMillis = 16, easing = LinearEasing) else spring(),
        label = "holdProgress"
    )

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            val requiredHoldMs = 1200L
            while (isHolding) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / requiredHoldMs).coerceAtMost(1f)
                if (holdProgress >= 1f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlock()
                    isHolding = false
                    holdProgress = 0f
                    break
                }
                delay(16L)
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            isHolding = true
                            do {
                                val event = awaitPointerEvent()
                                val isReleased = event.changes.all { !it.pressed }
                            } while (!isReleased)
                            isHolding = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    shadowElevation = 8.dp
                ) {}

                if (animatedProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 6.dp,
                        trackColor = Color.Transparent
                    )
                }

                Icon(
                    imageVector = if (animatedProgress > 0.5f) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = if (animatedProgress > 0f) MaterialTheme.colorScheme.primary else Color.White
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.screen_locked),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.hold_to_unlock),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SynopsisAccordion(
    description: String,
    initiallyExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Subject,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.synopsis_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun androidx.compose.animation.SharedTransitionScope.PlayerArtwork(
    state: PlaybackUiState,
    title: String,
    from: String,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope
) {
    val artworkUri = state.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
    var imageLoadError by remember(artworkUri) { mutableStateOf(false) }
    val context = LocalContext.current
    val request = remember(artworkUri, context) {
        ImageRequest.Builder(context)
            .data(artworkUri)
            .placeholder(R.drawable.ic_audiobook_cover)
            .listener(onError = { _, _ -> imageLoadError = true })
            .build()
    }
    Box(modifier = Modifier.fillMaxSize().sharedBookCover(this, "${from}_cover_${state.currentMediaItem?.mediaId}", animatedVisibilityScope)) {
        if (!artworkUri.isNullOrBlank() && !imageLoadError) {
            AsyncImage(model = request, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BookPlaceholder(title = title, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PortraitPlayerContent(state: PlaybackUiState, viewModel: PlaybackViewModel, sharedTransitionScope: androidx.compose.animation.SharedTransitionScope, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, from: String, onBack: () -> Unit, onTransferClick: (String) -> Unit, onShowHistory: () -> Unit, onShowQueue: () -> Unit, onShowDetails: () -> Unit, onShowShare: () -> Unit, onShowSpeed: () -> Unit, onShowTimer: () -> Unit, onShowBookmark: () -> Unit, onShowEqualizer: () -> Unit, onShowSeekToTime: () -> Unit, onShowSkipByAmount: () -> Unit, onLock: () -> Unit) {
    val currentItem = state.currentMediaItem
    val context = LocalContext.current
    val mediaId = currentItem?.mediaId
    val metadata by produceState<com.raulburgosmurray.musicplayer.data.AudioMetadata?>(initialValue = null, key1 = mediaId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mediaId?.let { com.raulburgosmurray.musicplayer.data.MetadataJsonHelper.loadMetadata(context, it) }
        }
    }
    val displayTitle = metadata?.title?.takeIf { it.isNotBlank() } ?: currentItem?.mediaMetadata?.title?.toString() ?: "A"
    var pressedArea by remember { mutableStateOf<CoverTapArea?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).statusBarsPadding().navigationBarsPadding()
    ) {
        val maxCoverHeight = (maxHeight * 0.40f).coerceAtMost(maxWidth)
        val scrollState = rememberScrollState()
        val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }
        var showScrollHint by remember { mutableStateOf(true) }

        LaunchedEffect(canScrollForward) {
            if (canScrollForward) {
                showScrollHint = true
                delay(3000)
                showScrollHint = false
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_btn)) }
                    Row {
                        IconButton(onClick = { viewModel.toggleFavorite() }) { Icon(if (state.isFavorite) Icons.Filled.Favorite else Icons.Default.Favorite, contentDescription = stringResource(R.string.favourites_btn), tint = if (state.isFavorite) Color.Red else LocalContentColor.current) }
                        IconButton(onClick = onShowQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = stringResource(R.string.playback_queue_title)) }
                        IconButton(onClick = onShowEqualizer) { Icon(Icons.Default.Equalizer, contentDescription = stringResource(R.string.equalizer_title), tint = if (state.eqPreset != EqPreset.FLAT) Color.Red else LocalContentColor.current) }
                        IconButton(onClick = onLock) { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.lock_screen)) }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.manual_bookmarks)) }, leadingIcon = { Icon(Icons.Default.Bookmark, null) }, onClick = { showMoreMenu = false; onShowBookmark() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.activity_history_title)) }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { showMoreMenu = false; onShowHistory() })
                                if (state.canReturnToTimerStart) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.return_to_timer_start)) },
                                        leadingIcon = { Icon(Icons.Default.Restore, null) },
                                        onClick = { showMoreMenu = false; viewModel.returnToTimerStart() }
                                    )
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.go_to_time)) }, leadingIcon = { Icon(Icons.Default.AccessTime, null) }, onClick = { showMoreMenu = false; onShowSeekToTime() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.skip_by_amount)) }, leadingIcon = { Icon(Icons.Default.SkipNext, null) }, onClick = { showMoreMenu = false; onShowSkipByAmount() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.book_details)) }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { showMoreMenu = false; onShowDetails() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.share_btn)) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showMoreMenu = false; onShowShare() })
                                if (com.raulburgosmurray.musicplayer.FeatureFlags.P2P_TRANSFER) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.send)) }, leadingIcon = { Icon(Icons.Default.Wifi, null) }, onClick = { showMoreMenu = false; currentItem?.mediaId?.let { onTransferClick(it) } })
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = maxCoverHeight)) {
                    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).align(Alignment.Center).clip(BookCoverShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        with(sharedTransitionScope) {
                            PlayerArtwork(state, displayTitle, from, animatedVisibilityScope)
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
                }
                Spacer(Modifier.height(20.dp))
                PlayerControls(state, viewModel, onShowSpeed, onShowTimer)
                val synopsis = state.currentMusicDetails?.description
                if (!synopsis.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    SynopsisAccordion(description = synopsis)
                }
                Spacer(Modifier.height(16.dp))
            }

            // A small hint in the side gutter leaves the controls unobscured.
            AnimatedVisibility(
                visible = canScrollForward && showScrollHint,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 22.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.scroll_down_hint),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun LandscapePlayerContent(state: PlaybackUiState, viewModel: PlaybackViewModel, sharedTransitionScope: androidx.compose.animation.SharedTransitionScope, animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope, from: String, onBack: () -> Unit, onTransferClick: (String) -> Unit, onShowHistory: () -> Unit, onShowQueue: () -> Unit, onShowDetails: () -> Unit, onShowShare: () -> Unit, onShowSpeed: () -> Unit, onShowTimer: () -> Unit, onShowBookmark: () -> Unit, onShowEqualizer: () -> Unit, onShowSeekToTime: () -> Unit, onShowSkipByAmount: () -> Unit, onLock: () -> Unit) {
    val currentItem = state.currentMediaItem
    val context = LocalContext.current
    val mediaId = currentItem?.mediaId
    val metadata by produceState<com.raulburgosmurray.musicplayer.data.AudioMetadata?>(initialValue = null, key1 = mediaId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mediaId?.let { com.raulburgosmurray.musicplayer.data.MetadataJsonHelper.loadMetadata(context, it) }
        }
    }
    val displayTitle = metadata?.title?.takeIf { it.isNotBlank() } ?: currentItem?.mediaMetadata?.title?.toString() ?: "A"
    var pressedArea by remember { mutableStateOf<CoverTapArea?>(null) }
    
    Row(modifier = Modifier.fillMaxSize().padding(16.dp).statusBarsPadding().navigationBarsPadding()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(BookCoverShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            with(sharedTransitionScope) {
                PlayerArtwork(state, displayTitle, from, animatedVisibilityScope)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleFavorite() }) { Icon(if (state.isFavorite) Icons.Filled.Favorite else Icons.Default.Favorite, contentDescription = stringResource(R.string.favourites_btn), tint = if (state.isFavorite) Color.Red else LocalContentColor.current) }
                IconButton(onClick = onShowQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = stringResource(R.string.playback_queue_title)) }
                IconButton(onClick = onShowEqualizer) { Icon(Icons.Default.Equalizer, contentDescription = stringResource(R.string.equalizer_title), tint = if (state.eqPreset != EqPreset.FLAT) Color.Red else LocalContentColor.current) }
                IconButton(onClick = onLock) { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.lock_screen)) }
                Box {
                    var showMoreMenuLandscape by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMoreMenuLandscape = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = showMoreMenuLandscape, onDismissRequest = { showMoreMenuLandscape = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.manual_bookmarks)) }, leadingIcon = { Icon(Icons.Default.Bookmark, null) }, onClick = { showMoreMenuLandscape = false; onShowBookmark() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.activity_history_title)) }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { showMoreMenuLandscape = false; onShowHistory() })
                        if (state.canReturnToTimerStart) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.return_to_timer_start)) },
                                leadingIcon = { Icon(Icons.Default.Restore, null) },
                                onClick = { showMoreMenuLandscape = false; viewModel.returnToTimerStart() }
                            )
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.go_to_time)) }, leadingIcon = { Icon(Icons.Default.AccessTime, null) }, onClick = { showMoreMenuLandscape = false; onShowSeekToTime() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.skip_by_amount)) }, leadingIcon = { Icon(Icons.Default.SkipNext, null) }, onClick = { showMoreMenuLandscape = false; onShowSkipByAmount() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.book_details)) }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { showMoreMenuLandscape = false; onShowDetails() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_btn)) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showMoreMenuLandscape = false; onShowShare() })
                        if (com.raulburgosmurray.musicplayer.FeatureFlags.P2P_TRANSFER) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.send)) }, leadingIcon = { Icon(Icons.Default.Wifi, null) }, onClick = { showMoreMenuLandscape = false; currentItem?.mediaId?.let { onTransferClick(it) } })
                        }
                    }
                }
            }
            PlayerControls(state, viewModel, onShowSpeed, onShowTimer)
            val synopsis = state.currentMusicDetails?.description
            if (!synopsis.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                SynopsisAccordion(description = synopsis)
            }
        }
    }
}

@Composable
fun PlayerControls(state: PlaybackUiState, viewModel: PlaybackViewModel, onShowSpeed: () -> Unit, onShowTimer: () -> Unit) {
    val currentItem = state.currentMediaItem
    val context = LocalContext.current
    val mediaId = currentItem?.mediaId
    val metadata by produceState<com.raulburgosmurray.musicplayer.data.AudioMetadata?>(initialValue = null, key1 = mediaId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mediaId?.let { com.raulburgosmurray.musicplayer.data.MetadataJsonHelper.loadMetadata(context, it) }
        }
    }
    val displayTitle = metadata?.title?.takeIf { it.isNotBlank() } ?: currentItem?.mediaMetadata?.title?.toString() ?: stringResource(R.string.unknown_title)
    
    val isPlaying = state.playWhenReady
    val progress = if (state.duration > 0) state.currentPosition.toFloat() / state.duration.toFloat() else 0f
    val duration = state.duration
    var isSeeking by rememberSaveable(mediaId) { mutableStateOf(false) }
    var pendingSeekFraction by rememberSaveable(mediaId) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    LaunchedEffect(progress, isSeeking) {
        if (!isSeeking) pendingSeekFraction = progress.coerceIn(0f, 1f)
    }
    val displayedProgress = if (isSeeking) pendingSeekFraction else progress.coerceIn(0f, 1f)
    val position = if (isSeeking) {
        seekPositionForFraction(displayedProgress, duration)
    } else {
        state.currentPosition
    }
    val activeTimerMinutes = state.sleepTimerMinutes
    val showUndoButton = state.lastPositionBeforeSeek != null

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = displayTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(text = currentMediaItemArtist(currentItem), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Slider(
            value = displayedProgress,
            onValueChange = {
                isSeeking = true
                pendingSeekFraction = it.coerceIn(0f, 1f)
            },
            onValueChangeFinished = {
                seekPositionForFractionOrNull(pendingSeekFraction, duration)?.let(viewModel::seekTo)
                isSeeking = false
            },
            enabled = duration > 0L,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatDuration(position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showUndoButton) {
                TextButton(
                    onClick = { viewModel.undoSeek() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.undo), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(text = formatDuration(state.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.skipBackward(Constants.SKIP_FORWARD_MS) }) { Icon(Icons.Default.Replay10, stringResource(R.string.history_skip_backward, Constants.SKIP_FORWARD_MS / 1000), modifier = Modifier.size(40.dp)) }
            FilledIconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(20.dp)) {
                if (state.isBuffering && state.playWhenReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.pause_play_btn), modifier = Modifier.size(32.dp))
                }
            }
            IconButton(onClick = { viewModel.skipForward(Constants.SKIP_FORWARD_MS) }) { Icon(Icons.Default.Forward10, stringResource(R.string.history_skip_forward, Constants.SKIP_FORWARD_MS / 1000), modifier = Modifier.size(40.dp)) }
        }
        if (state.playbackError != null) {
            Text(
                text = stringResource(R.string.playback_error_retry),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(onClick = onShowSpeed, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.heightIn(min = 52.dp).weight(1f)) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text("${state.playbackSpeed}x · ${state.pitch}p", fontWeight = FontWeight.Bold)
                }
            }
            Surface(onClick = onShowTimer, shape = RoundedCornerShape(16.dp), color = if (activeTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (activeTimerMinutes > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.heightIn(min = 52.dp).weight(1f)) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
private fun PlayerSheetHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(11.dp).size(26.dp)
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlayerSheetEmptyState(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).size(30.dp)
            )
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SpeedSelectorContent(currentSpeed: Float, currentPitch: Float, onSpeedSelected: (Float) -> Unit, onPitchSelected: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f)
    val pitches = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f)
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp).padding(bottom = 16.dp)) {
        PlayerSheetHeader(Icons.Default.Speed, stringResource(R.string.playback_speed_selector))
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(speeds) { speed ->
                val isSelected = speed == currentSpeed
                Surface(onClick = { onSpeedSelected(speed) }, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.medium, modifier = Modifier.height(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("${speed}x", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.pitch_selector), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(pitches) { pitch ->
                val isSelected = pitch == currentPitch
                Surface(onClick = { onPitchSelected(pitch) }, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.medium, modifier = Modifier.height(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("${pitch}x", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
fun TimerSelectorContent(activeTimerMinutes: Int, onTimerSelected: (Int) -> Unit, onCancelTimer: () -> Unit) {
    val options = listOf(5, 10, 15, 30, 45, 60)
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp).padding(bottom = 16.dp)) {
        PlayerSheetHeader(Icons.Default.Bedtime, stringResource(R.string.sleep_timer_title))
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            items(options) { mins ->
                val isSelected = mins == activeTimerMinutes
                Surface(onClick = { onTimerSelected(mins) }, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.large, modifier = Modifier.height(60.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("${mins}m", fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (activeTimerMinutes > 0) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onCancelTimer, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), shape = MaterialTheme.shapes.large) { Text(stringResource(R.string.stop_sleep_timer), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun HistorySelectorContent(history: List<com.raulburgosmurray.musicplayer.HistoryAction>, onActionSelected: (com.raulburgosmurray.musicplayer.HistoryAction) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp).padding(bottom = 16.dp)) {
        PlayerSheetHeader(Icons.Default.History, stringResource(R.string.activity_history_title))
        Spacer(Modifier.height(16.dp))
        if (history.isEmpty()) { PlayerSheetEmptyState(Icons.Default.History, stringResource(R.string.no_activity_yet)) }
        else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { action ->
                    Card(onClick = { onActionSelected(action) }, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
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
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp).padding(bottom = 16.dp)) {
        PlayerSheetHeader(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.playback_queue_title))
        Spacer(Modifier.height(16.dp))
        if (playlist.isEmpty()) {
            PlayerSheetEmptyState(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.empty_queue))
        } else LazyColumn(modifier = Modifier.weight(1f, false).heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(playlist.size) { index ->
                val item = playlist[index]
                val itemMetadata by produceState<com.raulburgosmurray.musicplayer.data.AudioMetadata?>(initialValue = null, key1 = item.mediaId) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        item.mediaId?.let { com.raulburgosmurray.musicplayer.data.MetadataJsonHelper.loadMetadata(context, it) }
                    }
                }
                val itemTitle = capitalizeWords(itemMetadata?.title?.takeIf { it.isNotBlank() } ?: item.mediaMetadata.title?.toString() ?: stringResource(R.string.unknown_title))
                Surface(onClick = { onItemClicked(index) }, color = if (index == currentIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Text("${index + 1}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = if (index == currentIndex) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { Text(itemTitle, fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(capitalizeWords(item.mediaMetadata.artist?.toString() ?: stringResource(R.string.unknown_artist)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        IconButton(onClick = { onRemoveItem(index) }) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = stringResource(R.string.delete_book), tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkSelectorContent(bookmarks: List<com.raulburgosmurray.musicplayer.data.Bookmark>, onBookmarkSelected: (com.raulburgosmurray.musicplayer.data.Bookmark) -> Unit, onDeleteBookmark: (Int) -> Unit, onAddBookmark: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp).padding(bottom = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PlayerSheetHeader(Icons.Default.Bookmark, stringResource(R.string.manual_bookmarks)) }
            FilledIconButton(onClick = onAddBookmark) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_bookmark)) }
        }
        Spacer(Modifier.height(16.dp))
        if (bookmarks.isEmpty()) { PlayerSheetEmptyState(Icons.Default.BookmarkBorder, stringResource(R.string.no_bookmarks_yet)) }
        else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(bookmarks) { bookmark ->
                Surface(onClick = { onBookmarkSelected(bookmark) }, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) { Text(formatDuration(bookmark.position), fontWeight = FontWeight.SemiBold); if (bookmark.note.isNotEmpty()) Text(bookmark.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = { onDeleteBookmark(bookmark.id) }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_book), tint = MaterialTheme.colorScheme.error) }
                    }
                }
            } }
        }
    }
}

@Composable
fun ChapterSelectorContent(chapters: List<com.raulburgosmurray.musicplayer.Chapter>, currentPosition: Long, onChapterSelected: (com.raulburgosmurray.musicplayer.Chapter) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp).padding(bottom = 16.dp)) {
        PlayerSheetHeader(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.chapters))
        Spacer(Modifier.height(16.dp))
        if (chapters.isEmpty()) { PlayerSheetEmptyState(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.no_chapters_detected)) }
        else {
            val currentChapter = chapters.lastOrNull { it.startMs <= currentPosition }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(chapters) { chapter ->
                val isCurrent = chapter == currentChapter
                Surface(onClick = { onChapterSelected(chapter) }, color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isCurrent) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) { Text(chapter.title, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium); Text(formatDuration(chapter.startMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            } }
        }
    }
}

@Composable
fun eqPresetDisplayName(preset: EqPreset): String = when (preset) {
    EqPreset.FLAT -> stringResource(R.string.eq_preset_flat)
    EqPreset.VOICE_BOOST -> stringResource(R.string.eq_preset_voice_boost)
    EqPreset.BASS_CUT -> stringResource(R.string.eq_preset_bass_cut)
    EqPreset.CLARITY -> stringResource(R.string.eq_preset_clarity)
}

private fun eqPresetIcon(preset: EqPreset): ImageVector = when (preset) {
    EqPreset.FLAT -> Icons.Default.Tune
    EqPreset.VOICE_BOOST -> Icons.Default.RecordVoiceOver
    EqPreset.BASS_CUT -> Icons.Default.GraphicEq
    EqPreset.CLARITY -> Icons.Default.Hearing
}

@Composable
fun EqualizerSelectorContent(currentPreset: EqPreset, isAvailable: Boolean, onPresetSelected: (EqPreset) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp).size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.equalizer_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.equalizer_preset_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isAvailable) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        stringResource(R.string.equalizer_not_available),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            EqPreset.entries.chunked(2).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowPresets.forEach { preset ->
                        val selected = preset == currentPreset
                        Card(
                            onClick = { onPresetSelected(preset) },
                            modifier = Modifier.weight(1f).heightIn(min = 104.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = eqPresetIcon(preset),
                                        contentDescription = null,
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    if (selected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Text(
                                    eqPresetDisplayName(preset),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (rowPresets.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerActionDialog(
    icon: ImageVector,
    title: String,
    description: String,
    primaryLabel: String,
    primaryIcon: ImageVector,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .widthIn(max = 440.dp),
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
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp).size(30.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                content()

                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(primaryIcon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(primaryLabel)
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
private fun ShareFileConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    PlayerActionDialog(
        icon = Icons.Default.Share,
        title = stringResource(R.string.share_file_warning_title),
        description = stringResource(R.string.share_file_warning_message),
        primaryLabel = stringResource(R.string.confirm),
        primaryIcon = Icons.Default.Share,
        onPrimary = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun AddBookmarkDialog(currentPosition: Long, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    PlayerActionDialog(
        icon = Icons.Default.BookmarkAdd,
        title = stringResource(R.string.add_bookmark),
        description = stringResource(R.string.save_position_label, formatDuration(currentPosition)),
        primaryLabel = stringResource(R.string.save),
        primaryIcon = Icons.Default.Save,
        onPrimary = { onConfirm(note) },
        onDismiss = onDismiss
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDuration(currentPosition),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.note_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            shape = MaterialTheme.shapes.large
        )
    }
}

@Composable
fun SeekToTimeDialog(duration: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var hoursText by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("") }
    var secondsText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    PlayerActionDialog(
        icon = Icons.Default.Schedule,
        title = stringResource(R.string.go_to_time),
        description = stringResource(R.string.go_to_time_desc),
        primaryLabel = stringResource(R.string.go_to_time),
        primaryIcon = Icons.Default.SkipNext,
        onPrimary = {
            val inputMs = parseSeekTimeMs(hoursText, minutesText, secondsText, duration)
            if (inputMs != null) {
                error = false
                onConfirm(inputMs)
            } else {
                error = true
            }
        },
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimePartField(
                value = hoursText,
                onValueChange = { hoursText = it; error = false },
                label = stringResource(R.string.hours_hint),
                isError = error,
                modifier = Modifier.weight(1f)
            )
            TimePartField(
                value = minutesText,
                onValueChange = { minutesText = it; error = false },
                label = stringResource(R.string.minutes_hint),
                isError = error,
                maxValue = 59L,
                modifier = Modifier.weight(1f)
            )
            TimePartField(
                value = secondsText,
                onValueChange = { secondsText = it; error = false },
                label = stringResource(R.string.seconds_hint),
                isError = error,
                maxValue = 59L,
                modifier = Modifier.weight(1f)
            )
        }
        if (error) {
            DialogErrorMessage(stringResource(R.string.invalid_time))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipByAmountDialog(
    currentPosition: Long,
    duration: Long,
    onDismiss: () -> Unit,
    onConfirm: (amountMs: Long, isForward: Boolean) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val units = listOf(
        stringResource(R.string.unit_minutes) to 60_000L,
        stringResource(R.string.unit_hours) to 3_600_000L
    )
    var selectedUnitIndex by remember { mutableStateOf(0) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    var isForward by remember { mutableStateOf(true) }

    val amount = amountText.toLongOrNull()
    val unitMultiplier = units[selectedUnitIndex].second

    PlayerActionDialog(
        icon = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
        title = stringResource(R.string.skip_by_amount),
        description = stringResource(R.string.skip_by_amount_desc),
        primaryLabel = stringResource(R.string.confirm),
        primaryIcon = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
        onPrimary = {
            val validAmountMs = amount?.let {
                validatedSkipAmountMs(
                    currentPosition = currentPosition,
                    duration = duration,
                    amount = it,
                    unitMultiplier = unitMultiplier,
                    isForward = isForward
                )
            }
            if (validAmountMs != null) {
                error = false
                onConfirm(validAmountMs, isForward)
            } else {
                error = true
            }
        },
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { value ->
                    if (value.isEmpty() || value.toLongOrNull() != null) {
                        amountText = value
                        error = false
                    }
                },
                modifier = Modifier.weight(0.8f),
                singleLine = true,
                isError = error,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )
            ExposedDropdownMenuBox(
                expanded = unitMenuExpanded,
                onExpandedChange = { unitMenuExpanded = it },
                modifier = Modifier.weight(1.2f)
            ) {
                OutlinedTextField(
                    value = units[selectedUnitIndex].first,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded)
                    }
                )
                ExposedDropdownMenu(
                    expanded = unitMenuExpanded,
                    onDismissRequest = { unitMenuExpanded = false }
                ) {
                    units.forEachIndexed { index, pair ->
                        DropdownMenuItem(
                            text = { Text(pair.first) },
                            leadingIcon = {
                                Icon(
                                    if (index == 0) Icons.Default.Timer else Icons.Default.Schedule,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                selectedUnitIndex = index
                                unitMenuExpanded = false
                                error = false
                            }
                        )
                    }
                }
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !isForward,
                onClick = { isForward = false; error = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.FastRewind, contentDescription = null) },
                label = { Text(stringResource(R.string.direction_backward)) }
            )
            SegmentedButton(
                selected = isForward,
                onClick = { isForward = true; error = false },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.FastForward, contentDescription = null) },
                label = { Text(stringResource(R.string.direction_forward)) }
            )
        }

        if (error) {
            DialogErrorMessage(stringResource(R.string.invalid_skip_amount))
        }
    }
}

@Composable
private fun TimePartField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    maxValue: Long? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            val parsed = candidate.toLongOrNull()
            if (candidate.isEmpty() || (parsed != null && parsed >= 0L && (maxValue == null || parsed <= maxValue))) {
                onValueChange(candidate)
            }
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        singleLine = true,
        isError = isError,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )
    )
}

@Composable
private fun DialogErrorMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
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
                    imageVector = Icons.Default.Replay10,
                    contentDescription = stringResource(R.string.rewind_30s),
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
                    contentDescription = stringResource(R.string.pause_play_btn),
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
                    imageVector = Icons.Default.Forward10,
                    contentDescription = stringResource(R.string.fast_forward_30s),
                    tint = Color.White.copy(alpha = if (pressedArea == CoverTapArea.RIGHT) 1f else 0.5f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
