package com.raulburgosmurray.musicplayer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferProtocolTest {
    @Test
    fun `secure header preserves first audio byte`() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply { writeUTF("book.mp3"); writeLong(123); writeByte(42) }
        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(TransferHeader("book.mp3", 123), TransferProtocol.readSecureHeader(input))
        assertEquals(42, input.read())
    }

    @Test
    fun `old sender closing connection produces protocol not confirmed error`() {
        assertThrows(TransferProtocolNotConfirmedException::class.java) {
            TransferProtocol.readSecureHeader(DataInputStream(ByteArrayInputStream(byteArrayOf())))
        }
    }

    @Test
    fun `truncated header produces protocol not confirmed error`() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).writeUTF("book.mp3")
        assertThrows(TransferProtocolNotConfirmedException::class.java) {
            TransferProtocol.readSecureHeader(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
        }
    }
}
