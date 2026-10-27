package com.raulburgosmurray.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekInteractionTest {

    @Test
    fun `seek target is clamped to a valid duration`() {
        assertEquals(60_000L, seekPositionForFraction(1.5f, 60_000L))
        assertEquals(0L, seekPositionForFraction(-1f, 60_000L))
        assertEquals(30_000L, seekPositionForFraction(0.5f, 60_000L))
    }

    @Test
    fun `invalid duration cannot produce a seek`() {
        assertEquals(null, seekPositionForFractionOrNull(0.5f, 0L))
        assertEquals(null, seekPositionForFractionOrNull(0.5f, -1L))
    }

    @Test
    fun `time fields produce a position only within the audiobook duration`() {
        assertEquals(3_723_000L, parseSeekTimeMs("1", "2", "3", 7_200_000L))
        assertEquals(0L, parseSeekTimeMs("", "", "", 7_200_000L))
        assertEquals(null, parseSeekTimeMs("2", "0", "1", 7_200_000L))
    }

    @Test
    fun `time fields reject invalid minutes seconds and numeric overflow`() {
        assertEquals(null, parseSeekTimeMs("0", "60", "0", 7_200_000L))
        assertEquals(null, parseSeekTimeMs("0", "0", "60", 7_200_000L))
        assertEquals(null, parseSeekTimeMs("999999999999999999999", "0", "0", Long.MAX_VALUE))
    }

    @Test
    fun `skip amount is accepted only when resulting position remains in range`() {
        assertEquals(120_000L, validatedSkipAmountMs(300_000L, 600_000L, 2L, 60_000L, true))
        assertEquals(120_000L, validatedSkipAmountMs(300_000L, 600_000L, 2L, 60_000L, false))
        assertEquals(null, validatedSkipAmountMs(590_000L, 600_000L, 1L, 60_000L, true))
        assertEquals(null, validatedSkipAmountMs(10_000L, 600_000L, 1L, 60_000L, false))
        assertEquals(null, validatedSkipAmountMs(300_000L, 600_000L, 0L, 60_000L, true))
    }
}
