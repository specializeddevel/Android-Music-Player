package com.raulburgosmurray.musicplayer.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.raulburgosmurray.musicplayer.Music
import com.raulburgosmurray.musicplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "BookDownloader"
private const val CHANNEL_ID = "book_download_channel"
private const val NOTIFICATION_ID_BASE = 8000

object BookDownloader {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun downloadBook(context: Context, book: Music, onStarted: () -> Unit = {}): Boolean {
        if (!book.path.startsWith("http://") && !book.path.startsWith("https://")) {
            Log.w(TAG, "Book ${book.title} is already local or has an invalid URL: ${book.path}")
            return false
        }

        val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val libraryUris = settingsPrefs.getStringSet("library_root_uris", emptySet())?.toList() ?: emptyList()
        val primarySafUriString = libraryUris.firstOrNull()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) { onStarted() }
                createNotificationChannel(context)
                val notifId = NOTIFICATION_ID_BASE + book.id.hashCode()
                showProgressNotification(context, notifId, book.title, "Iniciando descarga...", 0, 100)

                val ext = book.fileName.substringAfterLast('.', "mp3").ifBlank { "mp3" }
                val mimeType = if (ext.equals("m4a", ignoreCase = true) || ext.equals("m4b", ignoreCase = true)) "audio/mp4" else "audio/mpeg"
                val sanitizedTitle = book.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
                val fileName = "$sanitizedTitle.$ext"

                var targetUri: Uri? = null

                // 1. Download directly into user's configured SAF folder if available
                if (!primarySafUriString.isNullOrBlank()) {
                    try {
                        val treeUri = Uri.parse(primarySafUriString)
                        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                        if (rootDoc != null && rootDoc.exists() && rootDoc.canWrite()) {
                            val existingFile = rootDoc.findFile(fileName)
                            if (existingFile != null && existingFile.exists()) {
                                existingFile.delete()
                            }
                            val newFile = rootDoc.createFile(mimeType, fileName)
                            if (newFile != null) {
                                targetUri = newFile.uri
                                Log.d(TAG, "Created SAF file in configured folder: $targetUri")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not create SAF file in configured folder, falling back to public Music dir", e)
                    }
                }

                // 2. Fallback: Download into public Music/Litera directory
                if (targetUri == null) {
                    val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Litera")
                    if (!musicDir.exists()) musicDir.mkdirs()
                    val targetFile = File(musicDir, fileName)
                    targetUri = Uri.fromFile(targetFile)
                    Log.d(TAG, "Created local file in Music/Litera: $targetUri")
                }

                // Resolve latest stream URL and synopsis for ABS books
                var downloadPath = book.path
                if (book.id.startsWith("abs_")) {
                    val absRepo = AudiobookshelfRepository(context)
                    val resolved = absRepo.resolveStreamUrl(book.id)
                    if (!resolved.isNullOrBlank()) {
                        downloadPath = resolved
                    }
                }

                val request = Request.Builder().url(downloadPath).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        showCompletedNotification(context, notifId, book.title, "Error al descargar (HTTP ${response.code})", false)
                        return@launch
                    }

                    val responseBody = response.body
                        ?: run {
                            showCompletedNotification(context, notifId, book.title, "Respuesta vacía del servidor", false)
                            return@launch
                        }

                    val totalBytes = responseBody.contentLength().takeIf { it > 0 } ?: book.fileSize
                    var copiedBytes = 0L

                    context.contentResolver.openOutputStream(targetUri!!, "wt")?.use { output ->
                        responseBody.byteStream().use { input ->
                            val buffer = ByteArray(32768)
                            var read: Int
                            var lastNotifTime = System.currentTimeMillis()

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                copiedBytes += read

                                val now = System.currentTimeMillis()
                                if (now - lastNotifTime > 800) {
                                    lastNotifTime = now
                                    val progressPercent = if (totalBytes > 0) ((copiedBytes * 100) / totalBytes).toInt() else -1
                                    val progressText = if (totalBytes > 0) "${formatSize(copiedBytes)} / ${formatSize(totalBytes)}" else formatSize(copiedBytes)
                                    showProgressNotification(context, notifId, book.title, progressText, progressPercent, 100)
                                }
                            }
                        }
                    }

                    // Update Room Database with the downloaded local URI
                    val db = AppDatabase.getDatabase(context)
                    val cachedBook = db.cachedBookDao().getBookById(book.id)
                    if (cachedBook != null) {
                        val updated = cachedBook.copy(
                            path = targetUri.toString(),
                            fileSize = if (copiedBytes > 0) copiedBytes else cachedBook.fileSize
                        )
                        db.cachedBookDao().upsertAll(listOf(updated))
                        Log.d(TAG, "Updated book ${book.title} in DB with downloaded path: $targetUri")
                    }

                    showCompletedNotification(context, notifId, book.title, "Descarga completada con éxito", true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading book ${book.title}", e)
                val notifId = NOTIFICATION_ID_BASE + book.id.hashCode()
                showCompletedNotification(context, notifId, book.title, "Error en la descarga: ${e.localizedMessage}", false)
            }
        }
        return true
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas de Audiolibros",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el progreso de descarga de audiolibros"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(context: Context, notifId: Int, title: String, text: String, progress: Int, max: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_audiobook_cover)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress >= 0) {
            builder.setProgress(max, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        nm.notify(notifId, builder.build())
    }

    private fun showCompletedNotification(context: Context, notifId: Int, title: String, text: String, success: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_audiobook_cover)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        nm.notify(notifId, builder.build())
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(java.util.Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
