package com.raulburgosmurray.musicplayer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class TransferIntegrityTest {

    @Test
    fun `copyExactly copies expected bytes and returns sha256`() {
        val input = ByteArrayInputStream("hello".toByteArray())
        val output = ByteArrayOutputStream()

        val result = TransferIntegrity.copyExactly(input, output, 5L)

        assertArrayEquals("hello".toByteArray(), output.toByteArray())
        assertEquals(5L, result.bytesCopied)
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            result.sha256
        )
    }

    @Test
    fun `copyExactly rejects a truncated payload`() {
        val input = ByteArrayInputStream("short".toByteArray())
        val output = ByteArrayOutputStream()

        assertThrows(EOFException::class.java) {
            TransferIntegrity.copyExactly(input, output, 10L)
        }
    }

    @Test
    fun `copyExactly does not consume protocol footer`() {
        val input = ByteArrayInputStream("audiohash".toByteArray())
        val output = ByteArrayOutputStream()

        TransferIntegrity.copyExactly(input, output, 5L)

        assertArrayEquals("audio".toByteArray(), output.toByteArray())
        assertEquals('h'.code, input.read())
    }

    @Test
    fun `safeFileName removes path traversal`() {
        assertEquals("Dune.m4a", TransferIntegrity.safeFileName("../../Dune.m4a"))
        assertEquals("audiobook.bin", TransferIntegrity.safeFileName(".."))
    }

    @Test
    fun `sha256Matches accepts same digest and rejects mismatch or malformed digest`() {
        val digest = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

        assertEquals(true, TransferIntegrity.sha256Matches(digest.uppercase(), digest))
        assertEquals(false, TransferIntegrity.sha256Matches("0".repeat(64), digest))
        assertEquals(false, TransferIntegrity.sha256Matches("not-a-hash", digest))
    }
}
