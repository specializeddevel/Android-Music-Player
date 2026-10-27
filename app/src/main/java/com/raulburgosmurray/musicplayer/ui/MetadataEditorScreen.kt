package com.raulburgosmurray.musicplayer.ui

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.raulburgosmurray.musicplayer.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

internal fun isMetadataSaveEnabled(title: String, artist: String, isSaving: Boolean): Boolean =
    title.isNotBlank() && artist.isNotBlank() && !isSaving

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    viewModel: MetadataEditorViewModel,
    onBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
    
    val canSave = isMetadataSaveEnabled(title, artist, uiState.isSaving)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_metadata_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_btn)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (!uiState.isLoading && uiState.error == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp
                ) {
                    Button(
                        onClick = { viewModel.saveMetadata() },
                        enabled = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .heightIn(min = 52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> MetadataEditorLoading(Modifier.padding(padding))
            uiState.error != null -> MetadataEditorError(
                message = uiState.error!!,
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
            else -> MetadataEditorContent(
                isLandscape = isLandscape,
                currentArtUri = uiState.metadata?.artUri,
                newArtUri = uiState.newArtUri,
                title = title,
                onTitleChange = { title = it },
                artist = artist,
                onArtistChange = { artist = it },
                album = album,
                onAlbumChange = { album = it },
                year = year,
                onYearChange = { year = it },
                genre = genre,
                onGenreChange = { genre = it },
                trackNumber = trackNumber,
                onTrackNumberChange = { trackNumber = it },
                comment = comment,
                onCommentChange = { comment = it },
                genres = viewModel.getPredefinedGenres(),
                onPickImage = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.padding(padding)
            )
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
private fun MetadataEditorLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MetadataEditorError(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 440.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.back_btn))
                }
            }
        }
    }
}

@Composable
private fun MetadataEditorContent(
    isLandscape: Boolean,
    currentArtUri: String?,
    newArtUri: Uri?,
    title: String,
    onTitleChange: (String) -> Unit,
    artist: String,
    onArtistChange: (String) -> Unit,
    album: String,
    onAlbumChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    genre: String,
    onGenreChange: (String) -> Unit,
    trackNumber: String,
    onTrackNumberChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    genres: List<String>,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                val coverSize = minOf(maxWidth, maxHeight, 320.dp)
                CoverArtSection(
                    currentArtUri = currentArtUri,
                    newArtUri = newArtUri,
                    bookTitle = title,
                    onPickImage = onPickImage,
                    modifier = Modifier.size(coverSize)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                MetadataFieldsCard(
                    title = title,
                    onTitleChange = onTitleChange,
                    artist = artist,
                    onArtistChange = onArtistChange,
                    album = album,
                    onAlbumChange = onAlbumChange,
                    year = year,
                    onYearChange = onYearChange,
                    genre = genre,
                    onGenreChange = onGenreChange,
                    trackNumber = trackNumber,
                    onTrackNumberChange = onTrackNumberChange,
                    comment = comment,
                    onCommentChange = onCommentChange,
                    genres = genres
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CoverArtSection(
                currentArtUri = currentArtUri,
                newArtUri = newArtUri,
                bookTitle = title,
                onPickImage = onPickImage,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            MetadataFieldsCard(
                title = title,
                onTitleChange = onTitleChange,
                artist = artist,
                onArtistChange = onArtistChange,
                album = album,
                onAlbumChange = onAlbumChange,
                year = year,
                onYearChange = onYearChange,
                genre = genre,
                onGenreChange = onGenreChange,
                trackNumber = trackNumber,
                onTrackNumberChange = onTrackNumberChange,
                comment = comment,
                onCommentChange = onCommentChange,
                genres = genres,
                modifier = Modifier.widthIn(max = 720.dp)
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MetadataFieldsCard(
    title: String,
    onTitleChange: (String) -> Unit,
    artist: String,
    onArtistChange: (String) -> Unit,
    album: String,
    onAlbumChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    genre: String,
    onGenreChange: (String) -> Unit,
    trackNumber: String,
    onTrackNumberChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    genres: List<String>,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.metadata_information),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.metadata_required_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            EditTextField(
                label = stringResource(R.string.field_title),
                value = title,
                onValueChange = onTitleChange,
                placeholder = stringResource(R.string.field_title_placeholder)
            )
            EditTextField(
                label = stringResource(R.string.field_author_label),
                value = artist,
                onValueChange = onArtistChange,
                placeholder = stringResource(R.string.field_author_placeholder)
            )
            EditTextField(
                label = stringResource(R.string.detail_series),
                value = album,
                onValueChange = onAlbumChange,
                placeholder = stringResource(R.string.field_series_placeholder)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditTextField(
                    label = stringResource(R.string.detail_year),
                    value = year,
                    onValueChange = onYearChange,
                    placeholder = "2024",
                    modifier = Modifier.weight(1f)
                )
                EditTextField(
                    label = stringResource(R.string.detail_volume),
                    value = trackNumber,
                    onValueChange = onTrackNumberChange,
                    placeholder = "1",
                    modifier = Modifier.weight(1f)
                )
            }
            GenreDropdown(
                selectedGenre = genre,
                onGenreSelected = onGenreChange,
                genres = genres
            )
            EditTextField(
                label = stringResource(R.string.field_comment),
                value = comment,
                onValueChange = onCommentChange,
                placeholder = stringResource(R.string.field_comment_placeholder),
                singleLine = false,
                minLines = 3
            )
        }
    }
}

@Composable
fun CoverArtSection(
    currentArtUri: String?,
    newArtUri: Uri?,
    bookTitle: String,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
) {
    val displayUri = newArtUri?.toString() ?: currentArtUri

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
            } else {
                BookPlaceholder(title = bookTitle, modifier = Modifier.fillMaxSize())
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (displayUri != null) {
                            stringResource(R.string.change_cover)
                        } else {
                            stringResource(R.string.add_cover)
                        },
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
        maxLines = if (singleLine) 1 else 5,
        shape = MaterialTheme.shapes.large
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
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
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
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp).size(32.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.metadata_saved),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.changes_applied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(stringResource(R.string.accept))
                }
            }
        }
    }
}
