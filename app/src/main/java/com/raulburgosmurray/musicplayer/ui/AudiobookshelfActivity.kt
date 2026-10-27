package com.raulburgosmurray.musicplayer.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raulburgosmurray.musicplayer.AboutActivity.Companion.EXTRA_SEED_COLOR
import com.raulburgosmurray.musicplayer.R
import com.raulburgosmurray.musicplayer.applyScreenOrientationPolicy
import com.raulburgosmurray.musicplayer.ui.theme.MusicPlayerTheme
import java.text.SimpleDateFormat
import java.util.*

class AudiobookshelfActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenOrientationPolicy()
        enableEdgeToEdge()

        val preferences = getSharedPreferences("settings", MODE_PRIVATE)

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            val dynamicTheming = preferences.getBoolean("dynamic_theming", true)
            val seedColor = if (intent.hasExtra(EXTRA_SEED_COLOR)) {
                Color(intent.getIntExtra(EXTRA_SEED_COLOR, 0))
            } else null

            MusicPlayerTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicTheming,
                seedColor = seedColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AudiobookshelfScreen(
                        settingsViewModel = settingsViewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val isConnected by settingsViewModel.isAbsConnected.collectAsState()
    val serverUrlState by settingsViewModel.absServerUrl.collectAsState()
    val usernameState by settingsViewModel.absUsername.collectAsState()
    val libraryName by settingsViewModel.absLibraryName.collectAsState()
    val lastSyncTime by settingsViewModel.absLastSyncTime.collectAsState()
    val isSyncing by settingsViewModel.isAbsSyncing.collectAsState()
    val syncError by settingsViewModel.absSyncError.collectAsState()

    var inputUrl by remember(serverUrlState) { mutableStateOf(serverUrlState) }
    var inputUsername by remember(usernameState) { mutableStateOf(usernameState) }
    var inputPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            icon = { Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Desconectar servidor Audiobookshelf?") },
            text = { Text("Los audiolibros remotos no descargados se eliminarán del catálogo. Los audiolibros que hayas descargado en tu teléfono se conservarán.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        settingsViewModel.disconnectAudiobookshelf()
                        Toast.makeText(context, "Servidor desconectado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Desconectar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Servidor Audiobookshelf", fontWeight = FontWeight.Bold) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isConnected) "Servidor Conectado" else "Conectar Audiobookshelf",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isConnected) "Biblioteca: ${libraryName.ifBlank { "Principal" }}" else "Sincroniza y transmite tus audiolibros desde tu servidor autoalojado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                if (isConnected) {
                    // Connected Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Servidor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(serverUrlState, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Usuario", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(usernameState, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (lastSyncTime > 0) {
                                HorizontalDivider(thickness = 0.5.dp)
                                val sdf = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("Última sincronización", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(sdf.format(Date(lastSyncTime)), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    if (isSyncing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }

                    if (!syncError.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = syncError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { settingsViewModel.resyncAudiobookshelf() },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isSyncing) "Sincronizando..." else "Sincronizar")
                        }

                        OutlinedButton(
                            onClick = { showDisconnectDialog = true },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Desconectar")
                        }
                    }
                } else {
                    // Connection Form Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = inputUrl,
                                onValueChange = { inputUrl = it },
                                label = { Text("URL del Servidor") },
                                placeholder = { Text("https://audio.example.com") },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            )

                            OutlinedTextField(
                                value = inputUsername,
                                onValueChange = { inputUsername = it },
                                label = { Text("Usuario") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            )

                            OutlinedTextField(
                                value = inputPassword,
                                onValueChange = { inputPassword = it },
                                label = { Text("Contraseña") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                                        )
                                    }
                                },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    if (inputUrl.isNotBlank() && inputUsername.isNotBlank() && inputPassword.isNotBlank()) {
                                        settingsViewModel.connectAudiobookshelf(inputUrl, inputUsername, inputPassword)
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large
                            )

                            if (isSyncing) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                            }

                            if (!syncError.isNullOrBlank()) {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = syncError!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    settingsViewModel.connectAudiobookshelf(inputUrl, inputUsername, inputPassword)
                                },
                                enabled = !isSyncing && inputUrl.isNotBlank() && inputUsername.isNotBlank() && inputPassword.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = MaterialTheme.shapes.large,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isSyncing) "Conectando..." else "Conectar y Sincronizar")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
