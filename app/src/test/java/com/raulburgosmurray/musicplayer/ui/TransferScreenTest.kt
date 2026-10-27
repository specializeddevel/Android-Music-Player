package com.raulburgosmurray.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferScreenTest {
    @Test
    fun `transfer percentage is clamped to visible bounds`() {
        assertEquals(0, transferProgressPercent(-0.2f))
        assertEquals(46, transferProgressPercent(0.456f))
        assertEquals(100, transferProgressPercent(1.4f))
    }

    @Test
    fun `playback progress uses hours only when needed`() {
        assertEquals("1:05 / 2:00", formatTransferPlaybackProgress(65_000, 120_000))
        assertEquals("1:01:01 / 2:00:00", formatTransferPlaybackProgress(3_661_000, 7_200_000))
    }
}
