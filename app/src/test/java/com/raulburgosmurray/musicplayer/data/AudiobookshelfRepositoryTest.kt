package com.raulburgosmurray.musicplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookshelfRepositoryTest {

    @Test
    fun `normalizeUrl formats URLs correctly`() {
        assertEquals("http://193.203.182.35:13378/audiobookshelf", AudiobookshelfClient.normalizeUrl("http://193.203.182.35:13378/audiobookshelf/"))
        assertEquals("http://193.203.182.35:13378", AudiobookshelfClient.normalizeUrl("193.203.182.35:13378"))
        assertEquals("https://my-server.com", AudiobookshelfClient.normalizeUrl("https://my-server.com/"))
    }

    @Test
    fun `buildCoverUrl constructs valid cover endpoints`() {
        val coverUrl = AudiobookshelfClient.buildCoverUrl(
            baseUrl = "http://193.203.182.35:13378/audiobookshelf",
            token = "secret-token",
            itemId = "item-123"
        )
        assertEquals("http://193.203.182.35:13378/audiobookshelf/api/items/item-123/cover?token=secret-token", coverUrl)
    }

    @Test
    fun `buildAudioStreamUrl constructs valid file stream endpoints`() {
        val streamUrlWithIno = AudiobookshelfClient.buildAudioStreamUrl(
            baseUrl = "http://193.203.182.35:13378/audiobookshelf",
            token = "secret-token",
            itemId = "item-123",
            audioFileIno = "ino-456"
        )
        assertEquals("http://193.203.182.35:13378/audiobookshelf/api/items/item-123/file/ino-456?token=secret-token", streamUrlWithIno)

        val streamUrlFallback = AudiobookshelfClient.buildAudioStreamUrl(
            baseUrl = "http://193.203.182.35:13378/audiobookshelf",
            token = "secret-token",
            itemId = "item-123",
            audioFileIno = null
        )
        assertEquals("http://193.203.182.35:13378/audiobookshelf/api/items/item-123/file/0?token=secret-token", streamUrlFallback)
    }
}
