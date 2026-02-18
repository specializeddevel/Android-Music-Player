package com.raulburgosmurray.musicplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM playback_queue ORDER BY orderIndex ASC")
    fun getAllQueueItems(): Flow<List<QueueItem>>

    @Query("SELECT * FROM playback_queue ORDER BY orderIndex ASC")
    suspend fun getQueueSnapshot(): List<QueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItem>)

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()

    @Transaction
    suspend fun updateFullQueue(items: List<QueueItem>) {
        clearQueue()
        insertAll(items)
    }
}
