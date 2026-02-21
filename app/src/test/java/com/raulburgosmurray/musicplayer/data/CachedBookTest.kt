package com.raulburgosmurray.musicplayer.data

import com.raulburgosmurray.musicplayer.Music
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedBookTest {

    @Test
    fun `CachedBook toMusic conversion preserves all fields`() {
        val cachedBook = CachedBook(
            id = "test-id",
            title = "Test Title",
            album = "Test Album",
            artist = "Test Artist",
            duration = 1000000L,
            path = "/path/to/file.mp3",
            artUri = "content://art",
            fileSize = 5000000L,
            fileName = "file.mp3"
        )

        val music = cachedBook.toMusic()

        assertEquals("test-id", music.id)
        assertEquals("Test Title", music.title)
        assertEquals("Test Album", music.album)
        assertEquals("Test Artist", music.artist)
        assertEquals(1000000L, music.duration)
        assertEquals("/path/to/file.mp3", music.path)
        assertEquals("content://art", music.artUri)
        assertEquals(5000000L, music.fileSize)
        assertEquals("file.mp3", music.fileName)
    }

    @Test
    fun `toCachedBook conversion preserves all fields`() {
        val music = Music(
            id = "test-id",
            title = "Test Title",
            album = "Test Album",
            artist = "Test Artist",
            duration = 1000000L,
            path = "/path/to/file.mp3",
            artUri = "content://art",
            fileSize = 5000000L,
            fileName = "file.mp3"
        )

        val cachedBook = music.toCachedBook()

        assertEquals("test-id", cachedBook.id)
        assertEquals("Test Title", cachedBook.title)
        assertEquals("Test Album", cachedBook.album)
        assertEquals("Test Artist", cachedBook.artist)
        assertEquals(1000000L, cachedBook.duration)
        assertEquals("/path/to/file.mp3", cachedBook.path)
        assertEquals("content://art", cachedBook.artUri)
        assertEquals(5000000L, cachedBook.fileSize)
        assertEquals("file.mp3", cachedBook.fileName)
    }

    @Test
    fun `CachedBook handles null artUri`() {
        val cachedBook = CachedBook(
            id = "test-id",
            title = "Test",
            album = "Album",
            artist = "Artist",
            duration = 1000L,
            path = "/path",
            artUri = null,
            fileSize = 1000L,
            fileName = "file.mp3"
        )

        val music = cachedBook.toMusic()

        assertNull(music.artUri)
    }

    @Test
    fun `Music handles null optional fields in toCachedBook`() {
        val music = Music(
            id = "test-id",
            title = "Test",
            album = "Album",
            artist = "Artist",
            path = "/path",
            artUri = null,
            fileSize = 0,
            fileName = ""
        )

        val cachedBook = music.toCachedBook()

        assertNull(cachedBook.artUri)
        assertEquals("", cachedBook.fileName)
    }
}
