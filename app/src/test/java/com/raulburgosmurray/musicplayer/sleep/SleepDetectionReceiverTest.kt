package com.raulburgosmurray.musicplayer.sleep

import java.io.Reader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepDetectionReceiverTest {

    @Test
    fun `body reader joins fragmented socket reads`() {
        val body = "{\"sleepOnsetMinutes\":12}"

        assertEquals(body, readExactBody(FragmentedReader(body), body.length))
    }

    @Test
    fun `body reader rejects a connection closed before content length`() {
        assertNull(readExactBody(FragmentedReader("short"), 20))
    }

    @Test
    fun `HTTP response contains real CRLF separators`() {
        val response = buildHttpResponse(200, "OK")

        assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(response.endsWith("\r\n\r\n"))
    }

    private class FragmentedReader(private val value: String) : Reader() {
        private var index = 0

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (index >= value.length) return -1
            buffer[offset] = value[index++]
            return 1
        }

        override fun close() = Unit
    }
}
