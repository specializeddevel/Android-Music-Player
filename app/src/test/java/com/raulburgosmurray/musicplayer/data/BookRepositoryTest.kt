package com.raulburgosmurray.musicplayer.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookRepositoryTest {

    @Test
    fun `getAllBooks returns flow from dao`() = runTest {
        val mockDao = mockk<CachedBookDao>(relaxed = true)
        val mockBooks = listOf(
            CachedBook("1", "Book 1", "Album", "Author", 1000, "path", null, 1000),
            CachedBook("2", "Book 2", "Album", "Author", 2000, "path", null, 2000)
        )
        
        coEvery { mockDao.getAllBooks() } returns flowOf(mockBooks)
        
        val repository = BookRepository(mockDao)
        
        repository.getAllBooks().collect { books ->
            assertEquals(2, books.size)
            assertEquals("Book 1", books[0].title)
        }
    }

    @Test
    fun `getBookById returns book from dao`() = runTest {
        val mockDao = mockk<CachedBookDao>(relaxed = true)
        val mockBook = CachedBook("1", "Book 1", "Album", "Author", 1000, "path", null, 1000)
        
        coEvery { mockDao.getBookById("1") } returns mockBook
        
        val repository = BookRepository(mockDao)
        
        val result = repository.getBookById("1")
        
        assertEquals("Book 1", result?.title)
    }

    @Test
    fun `getBookById returns null when not found`() = runTest {
        val mockDao = mockk<CachedBookDao>(relaxed = true)
        
        coEvery { mockDao.getBookById("nonexistent") } returns null
        
        val repository = BookRepository(mockDao)
        
        val result = repository.getBookById("nonexistent")
        
        assertNull(result)
    }

    @Test
    fun `saveBooks calls upsertAll on dao`() = runTest {
        val mockDao = mockk<CachedBookDao>(relaxed = true)
        val books = listOf(
            CachedBook("1", "Book 1", "Album", "Author", 1000, "path", null, 1000)
        )
        
        val repository = BookRepository(mockDao)
        
        repository.saveBooks(books)
        
        coVerify { mockDao.upsertAll(books) }
    }

    @Test
    fun `clearCache calls clearCache on dao`() = runTest {
        val mockDao = mockk<CachedBookDao>(relaxed = true)
        
        val repository = BookRepository(mockDao)
        
        repository.clearCache()
        
        coVerify { mockDao.clearCache() }
    }
}
