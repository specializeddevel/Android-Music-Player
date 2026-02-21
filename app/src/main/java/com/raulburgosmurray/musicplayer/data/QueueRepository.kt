package com.raulburgosmurray.musicplayer.data

import kotlinx.coroutines.flow.Flow

class QueueRepository(private val queueDao: QueueDao) {

    fun getAllQueueItems(): Flow<List<QueueItem>> = queueDao.getAllQueueItems()

    suspend fun getQueueSnapshot(): List<QueueItem> = queueDao.getQueueSnapshot()

    suspend fun updateFullQueue(items: List<QueueItem>) = queueDao.updateFullQueue(items)

    suspend fun clearQueue() = queueDao.clearQueue()
}
