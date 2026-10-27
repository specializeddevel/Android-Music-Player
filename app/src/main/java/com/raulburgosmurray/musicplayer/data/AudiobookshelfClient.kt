package com.raulburgosmurray.musicplayer.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "AudiobookshelfClient"

object AudiobookshelfClient {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun normalizeUrl(rawUrl: String): String {
        var clean = rawUrl.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return clean
    }

    suspend fun login(rawBaseUrl: String, username: String, password: String): Result<AbsUser> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeUrl(rawBaseUrl)
        
        // Audiobookshelf login endpoints to try (direct or with /audiobookshelf prefix)
        val loginEndpoints = listOf(
            if (baseUrl.endsWith("/login")) baseUrl else "$baseUrl/login",
            if (baseUrl.endsWith("/audiobookshelf")) "$baseUrl/login" else "$baseUrl/audiobookshelf/login"
        ).distinct()

        val jsonBody = json.encodeToString(AbsLoginRequest(username, password))
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        var lastException: Exception? = null

        for (endpoint in loginEndpoints) {
            try {
                Log.d(TAG, "Trying login at $endpoint")
                val request = Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseText = response.body?.string() ?: ""
                        val loginResponse = json.decodeFromString<AbsLoginResponse>(responseText)
                        val user = loginResponse.user
                        if (user != null && user.token.isNotBlank()) {
                            Log.d(TAG, "Login successful for user ${user.username}")
                            return@withContext Result.success(user)
                        } else {
                            val err = loginResponse.error ?: "Invalid response from server"
                            return@withContext Result.failure(Exception(err))
                        }
                    } else if (response.code == 401 || response.code == 400) {
                        return@withContext Result.failure(Exception("Credenciales incorrectas (HTTP ${response.code})"))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Login attempt failed at $endpoint", e)
                lastException = e
            }
        }

        val errorMsg = when (lastException) {
            is java.net.SocketTimeoutException -> "Tiempo de espera agotado. Verifica la dirección IP y que el puerto sea el 13378."
            is java.net.ConnectException -> "Conexión rechazada. Verifica que el servidor esté encendido y el puerto sea correcto."
            else -> lastException?.localizedMessage ?: "No se pudo conectar al servidor de Audiobookshelf"
        }
        Result.failure(Exception(errorMsg))
    }

    suspend fun getLibraries(baseUrl: String, token: String): Result<List<AbsLibrary>> = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeUrl(baseUrl).removeSuffix("/login")
        val endpoint = "$cleanUrl/api/libraries"

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    val libsResponse = json.decodeFromString<AbsLibrariesResponse>(responseText)
                    Result.success(libsResponse.libraries)
                } else {
                    Result.failure(Exception("Error al obtener bibliotecas (HTTP ${response.code})"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLibraries", e)
            Result.failure(e)
        }
    }

    suspend fun getLibraryItems(baseUrl: String, token: String, libraryId: String, limit: Int = 0): Result<List<AbsLibraryItem>> = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeUrl(baseUrl).removeSuffix("/login")
        val endpoint = "$cleanUrl/api/libraries/$libraryId/items?expanded=1&limit=$limit"

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    val itemsResponse = json.decodeFromString<AbsLibraryItemsResponse>(responseText)
                    Result.success(itemsResponse.results)
                } else {
                    Result.failure(Exception("Error al obtener audiolibros (HTTP ${response.code})"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getLibraryItems", e)
            Result.failure(e)
        }
    }

    suspend fun getItemDetail(baseUrl: String, token: String, itemId: String): Result<AbsLibraryItem> = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeUrl(baseUrl).removeSuffix("/login")
        val endpoint = "$cleanUrl/api/items/$itemId"

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseText = response.body?.string() ?: ""
                    val itemDetail = json.decodeFromString<AbsLibraryItem>(responseText)
                    Result.success(itemDetail)
                } else {
                    Result.failure(Exception("Error al obtener detalle de libro (HTTP ${response.code})"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getItemDetail for $itemId", e)
            Result.failure(e)
        }
    }

    suspend fun syncProgress(
        baseUrl: String,
        token: String,
        itemId: String,
        currentTimeSec: Double,
        durationSec: Double,
        isFinished: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeUrl(baseUrl).removeSuffix("/login")
        val endpoint = "$cleanUrl/api/me/progress/$itemId"

        val progressVal = if (durationSec > 0) (currentTimeSec / durationSec).coerceIn(0.0, 1.0) else 0.0
        val payload = AbsProgressPayload(
            currentTime = currentTimeSec,
            duration = durationSec,
            progress = progressVal,
            isFinished = isFinished
        )

        try {
            val jsonBody = json.encodeToString(payload)
            val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .patch(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Progress synced for $itemId: ${currentTimeSec}s / ${durationSec}s")
                    true
                } else {
                    Log.w(TAG, "Failed to sync progress for $itemId (HTTP ${response.code})")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing progress for $itemId", e)
            false
        }
    }

    fun buildCoverUrl(baseUrl: String, token: String, itemId: String): String {
        val cleanUrl = normalizeUrl(baseUrl).removeSuffix("/login")
        return "$cleanUrl/api/items/$itemId/cover?token=$token"
    }

    fun buildAudioStreamUrl(baseUrl: String, token: String, itemId: String, audioFileIno: String?): String {
        val cleanUrl = normalizeUrl(baseUrl).removeSuffix("/login")
        return if (!audioFileIno.isNullOrBlank()) {
            "$cleanUrl/api/items/$itemId/file/$audioFileIno?token=$token"
        } else {
            "$cleanUrl/api/items/$itemId/file/0?token=$token"
        }
    }

    fun buildAudioStreamUrls(baseUrl: String, token: String, itemId: String, audioFiles: List<AbsAudioFile>?): List<String> {
        val cleanUrl = normalizeUrl(baseUrl).removeSuffix("/login")
        if (audioFiles.isNullOrEmpty()) {
            return listOf("$cleanUrl/api/items/$itemId/file/0?token=$token")
        }
        return audioFiles.map { af ->
            val ino = af.ino ?: "0"
            "$cleanUrl/api/items/$itemId/file/$ino?token=$token"
        }
    }
}
