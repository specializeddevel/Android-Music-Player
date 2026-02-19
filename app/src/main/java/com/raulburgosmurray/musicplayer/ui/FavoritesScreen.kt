package com.raulburgosmurray.musicplayer.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulburgosmurray.musicplayer.Music

import androidx.compose.ui.res.stringResource
import com.raulburgosmurray.musicplayer.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FavoritesScreen(
    mainViewModel: MainViewModel,
    playbackViewModel: PlaybackViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onBookClick: (Music) -> Unit,
    onMiniPlayerClick: () -> Unit
) {
    val favoriteBooks by mainViewModel.favoriteBooks.collectAsState()
    val bookProgress by mainViewModel.bookProgress.collectAsState()
    val playbackState by playbackViewModel.uiState.collectAsState()

    var selectedBookForDetails by remember { mutableStateOf<Music?>(null) }
    val detailsSheetState = rememberModalBottomSheetState()
    var showDetailsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.my_favorites), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_btn))
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
        if (favoriteBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Favorite, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_favorites_yet),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoriteBooks) { book ->
                    with(sharedTransitionScope) {
                        BookListItem(
                            book = book,
                            isFavorite = true,
                            progress = bookProgress[book.id] ?: 0f,
                            keyPrefix = "fav",
                            animatedVisibilityScope = animatedVisibilityScope,
                            onAddToQueue = { playbackViewModel.addToQueue(book) },
                            onLongClick = {
                                selectedBookForDetails = book
                                showDetailsSheet = true
                            },
                            onClick = { onBookClick(book) }
                        )
                    }
                }
            }
        }
    }

    if (showDetailsSheet && selectedBookForDetails != null) {
        ModalBottomSheet(
            onDismissRequest = { showDetailsSheet = false },
            sheetState = detailsSheetState
        ) {
            BookDetailsContent(book = selectedBookForDetails!!, allBooks = favoriteBooks)
        }
    }
}
