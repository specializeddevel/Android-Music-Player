package com.raulburgosmurray.musicplayer.data

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.client.http.ByteArrayContent
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class GoogleDriveService(context: Context, accountName: String) {

    private val driveService: Drive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_APPDATA))
        credential.selectedAccountName = accountName
        
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Litera").build()
    }

    private val SYNC_FILE_NAME = "litera_progress_v1.json"

    suspend fun uploadProgress(progressList: List<AudiobookProgress>): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = Gson().toJson(progressList)
            val content = ByteArrayContent.fromString("application/json", json)

            val existingFileId = findFileId()

            if (existingFileId != null) {
                driveService.files().update(existingFileId, null, content).execute()
            } else {
                val metadata = File().apply {
                    name = SYNC_FILE_NAME
                    parents = listOf("appDataFolder")
                }
                driveService.files().create(metadata, content).execute()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun downloadProgress(): List<AudiobookProgress>? = withContext(Dispatchers.IO) {
        try {
            val fileId = findFileId() ?: return@withContext null
            val inputStream = driveService.files().get(fileId).executeMediaAsInputStream()
            val json = inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(json, Array<AudiobookProgress>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findFileId(): String? {
        val result = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$SYNC_FILE_NAME'")
            .execute()
        return result.files.firstOrNull()?.id
    }
}
