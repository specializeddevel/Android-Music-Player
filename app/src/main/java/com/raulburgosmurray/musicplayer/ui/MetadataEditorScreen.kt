package com.raulburgosmurray.musicplayer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.raulburgosmurray.musicplayer.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.raulburgosmurray.musicplayer.Music

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    viewModel: MetadataEditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var trackNumber by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setNewArtUri(it) }
    }
    
    LaunchedEffect(uiState.metadata) {
        uiState.metadata?.let { metadata ->
            title = metadata.title
            artist = metadata.artist
            album = metadata.album ?: ""
            year = metadata.year ?: ""
            genre = metadata.genre ?: ""
            trackNumber = metadata.trackNumber?.toString() ?: ""
            comment = metadata.comment ?: ""
        }
    }
    
    LaunchedEffect(title) {
        viewModel.updateTitle(title)
    }
    
    LaunchedEffect(artist) {
        viewModel.updateArtist(artist)
    }
    
    LaunchedEffect(album) {
        viewModel.updateAlbum(album)
    }
    
    LaunchedEffect(year) {
        viewModel.updateYear(year)
    }
    
    LaunchedEffect(genre) {
        viewModel.updateGenre(genre)
    }
    
    LaunchedEffect(trackNumber) {
        viewModel.updateTrackNumber(trackNumber)
    }
    
    LaunchedEffect(comment) {
        viewModel.updateComment(comment)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_metadata_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_btn))
                    }
                },
                actions = {
                    if (!uiState.isSaving) {
                        IconButton(
                            onClick = { viewModel.saveMetadata() },
                            enabled = title.isNotBlank() && artist.isNotBlank()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text(stringResource(R.string.back_btn))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CoverArtSection(
                    currentArtUri = uiState.metadata?.artUri,
                    newArtUri = uiState.newArtUri,
                    bookTitle = title,
                    onPickImage = { imagePickerLauncher.launch("image/*") }
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        EditTextField(
                            label = stringResource(R.string.field_title),
                            value = title,
                            onValueChange = { title = it },
                            placeholder = stringResource(R.string.field_title_placeholder)
                        )

                        EditTextField(
                            label = stringResource(R.string.field_author_label),
                            value = artist,
                            onValueChange = { artist = it },
                            placeholder = stringResource(R.string.field_author_placeholder)
                        )

                        EditTextField(
                            label = stringResource(R.string.detail_series),
                            value = album,
                            onValueChange = { album = it },
                            placeholder = stringResource(R.string.field_series_placeholder)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EditTextField(
                                label = stringResource(R.string.detail_year),
                                value = year,
                                onValueChange = { year = it },
                                placeholder = "2024",
                                modifier = Modifier.weight(1f)
                            )

                            EditTextField(
                                label = stringResource(R.string.detail_volume),
                                value = trackNumber,
                                onValueChange = { trackNumber = it },
                                placeholder = "1",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        GenreDropdown(
                            selectedGenre = genre,
                            onGenreSelected = { genre = it },
                            genres = viewModel.getPredefinedGenres()
                        )
                        
                        EditTextField(
                            label = stringResource(R.string.field_comment),
                            value = comment,
                            onValueChange = { comment = it },
                            placeholder = stringResource(R.string.field_comment_placeholder),
                            singleLine = false,
                            minLines = 3
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    if (uiState.saveSuccess) {
        SuccessDialog(
            onDismiss = {
                viewModel.clearSuccess()
                onBack()
            }
        )
    }
}

@Composable
fun CoverArtSection(
    currentArtUri: String?,
    newArtUri: Uri?,
    bookTitle: String,
    onPickImage: () -> Unit
) {
    val displayUri = newArtUri?.toString() ?: currentArtUri
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onPickImage),
        contentAlignment = Alignment.Center
    ) {
        if (displayUri != null) {
            AsyncImage(
                model = displayUri,
                contentDescription = stringResource(R.string.book_cover),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        } else {
            BookPlaceholder(title = bookTitle, modifier = Modifier.fillMaxSize())
        }
        
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.change_cover),
                    tint = Color.White
                )
                Text(
                    text = if (displayUri != null) stringResource(R.string.change_cover) else stringResource(R.string.add_cover),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun EditTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 5
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDropdown(
    selectedGenre: String,
    onGenreSelected: (String) -> Unit,
    genres: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedGenre,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.detail_genre)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.no_genre)) },
                onClick = {
                    onGenreSelected("")
                    expanded = false
                }
            )
            
            genres.forEach { genre ->
                DropdownMenuItem(
                    text = { Text(genre) },
                    onClick = {
                        onGenreSelected(genre)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SuccessDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                
                Text(
                    text = stringResource(R.string.metadata_saved),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = stringResource(R.string.changes_applied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.accept))
                }
            }
        }
    }
}