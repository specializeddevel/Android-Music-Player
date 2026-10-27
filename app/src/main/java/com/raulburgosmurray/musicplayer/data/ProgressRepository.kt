package com.raulburgosmurray.musicplayer.data

import kotlinx.coroutines.flow.Flow

class ProgressRepository(private val progressDao: ProgressDao) {

    suspend fun getProgress(mediaId: String): AudiobookProgress? = 
        progressDao.getProgress(mediaId)

    suspend fun saveProgress(progress: AudiobookProgress) = 
        progressDao.saveProgress(progress)

    suspend fun applyUpdate(
        mediaId: String,
        update: ProgressUpdate,
        timestamp: Long = System.currentTimeMillis()
    ): AudiobookProgress = progressDao.applyUpdate(mediaId, update, timestamp)

    suspend fun saveAllProgress(progressList: List<AudiobookProgress>) = 
        progressDao.upsertAll(progressList)

    suspend fun getAllProgress(): List<AudiobookProgress> = 
        progressDao.getAllProgress()

    fun getAllProgressFlow(): Flow<List<AudiobookProgress>> = 
        progressDao.getAllProgressFlow()

    fun getReadBooksFlow(): Flow<List<AudiobookProgress>> =
        progressDao.getReadBooksFlow()

    suspend fun setReadStatus(mediaId: String, isRead: Boolean) {
        progressDao.applyUpdate(
            mediaId,
            ProgressUpdate(isRead = isRead, lastPosition = if (isRead) null else 0L)
        )
    }

    suspend fun deleteProgress(mediaId: String) = progressDao.deleteProgress(mediaId)
}
