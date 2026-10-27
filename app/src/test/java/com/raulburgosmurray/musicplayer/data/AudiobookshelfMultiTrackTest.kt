package com.raulburgosmurray.musicplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookshelfMultiTrackTest {

    @Test
    fun `converts global position to track index and offset accurately using MultiTrackHelper`() {
        val tracks = listOf(
            BookTrack(index = 0, ino = "0", streamUrl = "http://srv/0", startOffsetMs = 0L, durationMs = 3_600_000L),       // 0 to 1h
            BookTrack(index = 1, ino = "1", streamUrl = "http://srv/1", startOffsetMs = 3_600_000L, durationMs = 3_600_000L), // 1h to 2h
            BookTrack(index = 2, ino = "2", streamUrl = "http://srv/2", startOffsetMs = 7_200_000L, durationMs = 3_600_000L)  // 2h to 3h
        )
        val info = MultiTrackInfo(bookId = "abs_123", totalDurationMs = 10_800_000L, tracks = tracks)

        // 1. Test position at 1h 30m (5,400,000 ms) -> Track 1 at 30m (1,800,000 ms)
        val (idx, posInTrack) = MultiTrackHelper.globalPositionToTrack(info, 5_400_000L)
        assertEquals(1, idx)
        assertEquals(1_800_000L, posInTrack)

        // 2. Test converting Track 1 at 30m back to global position -> 5,400,000 ms
        val globalPos = MultiTrackHelper.trackPositionToGlobal(info, 1, 1_800_000L)
        assertEquals(5_400_000L, globalPos)
    }

    @Test
    fun `completing intermediate track does not mark entire book as read`() {
        val totalDurationMs = 10_800_000L // 3 hours

        // Reaching end of track 1 (1h = 3,600,000 ms = 33% of book)
        val atEndOfTrack1 = MultiTrackHelper.isBookCompleted(3_600_000L, totalDurationMs)
        assertFalse("End of track 1 must NOT mark entire 3-hour book as completed", atEndOfTrack1)

        // Reaching end of track 2 (2h = 7,200,000 ms = 66% of book)
        val atEndOfTrack2 = MultiTrackHelper.isBookCompleted(7_200_000L, totalDurationMs)
        assertFalse("End of track 2 must NOT mark entire 3-hour book as completed", atEndOfTrack2)

        // Reaching 99.5% of book (10,750,000 ms)
        val nearEndOfBook = MultiTrackHelper.isBookCompleted(10_750_000L, totalDurationMs)
        assertTrue("Reaching >= 99% of total book duration MUST mark book as completed", nearEndOfBook)
    }
}
