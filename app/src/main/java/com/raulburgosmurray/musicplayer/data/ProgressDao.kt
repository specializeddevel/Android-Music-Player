package com.raulburgosmurray.musicplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProgressDao {
    @Query("SELECT * FROM audiobook_progress WHERE mediaId = :mediaId")
    suspend fun getProgress(mediaId: String): AudiobookProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: AudiobookProgress)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progressList: List<AudiobookProgress>)

    @Query("SELECT * FROM audiobook_progress ORDER BY lastUpdated DESC")
    suspend fun getAllProgress(): List<AudiobookProgress>

    @Query("SELECT * FROM audiobook_progress")
    fun getAllProgressFlow(): kotlinx.coroutines.flow.Flow<List<AudiobookProgress>>

    @Query("SELECT * FROM audiobook_progress WHERE isRead = 1")
    fun getReadBooksFlow(): kotlinx.coroutines.flow.Flow<List<AudiobookProgress>>

    @Query("UPDATE audiobook_progress SET isRead = :isRead, lastUpdated = :timestamp WHERE mediaId = :mediaId")
    suspend fun updateReadStatus(mediaId: String, isRead: Boolean, timestamp: Long = System.currentTimeMillis())
}
