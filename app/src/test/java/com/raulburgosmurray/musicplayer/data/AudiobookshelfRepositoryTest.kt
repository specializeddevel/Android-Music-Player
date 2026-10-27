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

    @Test
    fun `sanitizeUrlForLog redacts JWT tokens in logs`() {
        val url = "http://193.203.182.35:13378/audiobookshelf/api/items/123/file/456?token=eyJhbGciOiJIUzI1NiIn..."
        val sanitized = sanitizeUrlForLog(url)
        assertEquals("http://193.203.182.35:13378/audiobookshelf/api/items/123/file/456?token=***", sanitized)
    }

    @Test
    fun `buildAudioStreamUrls returns multi-track endpoints correctly`() {
        val files = listOf(
            AbsAudioFile(ino = "100"),
            AbsAudioFile(ino = "101")
        )
        val urls = AudiobookshelfClient.buildAudioStreamUrls("http://193.203.182.35:13378/audiobookshelf", "secret-token", "item-123", files)
        assertEquals(2, urls.size)
        assertEquals("http://193.203.182.35:13378/audiobookshelf/api/items/item-123/file/100?token=secret-token", urls[0])
        assertEquals("http://193.203.182.35:13378/audiobookshelf/api/items/item-123/file/101?token=secret-token", urls[1])
    }
}
