package com.raulburgosmurray.musicplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicTest {

    @Test
    fun `Music data class has all fields`() {
        val music = Music(
            id = "test-id",
            title = "Test Title",
            album = "Test Album",
            artist = "Test Artist",
            duration = 1000000,
            path = "/path/to/file.mp3",
            artUri = "content://art",
            fileSize = 5000000,
            fileName = "file.mp3",
            trackMore = "track info",
            comment = "a comment"
        )

        assertEquals("test-id", music.id)
        assertEquals("Test Title", music.title)
        assertEquals("Test Album", music.album)
        assertEquals("Test Artist", music.artist)
        assertEquals(1000000, music.duration)
        assertEquals("/path/to/file.mp3", music.path)
        assertEquals("content://art", music.artUri)
        assertEquals(5000000, music.fileSize)
        assertEquals("file.mp3", music.fileName)
        assertEquals("track info", music.trackMore)
        assertEquals("a comment", music.comment)
    }

    @Test
    fun `Music data class has default values for optional fields`() {
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

        assertEquals(0L, music.duration)
        assertNull(music.artUri)
        assertEquals(0L, music.fileSize)
        assertEquals("", music.fileName)
        assertNull(music.trackMore)
        assertNull(music.comment)
    }

    @Test
    fun `Music copy works correctly`() {
        val original = Music(
            id = "test-id",
            title = "Original Title",
            album = "Album",
            artist = "Artist",
            path = "/path",
            artUri = null,
            fileSize = 0,
            fileName = ""
        )

        val copy = original.copy(title = "New Title")

        assertEquals("New Title", copy.title)
        assertEquals("test-id", copy.id)
        assertEquals("Album", copy.album)
    }

    @Test
    fun `Music equals works correctly`() {
        val music1 = Music(
            id = "test-id",
            title = "Test",
            album = "Album",
            artist = "Artist",
            path = "/path",
            artUri = null,
            fileSize = 0,
            fileName = ""
        )

        val music2 = Music(
            id = "test-id",
            title = "Test",
            album = "Album",
            artist = "Artist",
            path = "/path",
            artUri = null,
            fileSize = 0,
            fileName = ""
        )

        assertEquals(music1, music2)
    }
}
