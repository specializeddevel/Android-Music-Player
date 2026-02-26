package com.raulburgosmurray.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

private const val TAG = "MetadataHelper"

object MetadataHelper {
    fun isArtUriValid(context: Context, artUri: String?): Boolean {
        if (artUri == null) return false
        return try {
            if (artUri.startsWith("file://")) {
                val file = File(artUri.removePrefix("file://"))
                file.exists()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private val PREDEFINED_GENRES = listOf(
        "Terror", "Fiction", "Fantasy", "Science Fiction", "Mystery",
        "Thriller", "Romance", "Biography", "History", "Self-Help",
        "Business", "Children", "Young Adult", "Non-Fiction", "Comedy",
        "Drama", "Horror", "Adventure", "Crime", "Other"
    )

    fun extractMetadataFromUri(context: Context, uri: Uri, fileName: String): AudioMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                extractMetadata(retriever, uri.toString(), fileName, directoryName = null, context = context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata from $uri", e)
            null
        } finally {
            retriever.release()
        }
    }

    fun extractMetadataFromDocumentFile(context: Context, file: DocumentFile, directoryName: String?): AudioMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                extractMetadata(retriever, file.uri.toString(), file.name ?: "", directoryName = directoryName, context = context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata from ${file.name}", e)
            null
        } finally {
            retriever.release()
        }
    }

    private fun extractMetadata(retriever: MediaMetadataRetriever, mediaId: String, fileName: String, directoryName: String? = null, context: Context? = null): AudioMetadata {
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fileName
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: directoryName ?: "Unknown Album"
        val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
        val genre = normalizeGenre(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE))
        val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.toIntOrNull()

        val artUri = if (context != null) {
            extractAndCacheCoverArt(context, mediaId, retriever)
        } else {
            null
        }

        return AudioMetadata(
            mediaId = mediaId,
            title = title,
            artist = artist,
            album = album,
            year = year,
            genre = genre,
            trackNumber = trackNumber,
            comment = null,
            artUri = artUri,
            duration = duration,
            fileName = fileName,
            extractedAt = System.currentTimeMillis()
        )
    }

    private fun extractAndCacheCoverArt(context: Context, mediaId: String, retriever: MediaMetadataRetriever): String? {
        val cacheFile = File(context.cacheDir, "cover_${mediaId.hashCode()}.jpg")
        
        if (cacheFile.exists()) {
            return android.net.Uri.fromFile(cacheFile).toString()
        }

        return try {
            retriever.embeddedPicture?.let { art ->
                BitmapFactory.decodeByteArray(art, 0, art.size)?.let { bitmap ->
                    FileOutputStream(cacheFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    android.net.Uri.fromFile(cacheFile).toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cover art for $mediaId", e)
            null
        }
    }

    private fun normalizeGenre(genre: String?): String? {
        if (genre.isNullOrBlank()) return null

        val normalized = genre.trim().lowercase()
        return PREDEFINED_GENRES.find { it.lowercase() == normalized }
            ?: PREDEFINED_GENRES.find { it.lowercase().contains(normalized) }
            ?: "Other"
    }

    fun getPredefinedGenres(): List<String> = PREDEFINED_GENRES
}