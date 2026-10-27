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

    @Test
    fun `edited metadata updates every editable library field`() {
        val cachedBook = sampleBook()
        val metadata = sampleMetadata(
            title = "Edited title",
            artist = "Edited author",
            album = "Edited album",
            artUri = "file://edited-cover.jpg",
            userEditedFields = setOf(
                AudioMetadata.FIELD_TITLE,
                AudioMetadata.FIELD_ARTIST,
                AudioMetadata.FIELD_ALBUM,
                AudioMetadata.FIELD_ART_URI
            )
        )

        val updated = cachedBook.withEditedMetadata(metadata)

        assertEquals("Edited title", updated.title)
        assertEquals("Edited author", updated.artist)
        assertEquals("Edited album", updated.album)
        assertEquals("file://edited-cover.jpg", updated.artUri)
    }

    @Test
    fun `metadata fields not edited by the user do not replace scanned values`() {
        val cachedBook = sampleBook()
        val metadata = sampleMetadata(
            title = "Extractor title",
            artist = "Extractor author",
            album = "Extractor album",
            artUri = "file://extractor-cover.jpg",
            userEditedFields = setOf(AudioMetadata.FIELD_TITLE)
        )

        val updated = cachedBook.withEditedMetadata(metadata)

        assertEquals("Extractor title", updated.title)
        assertEquals("Original author", updated.artist)
        assertEquals("Original album", updated.album)
        assertEquals("file://original-cover.jpg", updated.artUri)
    }

    private fun sampleBook() = CachedBook(
        id = "test-id",
        title = "Original title",
        album = "Original album",
        artist = "Original author",
        duration = 1_000L,
        path = "/path/file.mp3",
        artUri = "file://original-cover.jpg",
        fileSize = 2_000L,
        fileName = "file.mp3"
    )

    private fun sampleMetadata(
        title: String,
        artist: String,
        album: String,
        artUri: String?,
        userEditedFields: Set<String>
    ) = AudioMetadata(
        mediaId = "test-id",
        title = title,
        artist = artist,
        album = album,
        year = null,
        genre = null,
        trackNumber = null,
        comment = null,
        artUri = artUri,
        duration = 1_000L,
        fileName = "file.mp3",
        userEditedFields = userEditedFields
    )
}
