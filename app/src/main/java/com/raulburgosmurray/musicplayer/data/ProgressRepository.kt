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
}
