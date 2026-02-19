package com.raulburgosmurray.musicplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedBookDao {
    @Query("SELECT * FROM cached_books")
    fun getAllBooks(): Flow<List<CachedBook>>

    @Query("SELECT * FROM cached_books WHERE id = :id")
    suspend fun getBookById(id: String): CachedBook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(books: List<CachedBook>)

    @Query("DELETE FROM cached_books")
    suspend fun clearCache()
}
