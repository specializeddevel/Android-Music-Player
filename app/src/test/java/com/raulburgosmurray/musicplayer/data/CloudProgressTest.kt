package com.raulburgosmurray.musicplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CloudProgressTest {

    @Test
    fun `portable key does not depend on the device local media id`() {
        val firstDevice = sampleBook(id = "content://media/external/audio/media/12")
        val secondDevice = sampleBook(id = "content://media/external/audio/media/987")

        assertEquals(portableBookKey(firstDevice), portableBookKey(secondDevice))
    }

    @Test
    fun `portable key changes for a different audio file`() {
        val first = sampleBook(id = "one")
        val second = sampleBook(id = "two").copy(fileName = "other.m4b", fileSize = 9_999L)

        assertNotEquals(portableBookKey(first), portableBookKey(second))
    }

    @Test
    fun `cloud progress resolves to the receiving device media id`() {
        val sourceBook = sampleBook(id = "source-local-id")
        val targetBook = sampleBook(id = "target-local-id")
        val progress = AudiobookProgress(
            mediaId = sourceBook.id,
            lastPosition = 400L,
            duration = 1_000L,
            lastUpdated = 20L
        )

        val restored = progress.toCloudProgress(sourceBook).toLocalProgress(targetBook.id)

        assertEquals("target-local-id", restored.mediaId)
        assertEquals(400L, restored.lastPosition)
    }

    private fun sampleBook(id: String) = CachedBook(
        id = id,
        title = "The Book",
        album = "The Series",
        artist = "The Author",
        duration = 1_000L,
        path = id,
        artUri = null,
        fileSize = 5_000L,
        fileName = "the-book.m4b"
    )
}
