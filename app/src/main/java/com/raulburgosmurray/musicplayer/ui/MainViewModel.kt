package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import android.content.ContentUris
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import com.raulburgosmurray.musicplayer.Music
import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.CachedBook
import com.raulburgosmurray.musicplayer.data.AudioMetadata
import com.raulburgosmurray.musicplayer.data.MetadataHelper
import com.raulburgosmurray.musicplayer.data.MetadataJsonHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.concurrent.ConcurrentHashMap

fun CachedBook.toMusic(): Music = Music(id, title, album, artist, duration, path, artUri, fileSize, fileName)
fun Music.toCachedBook(): CachedBook = CachedBook(id, title, album, artist, duration, path, artUri, fileSize, fileName)

enum class SortOrder { TITLE, ARTIST, PROGRESS, RECENT }

class MainViewModel(application: Application, settingsViewModel: SettingsViewModel) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _scanProgress = MutableStateFlow(0 to 0)
    val scanProgress = _scanProgress.asStateFlow()
    private val artUriCache = ConcurrentHashMap<String, String?>()
    
    private val _books = database.cachedBookDao().getAllBooks()
        .map { list -> list.map { it.toMusic() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteBooks = combine(books, _favoriteIds) { b, f -> b.filter { f.contains(it.id) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsViewModel.libraryRootUri.collect { uri ->
                if (database.cachedBookDao().getAllBooks().first().isEmpty()) loadBooks(uri)
            }
        }
        observeFavorites(); observeProgress()
    }

    private fun observeProgress() {
        viewModelScope.launch {
            database.progressDao().getAllProgressFlow().collect { list ->
                _bookProgress.value = list.associate { it.mediaId to if (it.duration > 0) it.lastPosition.toFloat() / it.duration.toFloat() else 0f }
                _bookActivityTimestamps.value = list.associate { it.mediaId to it.lastPauseTimestamp }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch { database.favoriteDao().getAllFavoriteIds().collectLatest { _favoriteIds.value = it.toSet() } }
    }

    fun loadBooks(libraryRootUri: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val audioFiles = withContext(Dispatchers.IO) {
                if (libraryRootUri != null) {
                    val rootDoc = DocumentFile.fromTreeUri(getApplication(), Uri.parse(libraryRootUri))
                    if (rootDoc != null) scanDirectoryRecursively(getApplication(), rootDoc) else emptyList()
                } else scanWithMediaStore(getApplication())
            }
            withContext(Dispatchers.IO) {
                database.cachedBookDao().clearCache()
                database.cachedBookDao().upsertAll(audioFiles.map { it.toCachedBook() })
            }
            _isLoading.value = false
        }
    }

private suspend fun scanDirectoryRecursively(context: Context, directory: DocumentFile): List<Music> = withContext(Dispatchers.IO) {
        val musicList = ConcurrentHashMap<String, Music>()
        val extensions = setOf("mp3", "m4a", "m4b", "aac", "wav", "ogg", "flac")
        var totalFiles = 0
        var processedFiles = 0

        suspend fun collectFiles(dir: DocumentFile): List<DocumentFile> {
            val files = mutableListOf<DocumentFile>()
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    files.addAll(collectFiles(file))
                } else {
                    val name = file.name?.lowercase() ?: ""
                    if (extensions.any { name.endsWith(".$it") }) {
                        files.add(file)
                    }
                }
            }
            return files
        }

        val allFiles = collectFiles(directory)
        totalFiles = allFiles.size
        _scanProgress.value = 0 to totalFiles

allFiles.chunked(8).forEach { chunk ->
            chunk.map { file ->
                async {
                    try {
                        val id = file.uri.toString()
                        Log.d("MainViewModel", "Scanning file: ${file.name}, URI: $id")
                        
                        val existingMetadata = MetadataJsonHelper.loadMetadata(context, id)
                        val freshMetadata = MetadataHelper.extractMetadataFromDocumentFile(context, file, directory.name)
                        
                        if (freshMetadata != null && freshMetadata.duration > 5000) {
                            val finalMetadata = if (existingMetadata != null) {
                                MetadataJsonHelper.mergeMetadata(existingMetadata, freshMetadata.copy(mediaId = id))
                            } else {
                                freshMetadata.copy(mediaId = id)
                            }
                            
                            val music = Music(
                                id = id,
                                title = finalMetadata.title,
                                artist = finalMetadata.artist,
                                album = finalMetadata.album ?: "Unknown Album",
                                duration = finalMetadata.duration,
                                path = id,
                                artUri = finalMetadata.artUri,
                                fileSize = file.length(),
                                fileName = finalMetadata.fileName
                            )
                            musicList[id] = music
                            MetadataJsonHelper.saveMetadata(context, finalMetadata)
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error scanning ${file.name}", e)
                    }
                }
            }.awaitAll()

            processedFiles += chunk.size
            _scanProgress.value = processedFiles to totalFiles
        }

        musicList.values.toList()
    }

private suspend fun scanWithMediaStore(context: Context): List<Music> = withContext(Dispatchers.IO) {
        val tempList = mutableListOf<Music>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DISPLAY_NAME)
        context.contentResolver.query(collection, proj, "${MediaStore.Audio.Media.DURATION} > 5000", null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val idLong = cursor.getLong(0); val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, idLong)
                val id = contentUri.toString()
                
                val existingMetadata = MetadataJsonHelper.loadMetadata(context, id)
                val freshMetadata = MetadataHelper.extractMetadataFromUri(context, contentUri, cursor.getString(6) ?: "")
                
                if (freshMetadata != null) {
                    val finalMetadata = if (existingMetadata != null) {
                        MetadataJsonHelper.mergeMetadata(existingMetadata, freshMetadata.copy(mediaId = id))
                    } else {
                        freshMetadata.copy(mediaId = id)
                    }
                    
                    val music = Music(
                        id = id,
                        title = finalMetadata.title,
                        artist = finalMetadata.artist,
                        album = finalMetadata.album ?: "Unknown",
                        duration = finalMetadata.duration,
                        path = id,
                        artUri = finalMetadata.artUri,
                        fileSize = cursor.getLong(5),
                        fileName = cursor.getString(6) ?: ""
                    )
                    tempList.add(music)
                    MetadataJsonHelper.saveMetadata(context, finalMetadata)
                }
            }
        }
        tempList
    }
}
