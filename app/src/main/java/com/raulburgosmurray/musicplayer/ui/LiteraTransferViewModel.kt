package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.Environment
import android.media.MediaScannerConnection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.BookRepository
import com.raulburgosmurray.musicplayer.data.ProgressRepository
import com.raulburgosmurray.musicplayer.data.AudiobookProgress
import com.raulburgosmurray.musicplayer.R
import com.raulburgosmurray.musicplayer.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.*
import java.net.*
import org.json.JSONObject

data class TransferUIState(
    val isServerRunning: Boolean = false,
    val qrData: String? = null,
    val qrPositionMs: Long = 0,
    val qrDurationMs: Long = 0,
    val localIp: String = "Detectando...",
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val transferStatus: String? = "Listo",
    val error: String? = null,
    val pendingProgress: AudiobookProgress? = null,
    val pendingBookTitle: String? = null,
    val showConflictDialog: Boolean = false,
    val targetIp: String? = null
)

class LiteraTransferViewModel(application: Application) : AndroidViewModel(application) {
    private val bookRepository = BookRepository(AppDatabase.getDatabase(application).cachedBookDao())
    private val progressRepository = ProgressRepository(AppDatabase.getDatabase(application).progressDao())
    private val _uiState = MutableStateFlow(TransferUIState())
    val uiState = _uiState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var decisionDeferred: CompletableDeferred<String>? = null

    init { refreshLocalIp() }

    fun resetTransferState() {
        _uiState.value = _uiState.value.copy(
            isDownloading = false, downloadProgress = 0f, transferStatus = getApplication<Application>().getString(R.string.open),
            error = null, pendingProgress = null, pendingBookTitle = null,
            showConflictDialog = false, targetIp = null
        )
    }

