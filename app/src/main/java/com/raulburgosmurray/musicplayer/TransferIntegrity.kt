package com.raulburgosmurray.musicplayer

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

data class TransferCopyResult(
    val bytesCopied: Long,
    val sha256: String
)

object TransferIntegrity {
    private const val BUFFER_SIZE = 64 * 1024
    private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")

    fun copyExactly(
        input: InputStream,
        output: OutputStream,
        expectedBytes: Long,
        onProgress: (Long) -> Unit = {}
    ): TransferCopyResult {
        require(expectedBytes >= 0L) { "El tamaño esperado no puede ser negativo" }

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L

        while (total < expectedBytes) {
            val remaining = expectedBytes - total
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) {
                throw EOFException("Transferencia incompleta: $total de $expectedBytes bytes")
            }
            if (read == 0) continue

            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            total += read
            onProgress(total)
        }

        return TransferCopyResult(
            bytesCopied = total,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    fun safeFileName(receivedName: String): String {
        val sanitized = receivedName
            .replace('\\', '/')
            .substringAfterLast('/')
            .trim()
        return sanitized.takeUnless { it.isEmpty() || it == "." || it == ".." }
            ?: "audiobook.bin"
    }

    fun sha256Matches(expected: String, actual: String): Boolean {
        return SHA256_PATTERN.matches(expected) && SHA256_PATTERN.matches(actual) &&
            expected.equals(actual, ignoreCase = true)
    }
}
