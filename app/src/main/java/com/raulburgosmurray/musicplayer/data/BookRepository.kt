package com.raulburgosmurray.musicplayer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookRepository(private val cachedBookDao: CachedBookDao) {

    fun getAllBooks(): Flow<List<CachedBook>> = cachedBookDao.getAllBooks()

    suspend fun getBookById(id: String): CachedBook? = cachedBookDao.getBookById(id)

    suspend fun getSampleBooks(): List<SampleBook> = cachedBookDao.getSampleBooks()

    suspend fun saveBooks(books: List<CachedBook>) = cachedBookDao.upsertAll(books)

    suspend fun clearCache() = cachedBookDao.clearCache()
}
