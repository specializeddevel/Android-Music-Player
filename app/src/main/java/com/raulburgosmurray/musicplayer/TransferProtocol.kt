package com.raulburgosmurray.musicplayer

import java.io.DataInputStream
import java.io.IOException

data class TransferHeader(val fileName: String, val fileSize: Long)

class TransferProtocolNotConfirmedException(cause: IOException) :
    IOException("The sender did not confirm the secure transfer protocol", cause)

object TransferProtocol {
    /** A legacy sender closes after FILE_V2. A disconnect can look identical. */
    fun readSecureHeader(input: DataInputStream): TransferHeader = try {
        TransferHeader(input.readUTF(), input.readLong())
    } catch (error: IOException) {
        throw TransferProtocolNotConfirmedException(error)
    }
}
