package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raulburgosmurray.musicplayer.data.AudioMetadata
import com.raulburgosmurray.musicplayer.data.MetadataHelper
import com.raulburgosmurray.musicplayer.data.MetadataJsonHelper
import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class MetadataEditorState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val metadata: AudioMetadata? = null,
    val newArtUri: Uri? = null,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

class MetadataEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val bookRepository = BookRepository(database.cachedBookDao())
    private val _uiState = MutableStateFlow(MetadataEditorState())
    val uiState: StateFlow<MetadataEditorState> = _uiState.asStateFlow()

    fun loadMetadata(mediaId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                Log.d("MetadataEditorVM", "Loading metadata for mediaId: $mediaId")
                
                var metadata = withContext(Dispatchers.IO) {
                    MetadataJsonHelper.loadMetadata(getApplication(), mediaId)
                }
                
                if (metadata == null) {
                    Log.d("MetadataEditorVM", "JSON metadata not found, checking database")
                    
                    val sampleBooks = withContext(Dispatchers.IO) {
                        bookRepository.getSampleBooks()
                    }
                    Log.d("MetadataEditorVM", "Sample books in database: $sampleBooks")
                    
                    val encodedMediaId = MetadataJsonHelper.encodeMediaIdForDatabase(mediaId)
                    Log.d("MetadataEditorVM", "Encoded mediaId: $encodedMediaId")
                    
                    val cachedBook = withContext(Dispatchers.IO) {
                        bookRepository.getBookById(encodedMediaId)
                    }
                    
                    Log.d("MetadataEditorVM", "CachedBook from database: $cachedBook")
                    
                    if (cachedBook != null) {
                        metadata = AudioMetadata(
                            mediaId = cachedBook.id,
                            title = cachedBook.title,
                            artist = cachedBook.artist,
                            album = cachedBook.album,
                            year = null,
                            genre = null,
                            trackNumber = null,
                            comment = null,
                            artUri = cachedBook.artUri,
                            duration = cachedBook.duration,
                            fileName = cachedBook.fileName,
                            extractedAt = System.currentTimeMillis()
                        )
                        
                        withContext(Dispatchers.IO) {
                            MetadataJsonHelper.saveMetadata(getApplication(), metadata)
                        }
                    } else {
                        Log.e("MetadataEditorVM", "Book not found in database for mediaId: $mediaId")
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    metadata = metadata,
                    error = if (metadata == null) "No se encontró el libro en la base de datos" else null
                )
            } catch (e: Exception) {
                Log.e("MetadataEditorVM", "Error loading metadata", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar metadatos: ${e.message}"
                )
            }
        }
    }

    private fun updateMetadata(field: String, value: Any?) {
        val metadata = _uiState.value.metadata ?: return
        val newMetadata = when (field) {
            "title" -> metadata.copy(title = value as String).withUserEditedField(AudioMetadata.FIELD_TITLE)
            "artist" -> metadata.copy(artist = value as String).withUserEditedField(AudioMetadata.FIELD_ARTIST)
            "album" -> metadata.copy(album = value as String).withUserEditedField(AudioMetadata.FIELD_ALBUM)
            "year" -> metadata.copy(year = (value as? String)?.ifBlank { null }).withUserEditedField(AudioMetadata.FIELD_YEAR)
            "genre" -> metadata.copy(genre = (value as? String)?.ifBlank { null }).withUserEditedField(AudioMetadata.FIELD_GENRE)
            "trackNumber" -> metadata.copy(trackNumber = value as? Int).withUserEditedField(AudioMetadata.FIELD_TRACK_NUMBER)
            "comment" -> metadata.copy(comment = (value as? String)?.ifBlank { null }).withUserEditedField(AudioMetadata.FIELD_COMMENT)
            else -> return
        }
        _uiState.value = _uiState.value.copy(metadata = newMetadata)
    }

    fun updateTitle(title: String) = updateMetadata("title", title)
    fun updateArtist(artist: String) = updateMetadata("artist", artist)
    fun updateAlbum(album: String) = updateMetadata("album", album)
    fun updateYear(year: String) = updateMetadata("year", year)
    fun updateGenre(genre: String) = updateMetadata("genre", genre)
    fun updateTrackNumber(trackNumber: String) = updateMetadata("trackNumber", trackNumber.toIntOrNull())
    fun updateComment(comment: String) = updateMetadata("comment", comment)

    fun setNewArtUri(uri: Uri?) {
        val metadata = _uiState.value.metadata ?: return
        if (uri != null) {
            _uiState.value = _uiState.value.copy(
                newArtUri = uri,
                metadata = metadata.withUserEditedField(AudioMetadata.FIELD_ART_URI)
            )
        } else {
            _uiState.value = _uiState.value.copy(newArtUri = uri)
        }
    }

    fun saveMetadata() {
        val metadata = _uiState.value.metadata ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val context = getApplication<Application>()
                
                withContext(Dispatchers.IO) {
                    var updatedMetadata = metadata
                    
                    if (_uiState.value.newArtUri != null) {
                        val artUri = saveCoverArt(context, metadata.mediaId, _uiState.value.newArtUri!!)
                        updatedMetadata = metadata.copy(artUri = artUri)
                        
                        val encodedMediaId = MetadataJsonHelper.encodeMediaIdForDatabase(metadata.mediaId)
                        val cachedBook = bookRepository.getBookById(encodedMediaId)
                        if (cachedBook != null) {
                            bookRepository.saveBooks(listOf(cachedBook.copy(artUri = artUri)))
                        }
                    }
                    
                    MetadataJsonHelper.saveMetadata(context, updatedMetadata)
                }
                
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true,
                    metadata = _uiState.value.metadata?.copy(
                        artUri = _uiState.value.newArtUri?.toString() ?: metadata.artUri
                    )
                )
            } catch (e: Exception) {
                Log.e("MetadataEditorVM", "Error saving metadata", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Error al guardar: ${e.message}"
                )
            }
        }
    }

    private fun saveCoverArt(context: Context, mediaId: String, imageUri: Uri): String? {
        return try {
            val cacheFile = File(context.cacheDir, "cover_${mediaId.hashCode()}.jpg")
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            android.net.Uri.fromFile(cacheFile).toString()
        } catch (e: Exception) {
            Log.e("MetadataEditorVM", "Error saving cover art", e)
            null
        }
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun getPredefinedGenres(): List<String> = MetadataHelper.getPredefinedGenres()
}