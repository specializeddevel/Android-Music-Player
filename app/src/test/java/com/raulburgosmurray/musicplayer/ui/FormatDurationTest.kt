package com.raulburgosmurray.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {

    @Test
    fun `formatDuration returns 00 00 for zero`() {
        val result = formatDuration(0)
        assertEquals("00:00", result)
    }

    @Test
    fun `formatDuration returns seconds only for less than hour`() {
        val result = formatDuration(65000) // 1:05
        assertEquals("01:05", result)
    }

    @Test
    fun `formatDuration returns hours for duration over an hour`() {
        val result = formatDuration(3665000) // 1:01:05
        assertEquals("1:01:05", result)
    }

    @Test
    fun `formatDuration handles exactly one minute`() {
        val result = formatDuration(60000) // 1:00
        assertEquals("01:00", result)
    }

    @Test
    fun `formatDuration handles exactly one hour`() {
        val result = formatDuration(3600000) // 1:00:00
        assertEquals("1:00:00", result)
    }
}