    fun refreshLocalIp() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = getLocalIpAddress()
            _uiState.value = _uiState.value.copy(localIp = ip)
        }
    }

    fun startServer(bookId: String) {
        stopServer()
        resetTransferState()
        serverJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bookProgress = progressRepository.getProgress(bookId)
                android.util.Log.d("LiteraTransfer", "startServer: bookId=$bookId, progress=$bookProgress")
                val bookDetails = bookRepository.getBookById(bookId) ?: return@launch
                val ip = getLocalIpAddress()
                if (ip == "0.0.0.0") { _uiState.value = _uiState.value.copy(error = "Sin Wi-Fi"); return@launch }

                val shortTitle = if (bookDetails.title.length > 25) bookDetails.title.take(22) + "..." else bookDetails.title
                val shortAuthor = if (bookDetails.artist.length > 20) bookDetails.artist.take(17) + "..." else bookDetails.artist

                val qrJson = JSONObject().apply {
                    put("ip", "$ip:${Constants.TRANSFER_SERVER_PORT}"); put("t", shortTitle); put("a", shortAuthor)
                    if (bookProgress != null) { 
                        android.util.Log.d("LiteraTransfer", "QR will include: position=${bookProgress.lastPosition}, duration=${bookProgress.duration}")
                        put("p", bookProgress.lastPosition); put("d", bookProgress.duration) 
                    }
                }
                
                val positionMs = bookProgress?.lastPosition ?: 0L
                val durationMs = bookProgress?.duration ?: 0L

                _uiState.value = _uiState.value.copy(
                    qrData = qrJson.toString(),
                    qrPositionMs = positionMs,
                    qrDurationMs = durationMs,
                    isServerRunning = true, 
                    transferStatus = getApplication<Application>().getString(R.string.qr_generated)
                )

                val pm = getApplication<Application>().getSystemService(Application.POWER_SERVICE) as PowerManager
                val wm = getApplication<Application>().getSystemService(Application.WIFI_SERVICE) as WifiManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Litera:TransferWake").apply { acquire(15*60*1000L) }
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Litera:WifiLock").apply { acquire() }

                serverSocket = ServerSocket(); serverSocket?.reuseAddress = true; serverSocket?.bind(InetSocketAddress(ip, Constants.TRANSFER_SERVER_PORT))

                while (isActive) {
                    val clientSocket = try { serverSocket?.accept() } catch (e: Exception) { null } ?: break
                    launch(Dispatchers.IO) { handleClient(clientSocket, bookDetails.path, bookDetails.title, bookDetails.artist, bookProgress) }
                }
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(error = "Error: ${e.message}") }
        }
    }

    private suspend fun handleClient(socket: Socket, path: String, title: String, author: String, progress: AudiobookProgress?) {
        withContext(Dispatchers.IO) {
            try {
                socket.tcpNoDelay = true
                val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
                val input = DataInputStream(BufferedInputStream(socket.inputStream))
                val context = getApplication<Application>()
                
                output.writeUTF(title); output.flush()
                
                val response = input.readUTF()
                if (response == "FILE") {
                    var fileSize = 0L; var fileName = "audiobook.mp3"
                    if (path.startsWith("content://")) {
                        context.contentResolver.query(Uri.parse(path), null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE))
                                fileName = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                            }
                        }
                    } else { val f = File(path); fileSize = f.length(); fileName = f.name }
                    
                    output.writeUTF(fileName); output.writeLong(fileSize); output.flush()
                    
                    withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(transferStatus = context.getString(R.string.sending, title)) }

                    val inputStream: InputStream = if (path.startsWith("content://")) { context.contentResolver.openInputStream(Uri.parse(path))!! } else { FileInputStream(File(path)) }
                    inputStream.use { i ->
                        val buffer = ByteArray(65536); var bytesRead: Int
                        while (i.read(buffer).also { bytesRead = it } != -1) { output.write(buffer, 0, bytesRead) }
                    }
                    output.flush()
                }
                socket.close()
                withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(transferStatus = context.getString(R.string.book_sent)) }
            } catch (e: Exception) { withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(error = "Fallo: ${e.message}") } }
            finally { try { socket.close() } catch (e: Exception) {} }
        }
    }

    fun processScannedData(qrString: String, libraryUri: String?) {
        if (_uiState.value.isDownloading || _uiState.value.showConflictDialog) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(qrString); val ip = json.getString("ip"); val title = json.getString("t"); val author = json.getString("a")
                val localBooks = bookRepository.getAllBooks().first()
                val existingBook = localBooks.find { it.title.startsWith(title.replace("...", ""), true) && it.artist.startsWith(author.replace("...", ""), true) }

                if (existingBook != null) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            showConflictDialog = true, pendingBookTitle = existingBook.title, targetIp = ip,
                            pendingProgress = if (json.has("p")) { AudiobookProgress(existingBook.id, json.getLong("p"), json.getLong("d"), System.currentTimeMillis()) } else null
                        )
                    }
                } else { receiveFromIp(ip, libraryUri) }
            } catch (e: Exception) { receiveFromIp(qrString, libraryUri) }
        }
    }

    fun handleUserDecision(decision: String, libraryUri: String?) {
        val targetIp = _uiState.value.targetIp; val progress = _uiState.value.pendingProgress
        android.util.Log.d("LiteraTransfer", "handleUserDecision: decision=$decision, progress=$progress")
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(showConflictDialog = false)
        viewModelScope.launch(Dispatchers.IO) {
            when (decision) {
                "PROGRESS" -> {
                    if (progress != null) {
                        android.util.Log.d("LiteraTransfer", "Saving progress: mediaId=${progress.mediaId}, position=${progress.lastPosition}, duration=${progress.duration}")
                        progressRepository.saveProgress(progress)
                        
                        // Verify it was saved
                        val savedProgress = progressRepository.getProgress(progress.mediaId)
                        android.util.Log.d("LiteraTransfer", "Verified saved progress: $savedProgress")
                    }
                    withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(transferStatus = context.getString(R.string.synced_via_qr)) }
                }
                "FILE" -> { if (targetIp != null) receiveFromIp(targetIp, libraryUri) }
                "CANCEL" -> { withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(transferStatus = context.getString(R.string.cancel)) } }
            }
        }
    }

    fun receiveFromIp(ipAndPort: String, targetRootUri: String?) {
        val context = getApplication<Application>()
        val pendingProgress = _uiState.value.pendingProgress
        viewModelScope.launch(Dispatchers.IO) {
            val cleanUrl = ipAndPort.replace("http://", "").replace("/", "").trim()
            _uiState.value = _uiState.value.copy(isDownloading = true, transferStatus = context.getString(R.string.connecting), error = null)
            var socket: Socket? = null
            try {
                val parts = cleanUrl.split(":"); socket = Socket(); socket.connect(InetSocketAddress(parts[0], parts[1].toInt()), Constants.SOCKET_CONNECT_TIMEOUT_MS); socket.soTimeout = Constants.SOCKET_READ_TIMEOUT_MS
                val input = DataInputStream(BufferedInputStream(socket.inputStream))
                val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
                
                val bookTitle = input.readUTF()
                withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(transferStatus = context.getString(R.string.receiving, bookTitle)) }
                
                output.writeUTF("FILE"); output.flush()
                
                val fileName = input.readUTF(); val fileSize = input.readLong()
                downloadToFolder(input, fileName, fileSize, targetRootUri, pendingProgress)
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(error = "Error: ${e.message}", isDownloading = false) }
            finally { try { socket?.close() } catch (e: Exception) {} }
        }
    }

    private suspend fun downloadToFolder(input: DataInputStream, fileName: String, fileSize: Long, targetRootUri: String?, pendingProgress: AudiobookProgress?) {
        val context = getApplication<Application>()
        var finalPath: String? = null
        var finalUri: String? = null
        val outputStream: OutputStream = if (targetRootUri != null) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(targetRootUri))
            var inboxDir = rootDoc?.findFile("Inbox"); if (inboxDir == null) inboxDir = rootDoc?.createDirectory("Inbox")
            val newFile = inboxDir?.createFile("audio/*", fileName)
            finalUri = newFile?.uri?.toString()
            context.contentResolver.openOutputStream(newFile!!.uri)!!
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Litera/Inbox")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, fileName); finalPath = f.absolutePath; FileOutputStream(f)
        }
        outputStream.use { out ->
            val buffer = ByteArray(65536); var total = 0L; var read: Int; var last = 0L
            while (total < fileSize) {
                read = input.read(buffer); if (read == -1) break
                out.write(buffer, 0, read); total += read
                val now = System.currentTimeMillis()
                if (now - last > 150) { _uiState.value = _uiState.value.copy(downloadProgress = total.toFloat() / fileSize); last = now }
            }
        }
        
        val savedUri = finalUri ?: "file://$finalPath"
        
        if (pendingProgress != null) {
            val newProgress = AudiobookProgress(
                mediaId = pendingProgress.mediaId,
                lastPosition = pendingProgress.lastPosition,
                duration = pendingProgress.duration,
                lastUpdated = System.currentTimeMillis(),
                playbackSpeed = 1.0f,
                lastPauseTimestamp = 0L
            )
            progressRepository.saveProgress(newProgress)
            android.util.Log.d("LiteraTransfer", "Saved progress with existing book ID: ${pendingProgress.mediaId}, position=${pendingProgress.lastPosition}")
        }
        
        if (finalPath != null && !finalPath.startsWith("content://")) MediaScannerConnection.scanFile(context, arrayOf(finalPath), null, null)
        withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(isDownloading = false, transferStatus = context.getString(R.string.book_received), downloadProgress = 1f, pendingProgress = null) }
    }

    private fun getLocalIpAddress(): String {
        try {
            for (intf in NetworkInterface.getNetworkInterfaces().toList()) {
                if (intf.name.lowercase().contains("wlan") || intf.name.lowercase().contains("ap")) {
                    for (addr in intf.inetAddresses) { if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress ?: "" }
                }
            }
        } catch (e: Exception) {}
        return "0.0.0.0"
    }

    fun stopServer() {
        serverJob?.cancel()
        try { serverSocket?.close() } catch (e: Exception) {}
        try { wakeLock?.release() } catch (e: Exception) {}
        try { wifiLock?.release() } catch (e: Exception) {}
        serverSocket = null
        _uiState.value = _uiState.value.copy(isServerRunning = false, qrData = null)
    }

    override fun onCleared() { stopServer(); super.onCleared() }
}
