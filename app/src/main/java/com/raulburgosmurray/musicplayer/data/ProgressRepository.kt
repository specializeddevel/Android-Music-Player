package com.raulburgosmurray.musicplayer.data

import kotlinx.coroutines.flow.Flow

class ProgressRepository(private val progressDao: ProgressDao) {

    suspend fun getProgress(mediaId: String): AudiobookProgress? = 
        progressDao.getProgress(mediaId)

    suspend fun saveProgress(progress: AudiobookProgress) = 
        progressDao.saveProgress(progress)

    suspend fun saveAllProgress(progressList: List<AudiobookProgress>) = 
        progressDao.upsertAll(progressList)

    suspend fun getAllProgress(): List<AudiobookProgress> = 
        progressDao.getAllProgress()

    fun getAllProgressFlow(): Flow<List<AudiobookProgress>> = 
        progressDao.getAllProgressFlow()

    fun getReadBooksFlow(): Flow<List<AudiobookProgress>> =
        progressDao.getReadBooksFlow()

    suspend fun setReadStatus(mediaId: String, isRead: Boolean) {
        val existing = progressDao.getProgress(mediaId)
        if (existing != null) {
            progressDao.updateReadStatus(mediaId, isRead)
            // If marking as unread, reset progress to zero
            if (!isRead && existing.lastPosition > 0) {
                progressDao.saveProgress(existing.copy(lastPosition = 0L, lastUpdated = System.currentTimeMillis()))
            }
        } else {
            // Create new progress entry with isRead flag
            progressDao.saveProgress(
                AudiobookProgress(
                    mediaId = mediaId,
                    lastPosition = 0L,
                    duration = 0L,
                    isRead = isRead
                )
            )
        }
    }
}
