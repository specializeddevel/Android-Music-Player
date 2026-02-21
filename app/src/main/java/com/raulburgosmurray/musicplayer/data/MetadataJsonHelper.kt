package com.raulburgosmurray.musicplayer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

private const val TAG = "MetadataJsonHelper"

object MetadataJsonHelper {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun saveMetadata(context: Context, metadata: AudioMetadata) {
        try {
            val metadataDir = File(context.filesDir, "metadata")
            if (!metadataDir.exists()) {
                metadataDir.mkdirs()
            }
            val fileName = "${metadata.mediaId.hashCode()}.json"
            val file = File(metadataDir, fileName)
            file.writeText(json.encodeToString(metadata))
            Log.d(TAG, "Saved metadata for ${metadata.mediaId} to $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metadata for ${metadata.mediaId}", e)
        }
    }

    fun loadMetadata(context: Context, mediaId: String): AudioMetadata? {
        return try {
            val metadataDir = File(context.filesDir, "metadata")
            val fileName = "${mediaId.hashCode()}.json"
            val file = File(metadataDir, fileName)
            if (file.exists()) {
                val metadata = json.decodeFromString<AudioMetadata>(file.readText())
                Log.d(TAG, "Loaded metadata for $mediaId from $fileName")
                metadata
            } else {
                Log.d(TAG, "Metadata file not found for $mediaId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metadata for $mediaId", e)
            null
        }
    }

    fun mergeMetadata(existing: AudioMetadata, fresh: AudioMetadata): AudioMetadata {
        return AudioMetadata(
            mediaId = fresh.mediaId,
            title = if (existing.isUserEdited(AudioMetadata.FIELD_TITLE)) existing.title else fresh.title,
            artist = if (existing.isUserEdited(AudioMetadata.FIELD_ARTIST)) existing.artist else fresh.artist,
            album = if (existing.isUserEdited(AudioMetadata.FIELD_ALBUM)) existing.album else fresh.album,
            year = if (existing.isUserEdited(AudioMetadata.FIELD_YEAR)) existing.year else fresh.year,
            genre = if (existing.isUserEdited(AudioMetadata.FIELD_GENRE)) existing.genre else fresh.genre,
            trackNumber = if (existing.isUserEdited(AudioMetadata.FIELD_TRACK_NUMBER)) existing.trackNumber else fresh.trackNumber,
            comment = if (existing.isUserEdited(AudioMetadata.FIELD_COMMENT)) existing.comment else fresh.comment,
            artUri = if (existing.isUserEdited(AudioMetadata.FIELD_ART_URI)) existing.artUri else fresh.artUri,
            duration = fresh.duration,
            fileName = fresh.fileName,
            extractedAt = fresh.extractedAt,
            userEditedFields = existing.userEditedFields
        )
    }

    fun deleteMetadata(context: Context, mediaId: String) {
        try {
            val metadataDir = File(context.filesDir, "metadata")
            val fileName = "${mediaId.hashCode()}.json"
            val file = File(metadataDir, fileName)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted metadata for $mediaId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting metadata for $mediaId", e)
        }
    }

    fun metadataExists(context: Context, mediaId: String): Boolean {
        val metadataDir = File(context.filesDir, "metadata")
        val fileName = "${mediaId.hashCode()}.json"
        return File(metadataDir, fileName).exists()
    }

    fun getAllMetadataFiles(context: Context): List<File> {
        val metadataDir = File(context.filesDir, "metadata")
        if (!metadataDir.exists()) {
            return emptyList()
        }
        return metadataDir.listFiles()?.toList() ?: emptyList()
    }

    fun encodeMediaIdForDatabase(mediaId: String): String {
        val uri = Uri.parse(mediaId)
        val scheme = uri.scheme
        val authority = uri.authority
        val path = uri.path

        if (scheme == null || authority == null || path == null) {
            return mediaId
        }

        val encodedPath = Uri.encode(path, "/")
        return "$scheme://$authority$encodedPath"
    }
}