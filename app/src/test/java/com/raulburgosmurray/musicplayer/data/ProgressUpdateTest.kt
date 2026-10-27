package com.raulburgosmurray.musicplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProgressUpdateTest {

    @Test
    fun `update creates defaults for a book without progress`() {
        val result = mergeProgress(
            existing = null,
            mediaId = "new-book",
            update = ProgressUpdate(playbackSpeed = 1.5f, pitch = 0.9f),
            timestamp = 123L
        )

        assertEquals("new-book", result.mediaId)
        assertEquals(0L, result.lastPosition)
        assertEquals(0L, result.duration)
        assertEquals(1.5f, result.playbackSpeed)
        assertEquals(0.9f, result.pitch)
        assertEquals(123L, result.lastUpdated)
    }

    @Test
    fun `partial update preserves unrelated progress fields`() {
        val existing = AudiobookProgress(
            mediaId = "book",
            lastPosition = 42_000L,
            duration = 90_000L,
            lastUpdated = 10L,
            lastPauseTimestamp = 11L,
            playbackSpeed = 1.2f,
            pitch = 1.0f,
            eqPresetName = "VOICE",
            isRead = true
        )

        val result = mergeProgress(
            existing = existing,
            mediaId = "book",
            update = ProgressUpdate(pitch = 1.1f),
            timestamp = 20L
        )

        assertEquals(42_000L, result.lastPosition)
        assertEquals(90_000L, result.duration)
        assertEquals(11L, result.lastPauseTimestamp)
        assertEquals(1.2f, result.playbackSpeed)
        assertEquals(1.1f, result.pitch)
        assertEquals("VOICE", result.eqPresetName)
        assertEquals(true, result.isRead)
        assertEquals(20L, result.lastUpdated)
    }

    @Test
    fun `snapshot can clear pause timestamp without clearing other settings`() {
        val existing = AudiobookProgress(
            mediaId = "book",
            lastPosition = 10L,
            duration = 100L,
            lastPauseTimestamp = 99L,
            playbackSpeed = 1.75f,
            pitch = 0.8f,
            eqPresetName = "BASS",
            isRead = false
        )

        val result = mergeProgress(
            existing,
            "book",
            ProgressUpdate(lastPosition = 50L, duration = 100L, lastPauseTimestamp = 0L),
            timestamp = 200L
        )

        assertEquals(50L, result.lastPosition)
        assertEquals(0L, result.lastPauseTimestamp)
        assertEquals(1.75f, result.playbackSpeed)
        assertEquals(0.8f, result.pitch)
        assertFalse(result.isRead)
    }
}
