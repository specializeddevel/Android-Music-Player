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
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds = _favoriteIds.asStateFlow()
    private val _bookProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val bookProgress = _bookProgress.asStateFlow()
    private val _bookActivityTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
    val bookActivityTimestamps = _bookActivityTimestamps.asStateFlow()

    val books: StateFlow<List<Music>> = combine(_books, sortOrder, _bookProgress, _bookActivityTimestamps) { books, order, progress, timestamps ->
        when (order) {
            SortOrder.TITLE -> books.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> books.sortedBy { it.artist.lowercase() }
            SortOrder.PROGRESS -> books.sortedByDescending { progress[it.id] ?: 0f }
            SortOrder.RECENT -> books.sortedByDescending { timestamps[it.id] ?: 0L }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.STATEFLOW_STOP_TIMEOUT_MS), emptyList())

    val favoriteBooks = combine(books, _favoriteIds) { b, f -> b.filter { f.contains(it.id) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.STATEFLOW_STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            settingsViewModel.libraryRootUri.collect { uri ->
                if (bookRepository.getAllBooks().first().isEmpty()) loadBooks(uri)
            }
        }
        observeFavorites(); observeProgress()
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progressRepository.getAllProgressFlow().collect { list ->
                _bookProgress.value = list.associate { it.mediaId to if (it.duration > 0) it.lastPosition.toFloat() / it.duration.toFloat() else 0f }
                _bookActivityTimestamps.value = list.associate { it.mediaId to it.lastPauseTimestamp }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch { favoriteRepository.getAllFavoriteIds().collectLatest { _favoriteIds.value = it.toSet() } }
    }

    fun loadBooks(libraryRootUri: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val audioFiles = withContext(Dispatchers.IO) {
                if (libraryRootUri != null) {
                    val rootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(libraryRootUri))
                    if (rootDoc != null) {
                        musicScanner.scanDirectory(getApplication(), rootDoc) { processed, total ->
                            _scanProgress.value = processed to total
                        }
                    } else emptyList()
                } else {
                    musicScanner.scanMediaStore(getApplication())
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
