package com.raulburgosmurray.musicplayer.sleep

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Servidor HTTP simple que escucha notificaciones del reloj Amazfit
 * sobre eventos de detección de sueño.
 *
 * Corre en un thread separado y expone un callback para manejar eventos.
 */
class SleepDetectionReceiver(
    private val context: Context,
    private val onSleepDetected: (sleepOnsetMinutes: Int) -> Unit
) {

    private var serverSocket: ServerSocket? = null
    private var receiverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Inicia el servidor en el puerto especificado.
     * @param port Puerto donde escuchar (default: 50002)
     */
    fun startListening(port: Int = 50002) {
        stopListening()

        receiverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                serverSocket?.soTimeout = SOCKET_ACCEPT_TIMEOUT_MS
                Log.i(TAG, "SleepDetectionReceiver listening on port $port")

                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        handleConnection(socket)
                    } catch (e: SocketTimeoutException) {
                        // Timeout es normal, continuar loop
                        continue
                    } catch (e: Exception) {
                        if (isActive) Log.e(TAG, "Error accepting connection", e)
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
            } finally {
                Log.i(TAG, "SleepDetectionReceiver stopped")
            }
        }
    }

    /**
     * Detiene el servidor.
     */
    fun stopListening() {
        receiverJob?.cancel()
        receiverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleConnection(socket: Socket) {
        try {
            socket.soTimeout = SOCKET_READ_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(socket.inputStream))

            // Leer primera línea (request line)
            val requestLine = reader.readLine() ?: return
            Log.d(TAG, "Received request: $requestLine")

            // Verificar que sea POST /sleep
            if (!requestLine.startsWith("POST /sleep")) {
                sendResponse(socket, 404, "Not Found")
                return
            }

            // Leer headers
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line!!.startsWith("Content-Length:")) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            // Leer body
            val body = CharArray(contentLength)
            reader.read(body, 0, contentLength)
            val bodyString = String(body)
            Log.d(TAG, "Request body: $bodyString")

            // Parsear JSON
            try {
                val data = Json.decodeFromString<SleepNotification>(bodyString)
                Log.i(TAG, "Sleep detected at minute ${data.sleepOnsetMinutes}")

                // Notificar al handler principal
                onSleepDetected(data.sleepOnsetMinutes)

                sendResponse(socket, 200, "OK")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse request body", e)
                sendResponse(socket, 400, "Bad Request")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling connection", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendResponse(socket: Socket, code: Int, message: String) {
        try {
            val response = """HTTP/1.1 $code $message\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"""
            socket.getOutputStream().write(response.toByteArray())
        } catch (_: Exception) {}
    }

    @Serializable
    data class SleepNotification(
        val sleepOnsetMinutes: Int,
        val timestamp: Long
    )

    companion object {
        private const val TAG = "SleepDetectionReceiver"
        private const val SOCKET_ACCEPT_TIMEOUT_MS = 5000
        private const val SOCKET_READ_TIMEOUT_MS = 10000
    }
}
