package com.raulburgosmurray.musicplayer.data

import android.content.Context
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

    fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun extract(context: Context, uriString: String): String? {
        if (!isSupported()) return null
        val uri = Uri.parse(uriString)
        Log.d(TAG, "Extracting from: $uriString (scheme=${uri.scheme})")

        // 1. Try local path resolution (file://, MediaStore DATA column, etc.)
        val localPath = resolveLocalPath(context, uri)
        if (localPath != null) {
            Log.d(TAG, "Resolved local path: $localPath")
            val desc = extractFromLocalFile(localPath)
            if (!desc.isNullOrBlank()) return desc
        }

        // 2. Try /proc/self/fd direct access via ParcelFileDescriptor (zero-copy seeking)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fdPath = "/proc/self/fd/${pfd.fd}"
                val desc = extractFromLocalFile(fdPath)
                if (!desc.isNullOrBlank()) {
                    Log.d(TAG, "Extracted description via /proc/self/fd")
                    return desc
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed /proc/self/fd extraction for $uri", e)
        }

        // 3. Fallback: Copy file to cacheDir so JAudiotagger can read head/tail atoms (e.g. moov at end of M4A)
        Log.d(TAG, "Trying file copy for SAF URI")
        val descFromCopy = extractViaCopy(context, uri)
        if (!descFromCopy.isNullOrBlank()) return descFromCopy

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
            val tag = audioFile.tag ?: return null

            // 1. Explicit candidates
            val candidates = listOf(
                tag.getFirst(FieldKey.COMMENT),
                tag.getFirst(FieldKey.SUBTITLE),
                tag.getFirst("DESCRIPTION"),
                tag.getFirst("LONG DESCRIPTION"),
                tag.getFirst("LDES"),
                tag.getFirst("ldes"), // mutagen MP4 uses 'ldes'
                tag.getFirst("desc"), // MP4 'desc' atom
                tag.getFirst("©cmt"),
                tag.getFirst("©des"),
            )

            val firstHit = candidates.firstOrNull { it.isNotBlank() }
            if (!firstHit.isNullOrBlank()) {
                Log.d(TAG, "Found candidate description: '${firstHit.take(30)}...'")
                return firstHit
            }

            // 2. Fallback: inspect all non-binary fields in tag for matching IDs
            val fields = tag.fields
            while (fields.hasNext()) {
                val field = fields.next()
                if (!field.isBinary) {
                    val id = field.id.uppercase()
                    val text = field.toString().trim()
                    if (text.isNotBlank() && (
                        id.contains("DES") || id.contains("CMT") ||
                        id.contains("COMM") || id.contains("LDES") ||
                        id.contains("COMMENT") || id.contains("SUBTITLE")
                    )) {
                        Log.d(TAG, "Found field by ID ($id): '${text.take(30)}...'")
                        return text
                    }
                }
            }

            Log.d(TAG, "No description candidates found in $path")
            null
        } catch (e: Exception) {
            Log.d(TAG, "JAudiotagger failed for $path", e)
            null
        }
    }

    private fun extractViaCopy(context: Context, uri: Uri): String? {
        var tempFile: File? = null
        return try {
            val originalName = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
            val ext = originalName.substringAfterLast('.', "tmp")
            tempFile = File(context.cacheDir, "synopsis_${uri.hashCode()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { out ->
                    input.copyTo(out)
                }
            }
            Log.d(TAG, "Copy: ${tempFile.length()} bytes, ext=.$ext, path=${tempFile.absolutePath}")
            extractFromLocalFile(tempFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed for $uri", e)
            null
        } finally {
            tempFile?.let { file ->
                if (file.exists() && !file.delete()) {
                    Log.w(TAG, "Failed to delete temp file: ${file.absolutePath}")
                }
            }
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
