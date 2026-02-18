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

    @Query("SELECT * FROM audiobook_progress ORDER BY lastUpdated DESC")
    suspend fun getAllProgress(): List<AudiobookProgress>

    @Query("SELECT * FROM audiobook_progress")
    fun getAllProgressFlow(): kotlinx.coroutines.flow.Flow<List<AudiobookProgress>>
}
