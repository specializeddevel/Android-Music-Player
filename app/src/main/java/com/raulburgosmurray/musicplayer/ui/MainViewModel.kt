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
import com.raulburgosmurray.musicplayer.data.MetadataJsonHelper
import com.raulburgosmurray.musicplayer.data.toMusic
import com.raulburgosmurray.musicplayer.data.toCachedBook
import com.raulburgosmurray.musicplayer.data.withEditedMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import java.io.File
import android.os.Environment

enum class SortOrder { TITLE, ARTIST, PROGRESS, RECENT }
enum class BookFilter { ALL, LOCAL_ONLY, SERVER_ONLY, IN_PROGRESS, COMPLETED }

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
        .map { list ->
            val context = getApplication<Application>()
            list.map { cached ->
                val editedMetadata = MetadataJsonHelper.loadMetadata(context, cached.id)
                cached.withEditedMetadata(editedMetadata).toMusic()
            }
        }
        .flowOn(Dispatchers.IO)
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

    private fun deduplicateBooks(list: List<Music>): List<Music> {
        val localBooks = list.filter { !it.path.startsWith("http://") && !it.path.startsWith("https://") }
        val remoteBooks = list.filter { it.path.startsWith("http://") || it.path.startsWith("https://") }

        val localKeys = localBooks.map { normalizeBookKey(it.title, it.artist) }.toSet()

        val uniqueRemoteBooks = remoteBooks.filter { remote ->
            val remoteKey = normalizeBookKey(remote.title, remote.artist)
            !localKeys.contains(remoteKey)
        }

        return localBooks + uniqueRemoteBooks
    }

    private fun normalizeBookKey(title: String, artist: String): String {
        val cleanTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanArtist = artist.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "$cleanTitle|$cleanArtist"
    }

    val allBooksUnfiltered: StateFlow<List<Music>> = _books
        .map { deduplicateBooks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.STATEFLOW_STOP_TIMEOUT_MS), emptyList())

    val books: StateFlow<List<Music>> = combine(_books, sortOrder, _bookProgress, _bookActivityTimestamps, filterAndReadStatus) { booksList, order, progress, timestamps, filterData ->
        val deduplicated = deduplicateBooks(booksList)
        val (filter, readStatus) = filterData
        val filtered = when (filter) {
            BookFilter.ALL -> deduplicated
            BookFilter.LOCAL_ONLY -> deduplicated.filter { !it.path.startsWith("http://") && !it.path.startsWith("https://") }
            BookFilter.SERVER_ONLY -> deduplicated.filter { it.path.startsWith("http://") || it.path.startsWith("https://") || it.id.startsWith("abs_") }
            BookFilter.IN_PROGRESS -> deduplicated.filter { (progress[it.id] ?: 0f) > 0f && readStatus[it.id] != true }
            BookFilter.COMPLETED -> deduplicated.filter { readStatus[it.id] == true }
        }
        val sorted = when (order) {
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.PROGRESS -> filtered.sortedByDescending { progress[it.id] ?: 0f }
            SortOrder.RECENT -> filtered.sortedByDescending { timestamps[it.id] ?: 0L }
        }
        sorted
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.STATEFLOW_STOP_TIMEOUT_MS), emptyList())

    val favoriteBooks = combine(_books, _favoriteIds) { allBooksList, favIdsSet ->
        val deduplicated = deduplicateBooks(allBooksList)
        deduplicated.filter { favIdsSet.contains(it.id) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Constants.STATEFLOW_STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            combine(settingsViewModel.libraryRootUris, settingsViewModel.scanAllMemory) { uris, scanAll ->
                Pair(uris, scanAll)
            }.collect { (uris, scanAll) ->
                smartLoadBooks(if (scanAll) emptyList() else uris, scanAll)
            }
        }
        observeFavorites(); observeProgress()
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progressRepository.getAllProgressFlow()
                .debounce(2.seconds)
                .collect { list ->
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

    suspend fun hasCachedBooks(): Boolean = bookRepository.getBookCount() > 0

    /**
     * Smart scan that only performs a full metadata extraction when the set of
     * audio files on disk differs from what is already cached in the database.
     * Otherwise it skips scanning entirely, making startup instant.
     */
    fun smartLoadBooks(libraryRootUris: List<String> = emptyList(), scanAllMemory: Boolean = false) {
        viewModelScope.launch {
            val hasBooks = hasCachedBooks()
            if (!hasBooks) {
                // First time or empty cache -> full scan
                loadBooks(libraryRootUris, scanAllMemory)
                return@launch
            }

            // Quick check: enumerate files without extracting metadata
            _isLoading.value = true
            val currentIds = withContext(Dispatchers.IO) {
                if (scanAllMemory) {
                    musicScanner.quickCheckMediaStore(getApplication())
                } else if (libraryRootUris.isNotEmpty()) {
                    val ids = mutableSetOf<String>()
                    for (uri in libraryRootUris) {
                        val rootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(uri))
                        if (rootDoc != null) {
                            ids.addAll(musicScanner.quickCheckDirectory(getApplication(), rootDoc))
                        }
                    }
                    ids
                } else {
                    emptySet()
                }
            }

            val cachedLocalIds = withContext(Dispatchers.IO) {
                bookRepository.getAllBooks().first()
                    .filter { !it.path.startsWith("http://") && !it.path.startsWith("https://") }
                    .map { it.id }
                    .toSet()
            }

            if (currentIds != cachedLocalIds) {
                // Files added or removed -> full scan
                loadBooks(libraryRootUris, scanAllMemory)
            } else {
                // No changes -> just stop the loading indicator
                _isLoading.value = false
            }
        }
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
                val existingMapByPath = bookRepository.getAllBooks().first().associateBy { it.path }
                val scannedCachedBooks = audioFiles.map { music ->
                    val existing = existingMapByPath[music.path]
                    if (existing != null) {
                        music.toCachedBook().copy(
                            id = existing.id,
                            description = music.description ?: existing.description
                        )
                    } else {
                        music.toCachedBook()
                    }
                }

                val absRepo = com.raulburgosmurray.musicplayer.data.AudiobookshelfRepository(getApplication())
                if (absRepo.isConnected()) {
                    bookRepository.clearLocalCache()
                    bookRepository.saveBooks(scannedCachedBooks)
                    absRepo.resync()
                } else {
                    bookRepository.clearCache()
                    bookRepository.saveBooks(scannedCachedBooks)
                }
            }
            _isLoading.value = false
        }
    }

    fun scanTransferInbox() {
        viewModelScope.launch {
            _isLoading.value = true
            val audioFiles = withContext(Dispatchers.IO) {
                val dir = File(getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Litera/Inbox")
                if (dir.exists() && dir.isDirectory) {
                    val docFile = DocumentFile.fromFile(dir)
                    musicScanner.scanDirectory(getApplication(), docFile) { _, _ -> }
                } else {
                    emptyList()
                }
            }
            withContext(Dispatchers.IO) {
                // Inbox scanning is additive. Clearing here also removed every book
                // discovered through MediaStore or the user's SAF folders.
                bookRepository.saveBooks(audioFiles.map { it.toCachedBook() })
            }
            _isLoading.value = false
        }
    }
}
