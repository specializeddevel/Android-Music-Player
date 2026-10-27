package com.raulburgosmurray.musicplayer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "AbsRepository"
private const val ABS_PREFS_NAME = "abs_prefs"

fun sanitizeUrlForLog(url: String?): String {
    if (url.isNullOrBlank()) return ""
    return url.replace(Regex("token=[^&]+"), "token=***")
}

sealed class StreamResolution {
    data class Success(val multiTrackInfo: MultiTrackInfo, val synopsis: String?) : StreamResolution() {
        val primaryStreamUrl: String get() = multiTrackInfo.tracks.firstOrNull()?.streamUrl ?: ""
        val streamUrls: List<String> get() = multiTrackInfo.tracks.map { it.streamUrl }
        val streamUrl: String get() = primaryStreamUrl
        val durationMs: Long get() = multiTrackInfo.totalDurationMs
    }
    object NotFound : StreamResolution()
    object Unauthorized : StreamResolution()
    data class NetworkError(val message: String) : StreamResolution()
}

class AudiobookshelfRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(ABS_PREFS_NAME, Context.MODE_PRIVATE)

    fun isConnected(): Boolean = prefs.getBoolean("abs_connected", false)
    fun getServerUrl(): String? = prefs.getString("abs_server_url", null)
    fun getUsername(): String? = prefs.getString("abs_username", null)
    fun getToken(): String? = prefs.getString("abs_token", null)
    fun getLibraryName(): String? = prefs.getString("abs_library_name", null)
    fun getLastSyncTime(): Long = prefs.getLong("abs_last_sync_time", 0L)

    suspend fun connectAndSync(
        rawServerUrl: String,
        username: String,
        password: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        val serverUrl = AudiobookshelfClient.normalizeUrl(rawServerUrl)
        Log.d(TAG, "Connecting to Audiobookshelf at $serverUrl")

        val loginResult = AudiobookshelfClient.login(serverUrl, username, password)
        val user = loginResult.getOrElse {
            return@withContext Result.failure(it)
        }

        val token = user.token
        val librariesResult = AudiobookshelfClient.getLibraries(serverUrl, token)
        val libraries = librariesResult.getOrElse {
            return@withContext Result.failure(it)
        }

        if (libraries.isEmpty()) {
            return@withContext Result.failure(Exception("No se encontraron bibliotecas en el servidor"))
        }

        val lib = libraries.find { it.mediaType.lowercase() == "book" } ?: libraries.first()

        val itemsResult = AudiobookshelfClient.getLibraryItems(serverUrl, token, lib.id)
        val items = itemsResult.getOrElse {
            return@withContext Result.failure(it)
        }

        val cachedBooks = items.map { item ->
            val media = item.media
            val meta = media?.metadata
            val durationMs = ((media?.duration ?: 0.0) * 1000).toLong()
            val audioFile = media?.audioFiles?.firstOrNull()
            val streamUrl = AudiobookshelfClient.buildAudioStreamUrl(serverUrl, token, item.id, audioFile?.ino)
            val coverUrl = AudiobookshelfClient.buildCoverUrl(serverUrl, token, item.id)
            val synopsis = meta?.description?.takeIf { it.isNotBlank() } ?: audioFile?.metadata?.tagComment?.takeIf { it.isNotBlank() }

            CachedBook(
                id = "abs_${item.id}",
                title = meta?.title?.takeIf { it.isNotBlank() } ?: "Audiolibro sin título",
                album = meta?.seriesName?.takeIf { it.isNotBlank() } ?: lib.name,
                artist = meta?.authorName?.takeIf { it.isNotBlank() } ?: "Autor desconocido",
                duration = durationMs,
                path = streamUrl,
                artUri = coverUrl,
                fileSize = media?.size ?: audioFile?.metadata?.size ?: 0L,
                fileName = meta?.title ?: "ABS Book",
                description = synopsis
            )
        }

        val db = AppDatabase.getDatabase(context)
        val existingMap = db.cachedBookDao().getAllBooks().first().associateBy { it.id }

        val finalBooks = cachedBooks.map { fresh ->
            val existing = existingMap[fresh.id]
            if (existing != null && !existing.path.startsWith("http://") && !existing.path.startsWith("https://")) {
                if (isLocalPathValid(existing.path)) {
                    fresh.copy(
                        path = existing.path,
                        description = fresh.description ?: existing.description
                    )
                } else {
                    fresh
                }
            } else {
                fresh
            }
        }
        db.cachedBookDao().upsertAll(finalBooks)

        prefs.edit()
            .putBoolean("abs_connected", true)
            .putString("abs_server_url", serverUrl)
            .putString("abs_username", username)
            .putString("abs_token", token)
            .putString("abs_library_id", lib.id)
            .putString("abs_library_name", lib.name)
            .putLong("abs_last_sync_time", System.currentTimeMillis())
            .apply()

        Log.d(TAG, "Successfully synced ${cachedBooks.size} books from Audiobookshelf")
        Result.success(cachedBooks.size)
    }

    suspend fun resync(): Result<Int> = withContext(Dispatchers.IO) {
        val serverUrl = getServerUrl()
        val token = getToken()
        val libId = prefs.getString("abs_library_id", null)

        if (serverUrl.isNullOrBlank() || token.isNullOrBlank() || libId.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No hay un servidor configurado"))
        }

        val itemsResult = AudiobookshelfClient.getLibraryItems(serverUrl, token, libId)
        val items = itemsResult.getOrElse {
            return@withContext Result.failure(it)
        }

        val libName = getLibraryName() ?: "Audiobookshelf"
        val cachedBooks = items.map { item ->
            val media = item.media
            val meta = media?.metadata
            val durationMs = ((media?.duration ?: 0.0) * 1000).toLong()
            val audioFile = media?.audioFiles?.firstOrNull()
            val streamUrl = AudiobookshelfClient.buildAudioStreamUrl(serverUrl, token, item.id, audioFile?.ino)
            val coverUrl = AudiobookshelfClient.buildCoverUrl(serverUrl, token, item.id)
            val synopsis = meta?.description?.takeIf { it.isNotBlank() } ?: audioFile?.metadata?.tagComment?.takeIf { it.isNotBlank() }

            CachedBook(
                id = "abs_${item.id}",
                title = meta?.title?.takeIf { it.isNotBlank() } ?: "Audiolibro sin título",
                album = meta?.seriesName?.takeIf { it.isNotBlank() } ?: libName,
                artist = meta?.authorName?.takeIf { it.isNotBlank() } ?: "Autor desconocido",
                duration = durationMs,
                path = streamUrl,
                artUri = coverUrl,
                fileSize = media?.size ?: audioFile?.metadata?.size ?: 0L,
                fileName = meta?.title ?: "ABS Book",
                description = synopsis
            )
        }

        val db = AppDatabase.getDatabase(context)
        val existingMap = db.cachedBookDao().getAllBooks().first().associateBy { it.id }

        val finalBooks = cachedBooks.map { fresh ->
            val existing = existingMap[fresh.id]
            if (existing != null && !existing.path.startsWith("http://") && !existing.path.startsWith("https://")) {
                if (isLocalPathValid(existing.path)) {
                    fresh.copy(
                        path = existing.path,
                        description = fresh.description ?: existing.description
                    )
                } else {
                    fresh
                }
            } else {
                fresh
            }
        }
        db.cachedBookDao().upsertAll(finalBooks)

        prefs.edit()
            .putLong("abs_last_sync_time", System.currentTimeMillis())
            .apply()

        Result.success(cachedBooks.size)
    }

    fun isLocalPathValid(path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            if (path.startsWith("content://")) {
                val uri = android.net.Uri.parse(path)
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            } else if (path.startsWith("file://")) {
                val uri = android.net.Uri.parse(path)
                val file = java.io.File(uri.path ?: "")
                file.exists() && file.length() > 0
            } else {
                val file = java.io.File(path)
                file.exists() && file.length() > 0
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun resolveStreamDetails(mediaId: String): StreamResolution = withContext(Dispatchers.IO) {
        if (!mediaId.startsWith("abs_")) {
            val db = AppDatabase.getDatabase(context)
            val cached = db.cachedBookDao().getBookById(mediaId)
            return@withContext if (cached != null) {
                val singleTrack = BookTrack(
                    index = 0,
                    ino = "0",
                    streamUrl = cached.path,
                    startOffsetMs = 0L,
                    durationMs = cached.duration
                )
                val trackInfo = MultiTrackInfo(
                    bookId = mediaId,
                    totalDurationMs = cached.duration,
                    tracks = listOf(singleTrack)
                )
                StreamResolution.Success(trackInfo, cached.description)
            } else {
                StreamResolution.NotFound
            }
        }

        val absItemId = mediaId.removePrefix("abs_")
        val db = AppDatabase.getDatabase(context)
        val cached = db.cachedBookDao().getBookById(mediaId)

        if (cached != null && !cached.path.startsWith("http://") && !cached.path.startsWith("https://")) {
            if (isLocalPathValid(cached.path)) {
                Log.d(TAG, "Book $mediaId local path verified: ${cached.path}")
                val singleTrack = BookTrack(
                    index = 0,
                    ino = "0",
                    streamUrl = cached.path,
                    startOffsetMs = 0L,
                    durationMs = cached.duration
                )
                val trackInfo = MultiTrackInfo(
                    bookId = mediaId,
                    totalDurationMs = cached.duration,
                    tracks = listOf(singleTrack)
                )
                return@withContext StreamResolution.Success(trackInfo, cached.description)
            } else {
                Log.w(TAG, "Book $mediaId local path ${cached.path} is missing/deleted! Falling back to Audiobookshelf server stream.")
            }
        }

        val serverUrl = getServerUrl()
        val token = getToken()

        if (serverUrl.isNullOrBlank() || token.isNullOrBlank()) {
            return@withContext StreamResolution.Unauthorized
        }

        Log.d(TAG, "Resolving stream details for $mediaId from Audiobookshelf REST API...")
        val itemDetailResult = AudiobookshelfClient.getItemDetail(serverUrl, token, absItemId)
        val itemDetail = itemDetailResult.getOrNull()
        if (itemDetail == null) {
            val ex = itemDetailResult.exceptionOrNull()
            Log.w(TAG, "Failed to resolve item detail for $mediaId", ex)
            val msg = ex?.message ?: ""
            return@withContext when {
                msg.contains("401") || msg.contains("403") -> StreamResolution.Unauthorized
                msg.contains("404") -> StreamResolution.NotFound
                else -> StreamResolution.NetworkError(msg.ifBlank { "No se pudo conectar al servidor" })
            }
        }

        val media = itemDetail.media
        val audioFiles = media?.audioFiles
        val multiTrackInfo = MultiTrackHelper.buildMultiTrackInfo(mediaId, audioFiles, serverUrl, token)
        val primaryStreamUrl = multiTrackInfo.tracks.firstOrNull()?.streamUrl ?: ""
        val durationMs = if (multiTrackInfo.totalDurationMs > 0) multiTrackInfo.totalDurationMs else (cached?.duration ?: 0L)
        val synopsis = media?.metadata?.description?.takeIf { it.isNotBlank() }
            ?: audioFiles?.firstOrNull()?.metadata?.tagComment?.takeIf { it.isNotBlank() }
            ?: cached?.description

        if (cached != null) {
            val updated = cached.copy(
                path = primaryStreamUrl,
                duration = durationMs,
                description = synopsis
            )
            db.cachedBookDao().upsertAll(listOf(updated))
        }

        Log.d(TAG, "Successfully resolved ${multiTrackInfo.tracks.size} stream tracks for $mediaId: ${sanitizeUrlForLog(primaryStreamUrl)} (duration=${durationMs}ms)")
        StreamResolution.Success(multiTrackInfo, synopsis)
    }

    suspend fun resolveStreamUrl(mediaId: String): String? {
        return when (val res = resolveStreamDetails(mediaId)) {
            is StreamResolution.Success -> res.streamUrl
            else -> null
        }
    }

    suspend fun syncPlaybackProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        isFinished: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!mediaId.startsWith("abs_")) return@withContext false

        val absItemId = mediaId.removePrefix("abs_")
        val serverUrl = getServerUrl()
        val token = getToken()

        if (serverUrl.isNullOrBlank() || token.isNullOrBlank()) return@withContext false

        val posSec = positionMs.toDouble() / 1000.0
        val durSec = durationMs.toDouble() / 1000.0

        AudiobookshelfClient.syncProgress(serverUrl, token, absItemId, posSec, durSec, isFinished)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        val db = AppDatabase.getDatabase(context)
        try {
            db.cachedBookDao().deleteRemoteStreamingBooks()
            Log.d(TAG, "Successfully disconnected and cleaned remote streaming books from Room DB")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning books during disconnect", e)
        }
    }
}
