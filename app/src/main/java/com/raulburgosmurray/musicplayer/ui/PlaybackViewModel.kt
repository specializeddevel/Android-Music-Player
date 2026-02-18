package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.raulburgosmurray.musicplayer.PlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.os.CountDownTimer

import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.FavoriteBook
import com.raulburgosmurray.musicplayer.data.Bookmark
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

import com.raulburgosmurray.musicplayer.Chapter
import com.raulburgosmurray.musicplayer.HistoryAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val currentMediaItem: MediaItem? = null,
    val playlist: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val playbackSpeed: Float = 1.0f,
    val isReady: Boolean = false,
    val isConnected: Boolean = false,
    val sleepTimerMinutes: Int = 0,
    val isFavorite: Boolean = false,
    val chapters: List<Chapter> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val lastPositionBeforeSeek: Long? = null,
    val history: List<HistoryAction> = emptyList(),
    val dominantColor: Int? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModel(application: Application) : androidx.lifecycle.AndroidViewModel(application) {

    var historyLimit: Int = 100 // Valor por defecto

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var sleepTimer: CountDownTimer? = null
    private val database = AppDatabase.getDatabase(application)

    private val _uiState = MutableStateFlow(PlaybackState())
    val uiState: StateFlow<PlaybackState> = _uiState.asStateFlow()

    private var isQueueLoaded = false
    private var pendingBooksToLoadQueue: List<com.raulburgosmurray.musicplayer.Music>? = null

    init {
        observeFavoriteStatus()
        observeBookmarks()
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            _uiState.flatMapLatest { state ->
                val mediaId = state.currentMediaItem?.mediaId
                if (mediaId != null) {
                    database.bookmarkDao().getBookmarksForMedia(mediaId)
                } else {
                    flowOf(emptyList())
                }
            }.collectLatest { bookmarkList ->
                _uiState.value = _uiState.value.copy(bookmarks = bookmarkList)
            }
        }
    }

    private fun observeFavoriteStatus() {
        viewModelScope.launch {
            _uiState.flatMapLatest { state ->
                val mediaId = state.currentMediaItem?.mediaId
                if (mediaId != null) {
                    database.favoriteDao().isFavorite(mediaId)
                } else {
                    flowOf(false)
                }
            }.collectLatest { isFav ->
                _uiState.value = _uiState.value.copy(isFavorite = isFav)
            }
        }
    }

    private fun logAction(label: String) {
        val currentPos = controller?.currentPosition ?: 0L
        val newAction = HistoryAction(label, currentPos)
        val newList = _uiState.value.history.toMutableList()
        newList.add(0, newAction)
        _uiState.value = _uiState.value.copy(history = newList.take(historyLimit))
    }

    fun toggleFavorite() {
        val currentItem = _uiState.value.currentMediaItem ?: return
        val isCurrentlyFav = _uiState.value.isFavorite
        
        viewModelScope.launch {
            if (isCurrentlyFav) {
                database.favoriteDao().removeFavorite(currentItem.mediaId)
            } else {
                database.favoriteDao().addFavorite(FavoriteBook(currentItem.mediaId))
            }
        }
    }

    fun addBookmark(note: String, position: Long) {
        val currentItem = _uiState.value.currentMediaItem ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            database.bookmarkDao().insertBookmark(
                Bookmark(
                    mediaId = currentItem.mediaId,
                    position = position,
                    note = note
                )
            )
            withContext(Dispatchers.Main) {
                logAction("Marcador añadido")
            }
        }
    }

    fun deleteBookmark(bookmarkId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            database.bookmarkDao().deleteBookmark(bookmarkId)
        }
    }

    fun updateBookmarkNote(bookmarkId: Int, newNote: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.bookmarkDao().updateBookmarkNote(bookmarkId, newNote)
        }
    }

    fun initController(context: Context) {
        if (controller != null) return
        if (controllerFuture != null && !controllerFuture!!.isDone) return

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            try {
                val newController = controllerFuture?.get()
                if (newController != null) {
                    controller = newController
                    setupController()

                    // Restaurar cola persistente si había una carga pendiente
                    pendingBooksToLoadQueue?.let {
                        loadPersistedQueue(it)
                        pendingBooksToLoadQueue = null
                    }

                    pendingPlaylist?.let { (items, index) ->
                        playPlaylist(items, index)
                        pendingPlaylist = null
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Fallo al obtener controlador", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateDominantColor(artworkUri: android.net.Uri?) {
        if (artworkUri == null) {
            _uiState.value = _uiState.value.copy(dominantColor = null)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(artworkUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    Palette.from(bitmap).generate { palette ->
                        val color = palette?.getVibrantColor(0) ?: palette?.getDominantColor(0)
                        if (color != 0 && color != null) {
                            _uiState.value = _uiState.value.copy(dominantColor = color)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(dominantColor = null)
            }
        }
    }

    private var lastScannedPath: String? = null

    private fun extractChapters(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val chaptersList = mutableListOf<Chapter>()
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Error al extraer capítulos", e)
            } finally {
                retriever.release()
            }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(chapters = chaptersList)
            }
        }
    }

    private fun updatePlaylistState() {
        val player = controller ?: return
        val items = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            items.add(player.getMediaItemAt(i))
        }
        _uiState.value = _uiState.value.copy(
            playlist = items,
            currentIndex = player.currentMediaItemIndex
        )
    }

    private fun persistQueue() {
        val player = controller ?: return
        
        // No sobreescribir la base de datos si el reproductor está vacío y aún no hemos cargado la cola inicial
        if (player.mediaItemCount == 0 && !isQueueLoaded) return

        val items = mutableListOf<com.raulburgosmurray.musicplayer.data.QueueItem>()
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            items.add(com.raulburgosmurray.musicplayer.data.QueueItem(mediaId = item.mediaId, orderIndex = i))
        }
        viewModelScope.launch(Dispatchers.IO) {
            database.queueDao().updateFullQueue(items)
        }
    }

    fun loadPersistedQueue(allBooks: List<com.raulburgosmurray.musicplayer.Music>) {
        val player = controller
        if (player == null) {
            pendingBooksToLoadQueue = allBooks
            return
        }
        
        if (player.mediaItemCount > 0) {
            isQueueLoaded = true
            return 
        }

        viewModelScope.launch {
            val savedQueue = withContext(Dispatchers.IO) { database.queueDao().getQueueSnapshot() }
            if (savedQueue.isNotEmpty()) {
                val itemsToLoad = savedQueue.mapNotNull { savedItem ->
                    allBooks.find { it.id == savedItem.mediaId }?.toMediaItem()
                }
                if (itemsToLoad.isNotEmpty()) {
                    player.setMediaItems(itemsToLoad)
                    player.prepare()
                    updatePlaylistState()
                }
            }
            isQueueLoaded = true
        }
    }

    private fun setupController() {
        val player = controller ?: return
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _uiState.value = _uiState.value.copy(
                    currentMediaItem = mediaItem,
                    currentIndex = player.currentMediaItemIndex,
                    duration = player.duration,
                    chapters = emptyList(),
                    dominantColor = null
                )
                updateDominantColor(mediaItem?.mediaMetadata?.artworkUri)
                mediaItem?.localConfiguration?.uri?.path?.let { path ->
                    if (path != lastScannedPath) {
                        extractChapters(path)
                        lastScannedPath = path
                    }
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updatePlaylistState()
                persistQueue()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.value = _uiState.value.copy(
                    isReady = playbackState == Player.STATE_READY,
                    duration = player.duration
                )
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                _uiState.value = _uiState.value.copy(currentPosition = newPosition.positionMs)
            }
        })

        _uiState.value = _uiState.value.copy(
            isConnected = true,
            isPlaying = player.isPlaying,
            currentMediaItem = player.currentMediaItem,
            duration = player.duration,
            currentPosition = player.currentPosition,
            playbackSpeed = player.playbackParameters.speed,
            isReady = player.playbackState == Player.STATE_READY
        )
        updatePlaylistState()
        updateDominantColor(player.currentMediaItem?.mediaMetadata?.artworkUri)
        if (player.isPlaying) startProgressUpdate()
    }

    fun skipToQueueItem(index: Int) {
        controller?.let {
            it.seekTo(index, 0)
            it.play()
        }
    }

    fun removeItemFromQueue(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveItemInQueue(fromIndex: Int, toIndex: Int) {
        controller?.moveMediaItem(fromIndex, toIndex)
    }

    fun addToQueue(music: com.raulburgosmurray.musicplayer.Music) {
        val player = controller ?: return
        
        // Evitar duplicados: verificar si el ID ya está en la cola actual
        val alreadyInQueue = (0 until player.mediaItemCount).any { 
            player.getMediaItemAt(it).mediaId == music.id 
        }
        
        if (!alreadyInQueue) {
            val mediaItem = music.toMediaItem()
            player.addMediaItem(mediaItem)
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                controller?.let {
                    if (it.isPlaying) {
                        _uiState.value = _uiState.value.copy(currentPosition = it.currentPosition)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdate() { progressJob?.cancel() }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                logAction("Pausa")
                it.pause()
            } else {
                logAction("Reproducir")
                it.play()
            }
        }
    }

    private fun saveCurrentPositionAsUndo() {
        controller?.let { _uiState.value = _uiState.value.copy(lastPositionBeforeSeek = it.currentPosition) }
    }

    fun undoSeek() {
        val prevPos = _uiState.value.lastPositionBeforeSeek ?: return
        val currentPos = controller?.currentPosition ?: 0L
        logAction("Deshacer salto")
        controller?.seekTo(prevPos)
        _uiState.value = _uiState.value.copy(lastPositionBeforeSeek = currentPos)
    }

    fun seekTo(position: Long) {
        saveCurrentPositionAsUndo()
        logAction("Salto manual")
        controller?.seekTo(position)
    }

    fun skipForward(millis: Long) {
        saveCurrentPositionAsUndo()
        logAction("Adelantar ${millis/1000}s")
        controller?.let { it.seekTo(it.currentPosition + millis) }
    }

    fun skipBackward(millis: Long) {
        saveCurrentPositionAsUndo()
        logAction("Retroceder ${millis/1000}s")
        controller?.let { it.seekTo(it.currentPosition - millis) }
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        _uiState.value = _uiState.value.copy(sleepTimerMinutes = minutes)
        sleepTimer = object : CountDownTimer(minutes * 60 * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minsRemaining = (millisUntilFinished / 1000 / 60).toInt() + 1
                if (_uiState.value.sleepTimerMinutes != minsRemaining) {
                    _uiState.value = _uiState.value.copy(sleepTimerMinutes = minsRemaining)
                }
            }
            override fun onFinish() { togglePlayPause(); cancelSleepTimer() }
        }.start()
    }

    fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        _uiState.value = _uiState.value.copy(sleepTimerMinutes = 0)
    }

    private var pendingPlaylist: Pair<List<MediaItem>, Int>? = null

    fun playPlaylist(mediaItems: List<MediaItem>, startIndex: Int) {
        val player = controller
        if (player != null) {
            player.stop() 
            player.setMediaItems(mediaItems, startIndex, 0)
            player.prepare()
            player.play()
        } else {
            pendingPlaylist = Pair(mediaItems, startIndex)
        }
    }

    override fun onCleared() {
        sleepTimer?.cancel()
        releaseController()
        super.onCleared()
    }

    fun releaseController() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
            controllerFuture = null
            controller = null
        }
    }
}
