package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

fun CachedBook.toMusic(): Music = Music(id, title, album, artist, duration, path, artUri, fileSize, fileName)
fun Music.toCachedBook(): CachedBook = CachedBook(id, title, album, artist, duration, path, artUri, fileSize, fileName)

enum class SortOrder { TITLE, ARTIST, PROGRESS, RECENT }

class MainViewModel(application: Application, settingsViewModel: SettingsViewModel) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
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

    private fun scanDirectoryRecursively(context: Context, directory: DocumentFile): List<Music> {
        val musicList = mutableListOf<Music>()
        val extensions = arrayOf("mp3", "m4a", "m4b", "aac", "wav", "ogg", "flac")
        directory.listFiles().forEach { file ->
            if (file.isDirectory) musicList.addAll(scanDirectoryRecursively(context, file))
            else {
                val name = file.name?.lowercase() ?: ""
                if (extensions.any { name.endsWith(".$it") }) {
                    try {
                        context.contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(pfd.fileDescriptor)
                            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            if (duration > 5000) {
                                val id = file.uri.toString()
                                musicList.add(Music(id, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name ?: "Unknown", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: directory.name ?: "Unknown Album", duration, id, getUniqueArtUri(context, id, file.uri), file.length(), file.name ?: ""))
                            }
                            retriever.release()
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        return musicList
    }

    private suspend fun scanWithMediaStore(context: Context): List<Music> = withContext(Dispatchers.IO) {
        val tempList = mutableListOf<Music>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DISPLAY_NAME)
        context.contentResolver.query(collection, proj, "${MediaStore.Audio.Media.DURATION} > 5000", null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val idLong = cursor.getLong(0); val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, idLong)
                tempList.add(Music(idLong.toString(), cursor.getString(1) ?: "Unknown", cursor.getString(2) ?: "Unknown", cursor.getString(3) ?: "Unknown", cursor.getLong(4), contentUri.toString(), getUniqueArtUri(context, idLong.toString(), contentUri), cursor.getLong(5), cursor.getString(6) ?: ""))
            }
        }
        tempList
    }

    private fun getUniqueArtUri(context: Context, id: String, uri: Uri): String? {
        val cacheFile = File(context.cacheDir, "cover_${id.hashCode()}.jpg")
        if (cacheFile.exists()) return Uri.fromFile(cacheFile).toString()
        val retriever = MediaMetadataRetriever()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                retriever.embeddedPicture?.let { art ->
                    BitmapFactory.decodeByteArray(art, 0, art.size)?.let { bitmap ->
                        FileOutputStream(cacheFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                        return Uri.fromFile(cacheFile).toString()
                    }
                }
            }
        } catch (e: Exception) {} finally { retriever.release() }
        return null
    }
}
