package com.raulburgosmurray.musicplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class SampleBook(val id: String, val title: String)

@Dao
interface CachedBookDao {
    @Query("SELECT * FROM cached_books")
    fun getAllBooks(): Flow<List<CachedBook>>

    @Query("SELECT COUNT(*) FROM cached_books")
    suspend fun getBookCount(): Int

    @Query("SELECT * FROM cached_books WHERE id = :id")
    suspend fun getBookById(id: String): CachedBook?

    @Query("SELECT id, title FROM cached_books LIMIT 5")
    suspend fun getSampleBooks(): List<SampleBook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(books: List<CachedBook>)

    @Query("DELETE FROM cached_books")
    suspend fun clearCache()

    @Query("UPDATE cached_books SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: String, description: String)

    @Query("DELETE FROM cached_books WHERE id = :id")
    suspend fun deleteBookById(id: String)
}
