package com.raulburgosmurray.musicplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.app.Application
import android.net.Uri
import androidx.compose.ui.res.stringResource
import com.raulburgosmurray.musicplayer.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    mainViewModel: MainViewModel,
    syncViewModel: SyncViewModel,
    onBack: () -> Unit
) {
    val isDynamicEnabled by viewModel.isDynamicThemingEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val libraryRootUris by viewModel.libraryRootUris.collectAsState()
    val scanAllMemory by viewModel.scanAllMemory.collectAsState()
    val historyLimit by viewModel.historyLimit.collectAsState()
    val isShakeEnabled by viewModel.isShakeEnabled.collectAsState()
    val isVibrationEnabled by viewModel.isVibrationEnabled.collectAsState()
    val isSoundEnabled by viewModel.isSoundEnabled.collectAsState()
    
    val userAccount by syncViewModel.userAccount.collectAsState()
    val isSyncing by syncViewModel.isSyncing.collectAsState()
    val lastSyncTime by syncViewModel.lastSyncTime.collectAsState()

    val context = LocalContext.current
    
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) syncViewModel.onLoginSuccess(account)
        } catch (e: Exception) {}
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.addLibraryRootUri(it.toString())
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_btn))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Cloud Sync Section
            if (com.raulburgosmurray.musicplayer.FeatureFlags.CLOUD_SYNC) {
                Text(stringResource(R.string.cloud_sync), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (userAccount == null) {
                            Text(stringResource(R.string.cloud_sync_desc), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
                                        .build()
                                    val client = GoogleSignIn.getClient(context, gso)
                                    googleSignInLauncher.launch(client.signInIntent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudUpload, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.connect_google_drive))
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(userAccount?.displayName ?: "Usuario", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(userAccount?.email ?: "", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                    GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                                    syncViewModel.onLogout()
                                }) {
                                    Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(stringResource(R.string.last_sync), style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        if (lastSyncTime > 0) SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(lastSyncTime)) else stringResource(R.string.never),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Button(
                                    onClick = { syncViewModel.syncNow() },
                                    enabled = !isSyncing
                                ) {
                                    if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    else Text(stringResource(R.string.sync_now))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // Appearance
            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                label = {
                                    Text(stringResource(when (mode) {
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                        ThemeMode.LIGHT  -> R.string.theme_light
                                        ThemeMode.DARK   -> R.string.theme_dark
                                    }))
                                }
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.dynamic_colors), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.dynamic_colors_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isDynamicEnabled, onCheckedChange = { viewModel.setDynamicThemingEnabled(it) })
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Library
            Text(stringResource(R.string.library), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
            
            // Scan All Memory Option
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.scan_all_memory), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.scan_all_memory_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = scanAllMemory, onCheckedChange = { 
                            viewModel.setScanAllMemory(it)
                            if (it) mainViewModel.loadBooks(emptyList(), true)
                        })
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Folders Section
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.folders_to_scan), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("${libraryRootUris.size}/3", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    if (libraryRootUris.isNotEmpty()) {
                        libraryRootUris.forEach { uri ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(text = Uri.parse(uri).path ?: uri, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = { 
                                    viewModel.removeLibraryRootUri(uri)
                                    if (!scanAllMemory) mainViewModel.loadBooks(libraryRootUris.filter { it != uri }, false)
                                }) { 
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = MaterialTheme.colorScheme.error) 
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    if (libraryRootUris.size < 3 && !scanAllMemory) {
                        OutlinedButton(
                            onClick = { folderLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_folder))
                        }
                    }
                    
                    if (libraryRootUris.isNotEmpty() && !scanAllMemory) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { mainViewModel.loadBooks(libraryRootUris, false) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.rescan_folders))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Sleep Timer Options
            Text(stringResource(R.string.sleep_timer_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.shake_to_extend), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.shake_to_extend_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isShakeEnabled, onCheckedChange = { viewModel.setShakeEnabled(it) })
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth().alpha(if (isShakeEnabled) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.vibration_warning), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.vibration_warning_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isVibrationEnabled && isShakeEnabled, onCheckedChange = { viewModel.setVibrationEnabled(it) }, enabled = isShakeEnabled)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth().alpha(if (isShakeEnabled) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sound_warning), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.sound_warning_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isSoundEnabled && isShakeEnabled, onCheckedChange = { viewModel.setSoundEnabled(it) }, enabled = isShakeEnabled)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            // Activity History
            Text(stringResource(R.string.activity_history), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.entry_limit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.entry_limit_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                    val limits = listOf(25, 50, 100, 200)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        limits.forEach { limit -> FilterChip(selected = historyLimit == limit, onClick = { viewModel.setHistoryLimit(limit) }, label = { Text(limit.toString()) }) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Text(stringResource(R.string.about_app), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
            Text("Version ${com.raulburgosmurray.musicplayer.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}
