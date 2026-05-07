package com.raulburgosmurray.musicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileOutputStream

private const val TAG = "DescriptionExtractor"

object DescriptionExtractor {

    fun isSupported(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM

    fun extract(context: Context, uriString: String): String? {
        if (!isSupported()) return null
        val uri = Uri.parse(uriString)
        Log.d(TAG, "Extracting from: $uriString (scheme=${uri.scheme})")

        val localPath = resolveLocalPath(context, uri)
        if (localPath != null) {
            Log.d(TAG, "Resolved local path: $localPath")
            val desc = extractFromLocalFile(localPath)
            if (!desc.isNullOrBlank()) return desc
        } else {
            Log.d(TAG, "Could not resolve local path, trying via retriever")
            val descFromRetriever = extractViaRetriever(context, uri)
            if (!descFromRetriever.isNullOrBlank()) return descFromRetriever

            Log.d(TAG, "Trying partial file copy for SAF URI")
            val descFromPartialCopy = extractViaPartialCopy(context, uri)
            if (!descFromPartialCopy.isNullOrBlank()) return descFromPartialCopy
        }

        Log.d(TAG, "No description found for $uriString")
        return null
    }

    fun extractFromLocalFile(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) {
                Log.d(TAG, "File does not exist: $path")
                return null
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateDefault

            val candidates = listOf(
                tag.getFirst(FieldKey.COMMENT),
                tag.getFirst(FieldKey.SUBTITLE),
                tag.getFirst("DESCRIPTION"),
                tag.getFirst("LONG DESCRIPTION"),
                tag.getFirst("LDES"),
                tag.getFirst("©des"),
            )

            val result = candidates.firstOrNull { it.isNotBlank() }
            Log.d(TAG, "JAudiotagger: comment='${tag.getFirst(FieldKey.COMMENT)?.take(30)}', subtitle='${tag.getFirst(FieldKey.SUBTITLE)?.take(30)}'")
            result
        } catch (e: Exception) {
            Log.d(TAG, "JAudiotagger failed for $path", e)
            null
        }
    }

    private fun extractViaPartialCopy(context: Context, uri: Uri): String? {
        var tempFile: File? = null
        return try {
            // Usar la extensión del archivo original para que JAudiotagger lo reconozca
            val originalName = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
            val ext = originalName.substringAfterLast('.', "tmp")
            tempFile = File(context.cacheDir, "synopsis_${uri.hashCode()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                var copied = 0L
                val maxBytes = 512L * 1024
                var read: Int
                val out = FileOutputStream(tempFile)
                while (input.read(buffer).also { read = it } != -1 && copied < maxBytes) {
                    val toWrite = if (copied + read > maxBytes) (maxBytes - copied).toInt() else read
                    out.write(buffer, 0, toWrite)
                    copied += toWrite
                }
                out.close()
            }
            Log.d(TAG, "Partial copy: ${tempFile.length()} bytes, ext=.$ext, path=${tempFile.absolutePath}")
            extractFromLocalFile(tempFile.absolutePath)
        } catch (e: Exception) {
            Log.d(TAG, "Partial copy failed for $uri", e)
            null
        } finally {
            tempFile?.delete()
        }
    }

    private fun extractViaRetriever(context: Context, uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val comment = retriever.extractMetadata(25)
                Log.d(TAG, "Retriever: comment='${comment?.take(50)}'")
                comment?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Retriever failed for $uri", e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun resolveLocalPath(context: Context, uri: Uri): String? {
        return when {
            uri.scheme == "file" -> uri.path
            uri.scheme == "content" -> {
                val path = queryMediaStorePath(context, uri)
                if (path != null) return path

                try {
                    val projection = arrayOf(MediaStore.MediaColumns.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (idx >= 0) {
                                cursor.getString(idx)?.takeIf { File(it).exists() }
                            } else null
                        } else null
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Content resolver query failed for $uri", e)
                    null
                }
            }
            else -> null
        }
    }

    private fun queryMediaStorePath(context: Context, uri: Uri): String? {
        return try {
            if (uri.authority == "media" && uri.pathSegments.size >= 3) {
                val id = uri.lastPathSegment?.toLongOrNull()
                if (id != null) {
                    val projection = arrayOf(MediaStore.Audio.Media.DATA)
                    val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    val selection = "${MediaStore.Audio.Media._ID} = ?"
                    val selectionArgs = arrayOf(id.toString())

                    context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                            cursor.getString(idx)?.takeIf { File(it).exists() }
                        } else null
                    }
                } else null
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "MediaStore path query failed for $uri", e)
            null
        }
    }
}
