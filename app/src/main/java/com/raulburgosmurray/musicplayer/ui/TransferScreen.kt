package com.raulburgosmurray.musicplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.raulburgosmurray.musicplayer.R
import com.raulburgosmurray.musicplayer.ui.theme.MusicPlayerTheme
import kotlin.math.roundToInt

internal fun transferProgressPercent(progress: Float): Int =
    (progress.coerceIn(0f, 1f) * 100).roundToInt()

internal fun formatTransferPlaybackProgress(positionMs: Long, durationMs: Long): String =
    "${formatTransferTime(positionMs)} / ${formatTransferTime(durationMs)}"

private fun formatTransferTime(timeMs: Long): String {
    val totalSeconds = (timeMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferConflictDialog(
    bookTitle: String,
    onSyncProgress: () -> Unit,
    onDownloadBook: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp).size(28.dp)
                    )
                }
                Text(
                    stringResource(R.string.book_detected),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.existing_book_msg, bookTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TransferChoice(
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                    title = stringResource(R.string.sync_progress_only),
                    description = stringResource(R.string.sync_progress_only_desc),
                    onClick = onSyncProgress
                )
                TransferChoice(
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    title = stringResource(R.string.download_full_book),
                    description = stringResource(R.string.download_full_book_desc),
                    onClick = onDownloadBook
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun TransferChoice(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    state: TransferUIState,
    isSending: Boolean,
    isInitialized: Boolean,
    isManualEntry: Boolean,
    targetAddress: String,
    hasCameraPermission: Boolean,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onManualEntryChange: (Boolean) -> Unit,
    onTargetAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    cameraContent: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(if (isSending) R.string.send else R.string.receive), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_btn)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TransferHeader(isSending)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when {
                        !isInitialized -> CircularProgressIndicator()
                        state.isDownloading -> DownloadProgress(state)
                        isSending -> SenderContent(state, onRetry)
                        isManualEntry -> ManualReceiverContent(
                            targetAddress = targetAddress,
                            onTargetAddressChange = onTargetAddressChange,
                            onConnect = onConnect,
                            onUseCamera = { onManualEntryChange(false) }
                        )
                        hasCameraPermission -> {
                            CameraFrame(cameraContent)
                            FilledTonalButton(
                                onClick = { onManualEntryChange(true) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Keyboard, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.enter_address_manually))
                            }
                        }
                        else -> CameraPermissionContent(
                            onRequestPermission = onRequestCameraPermission,
                            onUseManual = { onManualEntryChange(true) }
                        )
                    }
                }
            }

            TransferStatusCard(state)

            OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.finish_and_exit), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TransferHeader(isSending: Boolean) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Icon(
            if (isSending) Icons.Default.QrCode2 else Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(16.dp).size(34.dp)
        )
    }
    Text(
        stringResource(if (isSending) R.string.transfer_send_title else R.string.transfer_receive_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Text(
        stringResource(if (isSending) R.string.transfer_send_instructions else R.string.transfer_receive_instructions),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun DownloadProgress(state: TransferUIState) {
    val percent = transferProgressPercent(state.downloadProgress)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp).size(32.dp)
        )
    }
    Text(
        state.transferStatus ?: stringResource(R.string.receiving_transfer),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    LinearProgressIndicator(
        progress = { state.downloadProgress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp))
    )
    Text("$percent%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SenderContent(state: TransferUIState, onRetry: () -> Unit) {
    if (state.error != null) {
        ErrorContent(state.error, onRetry)
        return
    }
    if (!state.isServerRunning) {
        CircularProgressIndicator()
        Text(stringResource(R.string.preparing_transfer), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    state.qrData?.let { data ->
        val bitmap = remember(data) { createTransferQrBitmap(data) }
        bitmap?.let {
            Surface(color = Color.White, shape = MaterialTheme.shapes.large, shadowElevation = 2.dp) {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.transfer_qr_code),
                    modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f).padding(12.dp)
                )
            }
        }
    }
    if (state.qrPositionMs > 0L && state.qrDurationMs > 0L) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                stringResource(
                    R.string.transfer_playback_progress,
                    formatTransferPlaybackProgress(state.qrPositionMs, state.qrDurationMs)
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
    Text(stringResource(R.string.qr_generated), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
    Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 52.dp), shape = MaterialTheme.shapes.large) { Text(stringResource(R.string.retry)) }
}

@Composable
private fun ManualReceiverContent(
    targetAddress: String,
    onTargetAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    onUseCamera: () -> Unit
) {
    Icon(Icons.Default.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
    Text(stringResource(R.string.manual_connection), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = targetAddress,
        onValueChange = onTargetAddressChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.ip_port_label)) },
        supportingText = { Text(stringResource(R.string.ip_port_example)) },
        singleLine = true,
        shape = MaterialTheme.shapes.large
    )
    Button(onClick = onConnect, enabled = targetAddress.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.large) {
        Text(stringResource(R.string.connect))
    }
    TextButton(onClick = onUseCamera) {
        Icon(Icons.Default.CameraAlt, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.use_camera))
    }
}

@Composable
private fun CameraPermissionContent(onRequestPermission: () -> Unit, onUseManual: () -> Unit) {
    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
    Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.transfer_camera_explanation),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.large) {
        Text(stringResource(R.string.grant_camera_permission))
    }
    TextButton(onClick = onUseManual) {
        Icon(Icons.Default.Keyboard, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.enter_address_manually))
    }
}

@Composable
private fun CameraFrame(cameraContent: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val frameSize = maxWidth.coerceAtMost(320.dp)
        Box(
            modifier = Modifier.size(frameSize).clip(MaterialTheme.shapes.extraLarge)
                .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            cameraContent()
        }
    }
    Text(
        stringResource(R.string.align_qr_in_frame),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun TransferStatusCard(state: TransferUIState) {
    val hasError = state.error != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (hasError) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    if (hasError) state.error.orEmpty() else state.transferStatus ?: stringResource(R.string.ready),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(R.string.your_ip, state.localIp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun createTransferQrBitmap(data: String): Bitmap? = try {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 2
    )
    val matrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 512, 512, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val offset = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[offset + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
} catch (_: Exception) {
    null
}

@Preview(name = "Receive audiobook", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun TransferReceiverPreview() {
    MusicPlayerTheme(dynamicColor = false) {
        TransferScreen(
            state = TransferUIState(localIp = "192.168.1.12", transferStatus = "Listo"),
            isSending = false,
            isInitialized = true,
            isManualEntry = false,
            targetAddress = "",
            hasCameraPermission = true,
            onBack = {},
            onFinish = {},
            onRetry = {},
            onManualEntryChange = {},
            onTargetAddressChange = {},
            onConnect = {},
            onRequestCameraPermission = {},
            cameraContent = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        )
    }
}

@Preview(name = "Transfer progress", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun TransferProgressPreview() {
    MusicPlayerTheme(dynamicColor = false) {
        TransferScreen(
            state = TransferUIState(
                isDownloading = true,
                downloadProgress = 0.46f,
                transferStatus = "Recibiendo: El nombre del viento",
                localIp = "192.168.1.12"
            ),
            isSending = false,
            isInitialized = true,
            isManualEntry = false,
            targetAddress = "",
            hasCameraPermission = true,
            onBack = {},
            onFinish = {},
            onRetry = {},
            onManualEntryChange = {},
            onTargetAddressChange = {},
            onConnect = {},
            onRequestCameraPermission = {},
            cameraContent = {}
        )
    }
}
