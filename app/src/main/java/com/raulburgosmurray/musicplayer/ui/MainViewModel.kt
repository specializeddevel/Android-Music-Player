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
import androidx.lifecycle.viewModelScope
import com.raulburgosmurray.musicplayer.Music
import com.raulburgosmurray.musicplayer.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class SortOrder {
    TITLE, ARTIST, PROGRESS, RECENT
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    
    private val _books = MutableStateFlow<List<Music>>(emptyList())
    
    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _bookProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val bookProgress: StateFlow<Map<String, Float>> = _bookProgress.asStateFlow()

    private val _bookActivityTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
    val bookActivityTimestamps: StateFlow<Map<String, Long>> = _bookActivityTimestamps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Lista principal ordenada reactivamente
    val books: StateFlow<List<Music>> = combine(_books, _sortOrder, _bookProgress, _bookActivityTimestamps) { books, order, progress, timestamps ->
        when (order) {
            SortOrder.TITLE -> books.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> books.sortedBy { it.artist.lowercase() }
            SortOrder.PROGRESS -> books.sortedByDescending { progress[it.id] ?: 0f }
            SortOrder.RECENT -> books.sortedByDescending { timestamps[it.id] ?: 0L }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteBooks: StateFlow<List<Music>> = combine(books, _favoriteIds) { books, favIds ->
        books.filter { favIds.contains(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadBooks()
        observeFavorites()
        observeProgress()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    private fun observeProgress() {
        viewModelScope.launch {
            database.progressDao().getAllProgressFlow().collect { progressList ->
                _bookProgress.value = progressList.associate { 
                    it.mediaId to if (it.duration > 0) it.lastPosition.toFloat() / it.duration.toFloat() else 0f
                }
                _bookActivityTimestamps.value = progressList.associate { 
                    it.mediaId to it.lastPauseTimestamp 
                }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            database.favoriteDao().getAllFavoriteIds().collectLatest { ids ->
                _favoriteIds.value = ids.toSet()
            }
        }
    }

    fun loadBooks() {
        viewModelScope.launch {
            _isLoading.value = true
            val audioFiles = getAllAudio(getApplication())
            _books.value = audioFiles
            _isLoading.value = false
        }
    }

    private fun getUniqueArtUri(context: Context, musicId: String, path: String): String? {
        val cacheFile = File(context.cacheDir, "cover_$musicId.jpg")
        if (cacheFile.exists()) return Uri.fromFile(cacheFile).toString()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                return Uri.fromFile(cacheFile).toString()
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error extrayendo arte de $path", e)
        } finally {
            retriever.release()
        }
        return null
    }

    private suspend fun getAllAudio(context: Context): List<Music> = withContext(Dispatchers.IO) {
        val tempList = mutableListOf<Music>()
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' AND ${MediaStore.Audio.Media.ALBUM} NOT LIKE 'WhatsApp%'"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        context.contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
                val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
                val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val artUri = getUniqueArtUri(context, id, path)

                if (duration > 10000 && !path.contains("WhatsApp") && File(path).exists()) {
                    tempList.add(Music(id, title, album, artist, duration, path, artUri))
                }
            }
        }
        tempList
    }
}
