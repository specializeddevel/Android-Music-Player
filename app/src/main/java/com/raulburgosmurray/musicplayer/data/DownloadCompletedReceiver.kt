package com.raulburgosmurray.musicplayer.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "DownloadReceiver"

class DownloadCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val prefs = context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
        val bookId = prefs.getString("dl_book_$downloadId", null)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return

        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query) ?: return

        try {
            if (cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                if (statusIdx >= 0 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUriString = if (uriIdx >= 0) cursor.getString(uriIdx) else null
                    val downloadedUri = localUriString ?: downloadManager.getUriForDownloadedFile(downloadId)?.toString()

                    if (!downloadedUri.isNullOrBlank()) {
                        Log.d(TAG, "Download $downloadId completed successfully for bookId=$bookId: $downloadedUri")
                        updateBookPathInDatabase(context, bookId, downloadedUri)
                    }
                } else {
                    Log.w(TAG, "Download $downloadId failed or was cancelled")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing download completed event", e)
        } finally {
            cursor.close()
            prefs.edit().remove("dl_book_$downloadId").apply()
        }
    }

    private fun updateBookPathInDatabase(context: Context, bookId: String?, localUri: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val cachedBook = if (bookId != null) {
                    db.cachedBookDao().getBookById(bookId)
                } else null

                if (cachedBook != null) {
                    val updated = cachedBook.copy(path = localUri)
                    db.cachedBookDao().upsertAll(listOf(updated))
                    Log.d(TAG, "Updated book ${cachedBook.title} path to local: $localUri")
                } else {
                    Log.w(TAG, "Could not find book with ID $bookId to update path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update book path in DB after download", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
