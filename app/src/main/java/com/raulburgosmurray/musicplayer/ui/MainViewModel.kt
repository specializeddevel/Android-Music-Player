package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import com.raulburgosmurray.musicplayer.Music
import com.raulburgosmurray.musicplayer.Constants
import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.CachedBook
import com.raulburgosmurray.musicplayer.data.BookRepository
import com.raulburgosmurray.musicplayer.data.FavoriteRepository
import com.raulburgosmurray.musicplayer.data.ProgressRepository
import com.raulburgosmurray.musicplayer.data.MusicScanner
import com.raulburgosmurray.musicplayer.data.toMusic
import com.raulburgosmurray.musicplayer.data.toCachedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder { TITLE, ARTIST, PROGRESS, RECENT }
enum class BookFilter { ALL, COMPLETED, IN_PROGRESS }

private data class FilterData(val filter: BookFilter, val readStatus: Map<String, Boolean>)

class MainViewModel(application: Application, settingsViewModel: SettingsViewModel) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val bookRepository = BookRepository(database.cachedBookDao())
    private val favoriteRepository = FavoriteRepository(database.favoriteDao())
    private val progressRepository = ProgressRepository(database.progressDao())
    private val musicScanner = MusicScanner()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _scanProgress = MutableStateFlow(0 to 0)
    val scanProgress = _scanProgress.asStateFlow()
    
    private val _books = bookRepository.getAllBooks()
        .map { list -> list.map { it.toMusic() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.STATEFLOW_STOP_TIMEOUT_MS), emptyList())
    
    val sortOrder = settingsViewModel.sortOrder
    private val _bookFilter = MutableStateFlow(BookFilter.ALL)
    val bookFilter = _bookFilter.asStateFlow()
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds = _favoriteIds.asStateFlow()
    private val _bookProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val bookProgress = _bookProgress.asStateFlow()
    private val _bookActivityTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
    val bookActivityTimestamps = _bookActivityTimestamps.asStateFlow()
    private val _bookReadStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val bookReadStatus = _bookReadStatus.asStateFlow()
    
    // Expose filter for UI - returns pair of filter type and read status map
    val filterAndReadStatus: StateFlow<Pair<BookFilter, Map<String, Boolean>>> = combine(_bookFilter, _bookReadStatus) { filter, readStatus -> 
        Pair(filter, readStatus) 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.STATEFLOW_STOP_TIMEOUT_MS), Pair(BookFilter.ALL, emptyMap()))

    val books: StateFlow<List<Music>> = combine(_books, sortOrder, _bookProgress, _bookActivityTimestamps, filterAndReadStatus) { books, order, progress, timestamps, filterData ->
        val (filter, readStatus) = filterData
        val filtered = when (filter) {
            BookFilter.ALL -> books
            BookFilter.COMPLETED -> books.filter { readStatus[it.id] == true }
            BookFilter.IN_PROGRESS -> books.filter { readStatus[it.id] != true }
        }
        val sorted = when (order) {
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.PROGRESS -> filtered.sortedByDescending { progress[it.id] ?: 0f }
            SortOrder.RECENT -> filtered.sortedByDescending { timestamps[it.id] ?: 0L }
        }
        sorted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.STATEFLOW_STOP_TIMEOUT_MS), emptyList())

    val favoriteBooks = combine(books, _favoriteIds) { b, f -> b.filter { f.contains(it.id) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.STATEFLOW_STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            combine(settingsViewModel.libraryRootUris, settingsViewModel.scanAllMemory) { uris, scanAll ->
                Pair(uris, scanAll)
            }.collect { (uris, scanAll) ->
                if (bookRepository.getAllBooks().first().isEmpty()) {
                    loadBooks(if (scanAll) emptyList() else uris, scanAll)
                }
            }
        }
        observeFavorites(); observeProgress()
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progressRepository.getAllProgressFlow().collect { list ->
                _bookProgress.value = list.associate { it.mediaId to if (it.duration > 0) it.lastPosition.toFloat() / it.duration.toFloat() else 0f }
                _bookActivityTimestamps.value = list.associate { it.mediaId to it.lastPauseTimestamp }
                _bookReadStatus.value = list.associate { it.mediaId to it.isRead }
            }
        }
    }

    fun toggleReadStatus(mediaId: String) {
        viewModelScope.launch {
            val currentStatus = _bookReadStatus.value[mediaId] ?: false
            progressRepository.setReadStatus(mediaId, !currentStatus)
        }
    }

    fun markAsRead(mediaId: String) {
        viewModelScope.launch {
            progressRepository.setReadStatus(mediaId, true)
        }
    }

    fun markAsUnread(mediaId: String) {
        viewModelScope.launch {
            progressRepository.setReadStatus(mediaId, false)
        }
    }

    fun setFilter(filter: BookFilter) {
        _bookFilter.value = filter
    }

    private fun observeFavorites() {
        viewModelScope.launch { favoriteRepository.getAllFavoriteIds().collectLatest { _favoriteIds.value = it.toSet() } }
    }

    fun loadBooks(libraryRootUris: List<String> = emptyList(), scanAllMemory: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            val audioFiles = withContext(Dispatchers.IO) {
                if (scanAllMemory) {
                    musicScanner.scanMediaStore(getApplication())
                } else if (libraryRootUris.isNotEmpty()) {
                    val allMusic = mutableListOf<Music>()
                    for (uri in libraryRootUris) {
                        val rootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(uri))
                        if (rootDoc != null) {
                            val music = musicScanner.scanDirectory(getApplication(), rootDoc) { processed, total ->
                                _scanProgress.value = processed to total
                            }
                            allMusic.addAll(music)
                        }
                    }
                    allMusic
                } else {
                    emptyList()
                }
            }
            withContext(Dispatchers.IO) {
                bookRepository.clearCache()
                bookRepository.saveBooks(audioFiles.map { it.toCachedBook() })
            }
            _isLoading.value = false
        }
    }
}
