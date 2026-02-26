package com.raulburgosmurray.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.raulburgosmurray.musicplayer.Constants
import com.raulburgosmurray.musicplayer.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class MusicScanner(
    private val metadataHelper: MetadataHelper = MetadataHelper,
    private val metadataJsonHelper: MetadataJsonHelper = MetadataJsonHelper
) {
    private val extensions = setOf("mp3", "m4a", "m4b", "aac", "wav", "ogg", "flac")

    /**
     * Check if the file path belongs to a messaging/voice recording app that should be excluded
     */
    private fun isExcludedPath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return Constants.EXCLUDED_AUDIO_PATHS.any { excluded ->
            lowerPath.contains(excluded.lowercase())
        }
    }

    /**
     * Check if the audio file should be included based on size and duration
     * Audiobooks are typically larger than voice messages
     */
    private fun isLikelyAudiobook(sizeBytes: Long, durationMs: Long): Boolean {
        // Must meet minimum duration (already checked in query)
        // Additional size check: audiobooks are usually > 5MB
        return sizeBytes >= Constants.MIN_AUDIOBOOK_SIZE_BYTES
    }

    suspend fun scanDirectory(context: Context, directory: DocumentFile, onProgress: (Int, Int) -> Unit): List<Music> = withContext(Dispatchers.IO) {
        val musicList = ConcurrentHashMap<String, Music>()
        
        val allFiles = collectAudioFiles(directory)
        val totalFiles = allFiles.size
        onProgress(0, totalFiles)

        allFiles.chunked(8).forEachIndexed { chunkIndex, chunk ->
            chunk.map { file ->
                async {
                    try {
                        val id = file.uri.toString()
                        val fileSize = file.length()
                        val fileName = file.name ?: ""

                        // Filter out files from messaging apps
                        if (isExcludedPath(fileName)) {
                            return@async
                        }

                        // Filter by minimum size (audiobooks are typically > 5MB)
                        if (!isLikelyAudiobook(fileSize, 0)) {
                            return@async
                        }

                        val existingMetadata = metadataJsonHelper.loadMetadata(context, id)
                        val freshMetadata = metadataHelper.extractMetadataFromDocumentFile(context, file, directory.name)

                        if (freshMetadata != null && freshMetadata.duration > Constants.MIN_AUDIO_DURATION_MS) {
                            val finalMetadata = if (existingMetadata != null) {
                                metadataJsonHelper.mergeMetadata(existingMetadata, freshMetadata.copy(mediaId = id))
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
                                fileSize = fileSize,
                                fileName = finalMetadata.fileName
                            )
                            musicList[id] = music
                            metadataJsonHelper.saveMetadata(context, finalMetadata)
                        }
                    } catch (e: Exception) {
                        Log.e("MusicScanner", "Error scanning ${file.name}", e)
                    }
                }
            }.awaitAll()

            val processed = (chunkIndex + 1) * chunk.size
            onProgress(processed, totalFiles)
        }

        musicList.values.toList()
    }

    suspend fun scanMediaStore(context: Context): List<Music> = withContext(Dispatchers.IO) {
        val tempList = mutableListOf<Music>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA
        )

        context.contentResolver.query(
            collection,
            proj,
            "${MediaStore.Audio.Media.DURATION} > ${Constants.MIN_AUDIO_DURATION_MS}",
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val idLong = cursor.getLong(0)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, idLong)
                val id = contentUri.toString()

                val fileSize = cursor.getLong(5)
                val filePath = cursor.getString(7) ?: ""

                // Filter out files from messaging apps
                if (isExcludedPath(filePath)) {
                    continue
                }

                // Filter by minimum size (audiobooks are typically > 5MB)
                if (!isLikelyAudiobook(fileSize, 0)) {
                    continue
                }

                val existingMetadata = metadataJsonHelper.loadMetadata(context, id)
                val freshMetadata = metadataHelper.extractMetadataFromUri(context, contentUri, cursor.getString(6) ?: "")

                if (freshMetadata != null) {
                    val finalMetadata = if (existingMetadata != null) {
                        metadataJsonHelper.mergeMetadata(existingMetadata, freshMetadata.copy(mediaId = id))
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
                        fileSize = fileSize,
                        fileName = cursor.getString(6) ?: ""
                    )
                    tempList.add(music)
                    metadataJsonHelper.saveMetadata(context, finalMetadata)
                }
            }
        }
        tempList
    }

    private fun collectAudioFiles(directory: DocumentFile): List<DocumentFile> {
        val files = mutableListOf<DocumentFile>()
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                files.addAll(collectAudioFiles(file))
            } else {
                val name = file.name?.lowercase() ?: ""
                if (extensions.any { name.endsWith(".$it") }) {
                    files.add(file)
                }
            }
        }
        return files
    }
}
