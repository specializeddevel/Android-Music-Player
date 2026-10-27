package com.raulburgosmurray.musicplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.raulburgosmurray.musicplayer.FeatureFlags
import com.raulburgosmurray.musicplayer.R
import com.raulburgosmurray.musicplayer.ui.theme.MusicPlayerTheme

@Composable
fun OnboardingScreen(
    onSelectFolder: () -> Unit,
    onScanAllMemory: () -> Unit,
    onTransferByQr: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(colors.surfaceContainerLow, colors.surface))
        ).safeDrawingPadding()
    ) {
        val wide = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (wide) {
                Row(
                    Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WelcomeHeader(Modifier.weight(1f))
                    LibraryChoices(onSelectFolder, onScanAllMemory, onTransferByQr, Modifier.weight(1.2f))
                }
            } else {
                Column(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    WelcomeHeader()
                    LibraryChoices(onSelectFolder, onScanAllMemory, onTransferByQr)
                }
            }
        }
    }
}

@Composable
private fun WelcomeHeader(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BookIllustration()
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.welcome_desc), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookIllustration() {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(160.dp, 120.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.size(76.dp, 96.dp).rotate(-14f).align(Alignment.CenterStart),
            shape = RoundedCornerShape(12.dp), color = colors.tertiaryContainer) {}
        Surface(Modifier.size(76.dp, 96.dp).rotate(12f).align(Alignment.CenterEnd),
            shape = RoundedCornerShape(12.dp), color = colors.secondaryContainer) {}
        Surface(Modifier.size(80.dp, 108.dp), shape = RoundedCornerShape(12.dp),
            color = colors.primaryContainer, shadowElevation = 4.dp) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly) {
                Icon(Icons.Outlined.Headphones, null, Modifier.size(32.dp), tint = colors.onPrimaryContainer)
                HorizontalDivider(Modifier.width(36.dp), color = colors.onPrimaryContainer.copy(alpha = 0.35f))
                HorizontalDivider(Modifier.width(24.dp), color = colors.onPrimaryContainer.copy(alpha = 0.2f))
            }
        }
    }
}

@Composable
private fun LibraryChoices(
    onSelectFolder: () -> Unit,
    onScanAllMemory: () -> Unit,
    onTransferByQr: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LibraryChoice(stringResource(R.string.select_folder),
            stringResource(R.string.onboarding_folder_description), Icons.Outlined.FolderOpen,
            featured = true, onClick = onSelectFolder)
        if (FeatureFlags.SCAN_ALL_MEMORY) {
            LibraryChoice(stringResource(R.string.scan_all_memory),
                stringResource(R.string.scan_all_memory_desc), Icons.Outlined.Storage, onClick = onScanAllMemory)
        }
        if (onTransferByQr != null) {
            LibraryChoice(stringResource(R.string.transfer_by_qr),
                stringResource(R.string.transfer_by_qr_desc), Icons.Outlined.QrCodeScanner, onClick = onTransferByQr)
        }
        Text(stringResource(R.string.onboarding_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun LibraryChoice(
    title: String,
    description: String,
    icon: ImageVector,
    featured: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val foreground = if (featured) colors.onPrimaryContainer else colors.onSurface
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (featured) colors.primaryContainer else colors.surfaceContainerLow),
        border = BorderStroke(1.dp, if (featured) colors.primary.copy(alpha = 0.3f) else colors.outlineVariant)
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp), tint = if (featured) foreground else colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = foreground)
                Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = if (featured) foreground else colors.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = foreground)
        }
    }
}

@Preview(showBackground = true, locale = "es", widthDp = 360, heightDp = 800)
@Preview(showBackground = true, locale = "es", widthDp = 800, heightDp = 400)
@Preview(showBackground = true, locale = "es", fontScale = 1.8f, widthDp = 360, heightDp = 800)
@Composable
private fun OnboardingPreview() {
    MusicPlayerTheme(dynamicColor = false) { OnboardingScreen({}, {}, {}) }
}
