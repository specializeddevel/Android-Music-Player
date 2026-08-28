package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.os.VibratorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.AudioAttributes
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.raulburgosmurray.musicplayer.PlaybackService
import com.raulburgosmurray.musicplayer.R
import com.raulburgosmurray.musicplayer.EqPreset
import com.raulburgosmurray.musicplayer.EqualizerManager
import com.raulburgosmurray.musicplayer.data.AudiobookProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.os.CountDownTimer
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.BookRepository
import com.raulburgosmurray.musicplayer.data.FavoriteRepository
import com.raulburgosmurray.musicplayer.data.BookmarkRepository
import com.raulburgosmurray.musicplayer.data.QueueRepository
import com.raulburgosmurray.musicplayer.data.ProgressRepository
import com.raulburgosmurray.musicplayer.data.Bookmark
import com.raulburgosmurray.musicplayer.data.AudioMetadata
import com.raulburgosmurray.musicplayer.data.MetadataJsonHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first

import com.raulburgosmurray.musicplayer.Chapter
import com.raulburgosmurray.musicplayer.HistoryAction
import com.raulburgosmurray.musicplayer.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import com.raulburgosmurray.musicplayer.Constants
import com.raulburgosmurray.musicplayer.data.DescriptionExtractor
import com.raulburgosmurray.musicplayer.ui.formatDuration
import com.raulburgosmurray.musicplayer.sleep.SmartBookmarkManager
import java.util.Calendar

data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val currentMediaItem: MediaItem? = null,
    val playlist: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val playbackSpeed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val isReady: Boolean = false,
    val isConnected: Boolean = false,
    val sleepTimerMinutes: Int = 0,
    val isFavorite: Boolean = false,
    val chapters: List<Chapter> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val lastPositionBeforeSeek: Long? = null,
    val history: List<HistoryAction> = emptyList(),
    val dominantColor: Int? = null,
    val isShakeWaiting: Boolean = false,
    val currentMusicDetails: com.raulburgosmurray.musicplayer.Music? = null,
    val currentMetadata: AudioMetadata? = null,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val eqAvailable: Boolean = false,
    val timerStartPosition: Long? = null,
    val canReturnToTimerStart: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModel(application: Application) : androidx.lifecycle.AndroidViewModel(application) {

    companion object {
        internal const val MAX_REASONABLE_DURATION_MS = 315_360_000_000L // 10 years

        fun sanitizeDuration(duration: Long): Long {
            if (duration < 0) return 0L
            if (duration > MAX_REASONABLE_DURATION_MS) return 0L
            return duration
        }

        fun sanitizePosition(position: Long, duration: Long): Long {
            if (position < 0) return 0L
            if (duration in 1..MAX_REASONABLE_DURATION_MS && position > duration) return duration
            if (position > MAX_REASONABLE_DURATION_MS) return 0L
            return position
        }
    }

    var historyLimit: Int = 100 // Valor por defecto

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var pitchRecoveryJob: Job? = null
    private var descriptionJob: Job? = null
    private var sleepTimer: CountDownTimer? = null
    private var originalTimerMinutes: Int = 0
    private var sensorManager: SensorManager? = null
    private var shakeDetector: com.raulburgosmurray.musicplayer.ShakeDetector? = null
    private val equalizerManager = EqualizerManager()
    private val smartBookmarkManager = SmartBookmarkManager()
    
    private val bookRepository = BookRepository(AppDatabase.getDatabase(application).cachedBookDao())
    private val favoriteRepository = FavoriteRepository(AppDatabase.getDatabase(application).favoriteDao())
    private val bookmarkRepository = BookmarkRepository(AppDatabase.getDatabase(application).bookmarkDao())
    private val queueRepository = QueueRepository(AppDatabase.getDatabase(application).queueDao())
    private val progressRepository = ProgressRepository(AppDatabase.getDatabase(application).progressDao())

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var isQueueLoaded = false
    private var pendingBooksToLoadQueue: List<com.raulburgosmurray.musicplayer.Music>? = null

    // Shake Preferences
    private var isShakeSettingEnabled = true
    private var isVibrationEnabled = true
    private var isSoundEnabled = false

    // Time Announcement
    private var isTimeAnnouncementEnabled = false
    private var timeAnnouncementIntervalMinutes = 30
    private var timeAnnouncementJob: Job? = null
    private var tts: TextToSpeech? = null

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val timerPrefs = application.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)

    init {
        historyLimit = prefs.getInt("history_limit", 100)
        val savedHistory = prefs.getString("history_json", null)
        val initialHistory = if (savedHistory != null) {
            try {
                Json.decodeFromString<List<HistoryAction>>(savedHistory)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        _uiState.value = _uiState.value.copy(history = initialHistory.take(historyLimit))
        observeFavoriteStatus()
        observeBookmarks()

        isTimeAnnouncementEnabled = prefs.getBoolean("time_announcement_enabled", false)
        timeAnnouncementIntervalMinutes = prefs.getInt("time_announcement_interval", 30)
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    private fun checkTimerStartForMediaId(mediaId: String?) {
        if (mediaId == null) {
            _uiState.value = _uiState.value.copy(canReturnToTimerStart = false, timerStartPosition = null)
            return
        }
        val savedMediaId = timerPrefs.getString("last_timer_media_id", null)
        val savedPosition = timerPrefs.getLong("last_timer_position", -1L)
        if (savedMediaId == mediaId && savedPosition >= 0) {
            _uiState.value = _uiState.value.copy(canReturnToTimerStart = true, timerStartPosition = savedPosition)
        } else {
            _uiState.value = _uiState.value.copy(canReturnToTimerStart = false, timerStartPosition = null)
        }
    }

    fun updateShakePreferences(enabled: Boolean, vibration: Boolean, sound: Boolean) {
        isShakeSettingEnabled = enabled
        isVibrationEnabled = vibration
        isSoundEnabled = sound
    }

    fun updateTimeAnnouncementPreferences(enabled: Boolean, intervalMinutes: Int) {
        isTimeAnnouncementEnabled = enabled
        timeAnnouncementIntervalMinutes = intervalMinutes
        if (enabled && intervalMinutes > 0) {
            startTimeAnnouncementLoop()
        } else {
            stopTimeAnnouncementLoop()
        }
    }

    private fun startTimeAnnouncementLoop() {
        stopTimeAnnouncementLoop()
        if (!isTimeAnnouncementEnabled || timeAnnouncementIntervalMinutes <= 0) return
        timeAnnouncementJob = viewModelScope.launch {
            while (isActive) {
                delay(timeAnnouncementIntervalMinutes * 60 * 1000L)
                if (controller?.isPlaying == true) {
                    announceTime()
                }
            }
        }
    }

    private fun stopTimeAnnouncementLoop() {
        timeAnnouncementJob?.cancel()
        timeAnnouncementJob = null
    }

    private fun announceTime() {
        val ttsInstance = tts ?: return
        if (ttsInstance.isSpeaking) return
        val now = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val message = getApplication<Application>().getString(R.string.time_announcement, now)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ttsInstance.speak(message, TextToSpeech.QUEUE_FLUSH, null, "time_announcement")
        } else {
            @Suppress("DEPRECATION")
            val params = java.util.HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "time_announcement"
            ttsInstance.speak(message, TextToSpeech.QUEUE_FLUSH, params)
        }
    }

    private fun playWarningSound() {
        if (!isSoundEnabled) return
        try {
            // Usar STREAM_ALARM para asegurar que se oiga
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 300)
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Error al reproducir sonido de aviso", e)
        }
    }

    private fun vibrate() {
        if (!isVibrationEnabled) return
        val context = getApplication<Application>().applicationContext
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Usar atributos de alarma para mayor prioridad
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE), attributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    private fun startShakeDetection() {
        if (shakeDetector != null) return
        val context = getApplication<Application>().applicationContext
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        shakeDetector = com.raulburgosmurray.musicplayer.ShakeDetector {
            Log.d("PlaybackVM", "Agitado confirmado, extendiendo...")
            extendSleepTimer()
        }
        
        // Usar DELAY_GAME para mayor frecuencia de muestreo
        sensorManager?.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopShakeDetection() {
        sensorManager?.unregisterListener(shakeDetector)
        shakeDetector = null
    }

    private fun extendSleepTimer() {
        stopShakeDetection()
        vibrate()
        playWarningSound()
        viewModelScope.launch(Dispatchers.Main) {
            startSleepTimer(originalTimerMinutes)
        }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            _uiState.flatMapLatest { state ->
                val mediaId = state.currentMediaItem?.mediaId
                if (mediaId != null) {
                    bookmarkRepository.getBookmarksForMedia(mediaId)
                } else {
                    flowOf(emptyList())
                }
            }.collectLatest { bookmarkList ->
                _uiState.value = _uiState.value.copy(bookmarks = bookmarkList)
            }
        }
    }

    private fun observeFavoriteStatus() {
        viewModelScope.launch {
            _uiState.flatMapLatest { state ->
                val mediaId = state.currentMediaItem?.mediaId
                if (mediaId != null) {
                    favoriteRepository.isFavorite(mediaId)
                } else {
                    flowOf(false)
                }
            }.collectLatest { isFav ->
                _uiState.value = _uiState.value.copy(isFavorite = isFav)
            }
        }
    }

    private fun logAction(label: String) {
        val rawPos = controller?.currentPosition ?: 0L
        val duration = controller?.duration ?: 0L
        val currentPos = sanitizePosition(rawPos, duration)
        val newAction = HistoryAction(label, currentPos)
        val newList = _uiState.value.history.toMutableList()
        newList.add(0, newAction)
        val limited = newList.take(historyLimit)
        _uiState.value = _uiState.value.copy(history = limited)
        try {
            prefs.edit().putString("history_json", Json.encodeToString(limited)).apply()
        } catch (_: Exception) {
            // ignore serialization errors
        }
    }

    fun toggleFavorite() {
        val currentItem = _uiState.value.currentMediaItem ?: return
        val isCurrentlyFav = _uiState.value.isFavorite
        
        viewModelScope.launch {
            if (isCurrentlyFav) {
                favoriteRepository.removeFavorite(currentItem.mediaId)
            } else {
                favoriteRepository.addFavorite(currentItem.mediaId)
            }
        }
    }

    fun addBookmark(note: String, position: Long) {
        val currentItem = _uiState.value.currentMediaItem ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepository.addBookmark(
                mediaId = currentItem.mediaId,
                position = position,
                note = note
            )
            withContext(Dispatchers.Main) {
                logAction(getApplication<Application>().getString(R.string.history_bookmark_added))
            }
        }
    }

    fun deleteBookmark(bookmarkId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepository.deleteBookmark(bookmarkId)
        }
    }

    fun updateBookmarkNote(bookmarkId: Int, newNote: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepository.updateBookmarkNote(bookmarkId, newNote)
        }
    }

    fun initController(context: Context) {
        if (controller != null) return
        if (controllerFuture != null && !controllerFuture!!.isDone) return

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            try {
                val newController = controllerFuture?.get()
                if (newController != null) {
                    controller = newController
                    setupController()

                    // Restaurar cola persistente si había una carga pendiente
                    pendingBooksToLoadQueue?.let {
                        loadPersistedQueue(it)
                        pendingBooksToLoadQueue = null
                    }

                    pendingPlaylist?.let { (items, index) ->
                        playPlaylist(items, index)
                        pendingPlaylist = null
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Fallo al obtener controlador", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun updateDominantColor(artworkUri: android.net.Uri?) {
        if (artworkUri == null) {
            _uiState.value = _uiState.value.copy(dominantColor = null)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = getApplication<Application>().contentResolver
                    .openInputStream(artworkUri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    Palette.from(bitmap).generate { palette ->
                        val color = palette?.getVibrantColor(0) ?: palette?.getDominantColor(0)
                        if (color != 0 && color != null) {
                            _uiState.value = _uiState.value.copy(dominantColor = color)
                        }
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(dominantColor = null)
            }
        }
    }

    private var lastScannedUri: String? = null

    private fun extractChapters(uriString: String) {
        val uri = android.net.Uri.parse(uriString)
        viewModelScope.launch(Dispatchers.IO) {
            val chaptersList = mutableListOf<Chapter>()
            val retriever = android.media.MediaMetadataRetriever()
            val context = getApplication<Application>().applicationContext
            try {
                if (uriString.startsWith("content://")) {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                    }
                } else {
                    retriever.setDataSource(uriString)
                }
                // Aquí iría la lógica de extracción de capítulos (si la hubiera)
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Error al extraer capítulos de $uriString", e)
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(chapters = chaptersList)
            }
        }
    }

    private fun updatePlaylistState() {
        val player = controller ?: return
        val items = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            items.add(player.getMediaItemAt(i))
        }
        _uiState.value = _uiState.value.copy(
            playlist = items,
            currentIndex = player.currentMediaItemIndex
        )
    }

    private fun persistQueue() {
        val player = controller ?: return
        
        // No sobreescribir la base de datos si el reproductor está vacío y aún no hemos cargado la cola inicial
        if (player.mediaItemCount == 0 && !isQueueLoaded) return

        val items = mutableListOf<com.raulburgosmurray.musicplayer.data.QueueItem>()
        for (i in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            items.add(com.raulburgosmurray.musicplayer.data.QueueItem(mediaId = item.mediaId, orderIndex = i))
        }
        viewModelScope.launch(Dispatchers.IO) {
            queueRepository.updateFullQueue(items)
        }
    }

    private fun persistLastPlayedMediaId(mediaId: String) {
        val prefs = getApplication<Application>().getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_played_media_id", mediaId).apply()
    }

    private fun getLastPlayedMediaId(): String? {
        val prefs = getApplication<Application>().getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_played_media_id", null)
    }

    fun loadPersistedQueue(allBooks: List<com.raulburgosmurray.musicplayer.Music>) {
        val player = controller
        if (player == null) {
            pendingBooksToLoadQueue = allBooks
            return
        }

        if (player.mediaItemCount > 0) {
            isQueueLoaded = true
            return
        }

        viewModelScope.launch {
            val savedQueue = withContext(Dispatchers.IO) { queueRepository.getQueueSnapshot() }
            if (savedQueue.isEmpty()) {
                isQueueLoaded = true
                return@launch
            }

            // Filter out books that no longer exist in storage
            val existingBookIds = allBooks.map { it.id }.toSet()
            val (validItems, orphanedItems) = savedQueue.partition { existingBookIds.contains(it.mediaId) }

            // Remove orphaned items from DB
            if (orphanedItems.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    queueRepository.updateFullQueue(validItems.mapIndexed { index, item ->
                        com.raulburgosmurray.musicplayer.data.QueueItem(mediaId = item.mediaId, orderIndex = index)
                    })
                }
            }

            // Load only valid items
            val itemsToLoad = validItems.mapNotNull { savedItem ->
                allBooks.find { it.id == savedItem.mediaId }?.toMediaItem()
            }
            if (itemsToLoad.isNotEmpty()) {
                val lastMediaId = getLastPlayedMediaId()
                val startIndex = if (lastMediaId != null) {
                    itemsToLoad.indexOfFirst { it.mediaId == lastMediaId }.coerceAtLeast(0)
                } else 0
                val rawSaved = if (lastMediaId != null) {
                    withContext(Dispatchers.IO) {
                        progressRepository.getProgress(lastMediaId)?.lastPosition ?: 0L
                    }
                } else 0L
                val savedDuration = if (lastMediaId != null) {
                    withContext(Dispatchers.IO) {
                        progressRepository.getProgress(lastMediaId)?.duration ?: 0L
                    }
                } else 0L
                val savedPosition = sanitizePosition(rawSaved, sanitizeDuration(savedDuration))

                // Optimistic UI update so slider doesn't flash at 0
                _uiState.value = _uiState.value.copy(currentPosition = savedPosition)

                player.setMediaItems(itemsToLoad, startIndex, savedPosition)
                player.prepare()
                updatePlaylistState()
            }
            isQueueLoaded = true
        }
    }

    private fun updateCurrentMusicDetails(mediaId: String?) {
        if (mediaId == null) {
            _uiState.value = _uiState.value.copy(currentMusicDetails = null, currentMetadata = null)
            return
        }
        descriptionJob?.cancel()
        descriptionJob = viewModelScope.launch {
            val cachedBook = withContext(Dispatchers.IO) {
                bookRepository.getAllBooks().first().find { it.id == mediaId }
            }
            val metadata = withContext(Dispatchers.IO) {
                MetadataJsonHelper.loadMetadata(getApplication(), mediaId)
            }

            var description = cachedBook?.description
            Log.d("PlaybackVM", "Book: ${cachedBook?.title}, path=${cachedBook?.path}, existing desc=${cachedBook?.description?.take(50)}")

            if (description.isNullOrBlank() && DescriptionExtractor.isSupported() && !cachedBook?.path.isNullOrBlank()) {
                Log.d("PlaybackVM", "Attempting lazy extraction for ${cachedBook!!.path}")
                val extracted = withContext(Dispatchers.IO) {
                    DescriptionExtractor.extract(getApplication(), cachedBook!!.path)
                }
                Log.d("PlaybackVM", "Lazy extraction result: ${extracted?.take(50)}")
                if (!extracted.isNullOrBlank()) {
                    description = extracted
                    withContext(Dispatchers.IO) {
                        bookRepository.updateDescription(mediaId, extracted)
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                currentMusicDetails = cachedBook?.let { Music(it.id, it.title, it.album, it.artist, it.duration, it.path, it.artUri, it.fileSize, it.fileName, description = description) }, 
                currentMetadata = metadata
            )
        }
    }

    private fun restorePositionIfNeeded(mediaId: String) {
        viewModelScope.launch {
            val progress = withContext(Dispatchers.IO) {
                progressRepository.getProgress(mediaId)
            }
            
            Log.d("PlaybackViewModel", "restorePositionIfNeeded: mediaId=$mediaId, progress=$progress")
            
            val savedPos = progress?.lastPosition ?: 0L
            val safeSavedPos = sanitizePosition(savedPos, sanitizeDuration(progress?.duration ?: 0L))
            if (safeSavedPos > 0) {
                // Wait for player to be ready (max 3 seconds)
                val mediaController = controller
                mediaController?.let { ctrl ->
                    var attempts = 0
                    while (ctrl.playbackState != androidx.media3.common.Player.STATE_READY && attempts < 6) {
                        Log.d("PlaybackViewModel", "Waiting for player... state=${ctrl.playbackState}")
                        delay(500)
                        attempts++
                    }
                    
                    if (ctrl.playbackState == androidx.media3.common.Player.STATE_READY) {
                        val currentPos = ctrl.currentPosition
                        Log.d("PlaybackViewModel", "Restoring: currentPos=$currentPos, savedPos=$safeSavedPos")
                        ctrl.seekTo(safeSavedPos)
                        Log.d("PlaybackViewModel", "Restored position to ${safeSavedPos}ms")
                    } else {
                        Log.d("PlaybackViewModel", "Player never ready, state=${ctrl.playbackState}")
                    }
                }
            } else {
                Log.d("PlaybackViewModel", "No progress or position is 0 for mediaId=$mediaId")
            }
        }
    }

    private fun setupController() {
        val player = controller ?: return
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressUpdate()
                    smartBookmarkManager.startTracking(viewModelScope) { controller }
                } else {
                    stopProgressUpdate()
                    smartBookmarkManager.stopTracking()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _uiState.value = _uiState.value.copy(playWhenReady = playWhenReady)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Auto-mark previous book as read when playback advances automatically
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    _uiState.value.currentMusicDetails?.id?.let { previousId ->
                        viewModelScope.launch(Dispatchers.IO) {
                            progressRepository.setReadStatus(previousId, true)
                        }
                    }
                }

                val safeDuration = sanitizeDuration(player.duration)
                val safePosition = sanitizePosition(player.currentPosition, safeDuration)
                _uiState.value = _uiState.value.copy(
                    currentMediaItem = mediaItem,
                    currentIndex = player.currentMediaItemIndex,
                    duration = safeDuration,
                    currentPosition = safePosition,
                    chapters = emptyList(),
                    dominantColor = null
                )
                
                updateCurrentMusicDetails(mediaItem?.mediaId)
                updateDominantColor(mediaItem?.mediaMetadata?.artworkUri)
                mediaItem?.mediaId?.let { 
                    restorePerBookSettings(it)
                    persistLastPlayedMediaId(it)
                }
                checkTimerStartForMediaId(mediaItem?.mediaId)
                mediaItem?.localConfiguration?.uri?.toString()?.let { uriString ->
                    if (uriString != lastScannedUri) {
                        extractChapters(uriString)
                        lastScannedUri = uriString
                    }
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updatePlaylistState()
                persistQueue()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val safeDuration = sanitizeDuration(player.duration)
                val safePosition = sanitizePosition(player.currentPosition, safeDuration)
                _uiState.value = _uiState.value.copy(
                    isReady = playbackState == Player.STATE_READY,
                    duration = safeDuration,
                    currentPosition = safePosition
                )
                if (playbackState == Player.STATE_READY) {
                    attachEqualizer()
                }
                if (playbackState == Player.STATE_ENDED) {
                    _uiState.value.currentMusicDetails?.id?.let { mediaId ->
                        viewModelScope.launch(Dispatchers.IO) {
                            progressRepository.setReadStatus(mediaId, true)
                        }
                    }
                }
                // Defensive: some devices enter an unrecoverable idle state after changing pitch.
                // Reset UI so the user can retry playback instead of staying stuck.
                if (playbackState == Player.STATE_IDLE && player.playWhenReady) {
                    Log.w("PlaybackVM", "Unexpected IDLE with playWhenReady=true; resetting UI state")
                    _uiState.value = _uiState.value.copy(
                        playWhenReady = false,
                        isPlaying = false
                    )
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _uiState.value = _uiState.value.copy(playbackSpeed = playbackParameters.speed, pitch = playbackParameters.pitch)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlaybackVM", "Player error received: ${error.errorCodeName}", error)
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    playWhenReady = false
                )
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    val backward = oldPosition.positionMs - newPosition.positionMs
                    if (backward > Constants.SKIP_BACKWARD_MS * 2 && newPosition.positionMs < 5_000L) {
                        _uiState.value = _uiState.value.copy(lastPositionBeforeSeek = oldPosition.positionMs)
                        logAction(getApplication<Application>().getString(
                            R.string.history_accidental_seek,
                            formatDuration(oldPosition.positionMs)
                        ))
                    }
                }
                val safeDuration = sanitizeDuration(player.duration)
                _uiState.value = _uiState.value.copy(currentPosition = sanitizePosition(newPosition.positionMs, safeDuration))
            }
        })

        val safeDuration = sanitizeDuration(player.duration)
        _uiState.value = _uiState.value.copy(
            isConnected = true,
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            currentMediaItem = player.currentMediaItem,
            duration = safeDuration,
            currentPosition = sanitizePosition(player.currentPosition, safeDuration),
            playbackSpeed = player.playbackParameters.speed,
            pitch = player.playbackParameters.pitch,
            isReady = player.playbackState == Player.STATE_READY
        )
        updatePlaylistState()
        updateCurrentMusicDetails(player.currentMediaItem?.mediaId)
        updateDominantColor(player.currentMediaItem?.mediaMetadata?.artworkUri)
        if (player.isPlaying) startProgressUpdate()
    }

    fun skipToQueueItem(index: Int) {
        controller?.let {
            it.seekTo(index, 0)
            it.play()
        }
    }

    fun removeItemFromQueue(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveItemInQueue(fromIndex: Int, toIndex: Int) {
        controller?.moveMediaItem(fromIndex, toIndex)
    }

    fun addToQueue(music: com.raulburgosmurray.musicplayer.Music) {
        val player = controller ?: return
        
        // Evitar duplicados: verificar si el ID ya está en la cola actual
        val alreadyInQueue = (0 until player.mediaItemCount).any { 
            player.getMediaItemAt(it).mediaId == music.id 
        }
        
        if (!alreadyInQueue) {
            val mediaItem = music.toMediaItem()
            player.addMediaItem(mediaItem)
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                controller?.let {
                    if (it.isPlaying) {
                        val safePos = sanitizePosition(it.currentPosition, sanitizeDuration(it.duration))
                        _uiState.value = _uiState.value.copy(currentPosition = safePos)
                    }
                }
                delay(Constants.PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressUpdate() { progressJob?.cancel() }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                logAction(getApplication<Application>().getString(R.string.history_pause))
                it.pause()
            } else {
                logAction(getApplication<Application>().getString(R.string.history_play))
                it.play()
                // Defensive: if player is stuck in idle/zombie state, force a rebuild
                if (!it.isPlaying && it.playbackState == Player.STATE_IDLE) {
                    val currentIndex = it.currentMediaItemIndex
                    val currentPos = it.currentPosition
                    it.stop()
                    it.prepare()
                    it.seekTo(currentIndex, currentPos)
                    it.play()
                }
            }
        }
    }

    private fun saveCurrentPositionAsUndo() {
        controller?.let {
            val safePos = sanitizePosition(it.currentPosition, sanitizeDuration(it.duration))
            _uiState.value = _uiState.value.copy(lastPositionBeforeSeek = safePos)
        }
    }

    fun undoSeek() {
        val prevPos = _uiState.value.lastPositionBeforeSeek ?: return
        val rawCurrent = controller?.currentPosition ?: 0L
        val duration = controller?.duration ?: 0L
        val currentPos = sanitizePosition(rawCurrent, duration)
        logAction(getApplication<Application>().getString(R.string.history_undo_seek))
        controller?.seekTo(prevPos)
        _uiState.value = _uiState.value.copy(lastPositionBeforeSeek = currentPos)
    }

    fun seekTo(position: Long) {
        saveCurrentPositionAsUndo()
        logAction(getApplication<Application>().getString(R.string.history_manual_seek))
        controller?.let {
            // Defensive: if player is idle (e.g. after a pitch error), prepare first
            if (it.playbackState == Player.STATE_IDLE) {
                it.prepare()
            }
            it.seekTo(position)
        }
    }

    fun skipForward(millis: Long) {
        saveCurrentPositionAsUndo()
        logAction(getApplication<Application>().getString(R.string.history_skip_forward, millis/1000))
        controller?.let { it.seekTo(it.currentPosition + millis) }
    }

    fun skipBackward(millis: Long) {
        saveCurrentPositionAsUndo()
        logAction(getApplication<Application>().getString(R.string.history_skip_backward, millis/1000))
        controller?.let { it.seekTo(it.currentPosition - millis) }
    }

    fun skipByAmount(amountMs: Long, isForward: Boolean) {
        val currentPos = controller?.currentPosition ?: 0L
        val duration = controller?.duration ?: 0L
        val targetPos = if (isForward) {
            (currentPos + amountMs).coerceAtMost(duration)
        } else {
            (currentPos - amountMs).coerceAtLeast(0L)
        }
        saveCurrentPositionAsUndo()
        val labelRes = if (isForward) R.string.history_skip_by_amount_forward else R.string.history_skip_by_amount_backward
        logAction(getApplication<Application>().getString(labelRes, formatDuration(amountMs)))
        controller?.seekTo(targetPos)
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        // Persist speed per-book
        _uiState.value.currentMusicDetails?.id?.let { mediaId ->
            viewModelScope.launch(Dispatchers.IO) {
                val existing = progressRepository.getProgress(mediaId)
                if (existing != null) {
                    progressRepository.saveProgress(existing.copy(playbackSpeed = speed))
                }
            }
        }
    }

    fun setPitch(pitch: Float) {
        controller?.let { ctrl ->
            val wasPlaying = ctrl.isPlaying
            val currentPos = ctrl.currentPosition.coerceAtLeast(0L)

            // Changing pitch while playing can corrupt the AudioTrack pipeline on some devices.
            // Pause first, apply parameters, force a seek to rebuild the renderer, then resume.
            if (wasPlaying) {
                ctrl.pause()
            }
            ctrl.setPlaybackParameters(
                androidx.media3.common.PlaybackParameters(ctrl.playbackParameters.speed, pitch)
            )
            // Force renderer rebuild to avoid a zombie audio sink
            if (ctrl.playbackState != Player.STATE_IDLE) {
                ctrl.seekTo(currentPos)
            }
            if (wasPlaying) {
                ctrl.play()
            }
        }
        _uiState.value = _uiState.value.copy(pitch = pitch)
        // Persist pitch per-book
        _uiState.value.currentMusicDetails?.id?.let { mediaId ->
            viewModelScope.launch(Dispatchers.IO) {
                val existing = progressRepository.getProgress(mediaId)
                if (existing != null) {
                    progressRepository.saveProgress(existing.copy(pitch = pitch))
                }
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        originalTimerMinutes = minutes
        _uiState.value = _uiState.value.copy(sleepTimerMinutes = minutes, isShakeWaiting = false)

        // Save current position as the new timer start point (works for initial timer and shake extensions)
        val currentMediaId = controller?.currentMediaItem?.mediaId
        val currentPos = controller?.currentPosition ?: 0L
        val safePos = sanitizePosition(currentPos, controller?.duration ?: 0L)
        if (currentMediaId != null) {
            timerPrefs.edit()
                .putString("last_timer_media_id", currentMediaId)
                .putLong("last_timer_position", safePos)
                .apply()
            _uiState.value = _uiState.value.copy(
                canReturnToTimerStart = true,
                timerStartPosition = safePos
            )
            logAction(getApplication<Application>().getString(R.string.history_timer_started, formatDuration(safePos)))
        }
        
        var hasWarned = false

        sleepTimer = object : CountDownTimer(minutes * 60 * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minsRemaining = (millisUntilFinished / 1000 / 60).toInt() + 1
                if (_uiState.value.sleepTimerMinutes != minsRemaining) {
                    _uiState.value = _uiState.value.copy(sleepTimerMinutes = minsRemaining)
                }

                // Zona de advertencia: últimos 30 segundos
                if (millisUntilFinished <= Constants.SLEEP_TIMER_WARNING_MS && !hasWarned) {
                    hasWarned = true
                    if (isTimeAnnouncementEnabled) {
                        announceTime()
                    }
                    vibrate()
                    playWarningSound()
                    if (isShakeSettingEnabled) {
                        _uiState.value = _uiState.value.copy(isShakeWaiting = true)
                        startShakeDetection()
                    }
                }
            }
            override fun onFinish() { 
                stopShakeDetection()
                _uiState.value = _uiState.value.copy(isShakeWaiting = false)
                controller?.pause()
                logAction(getApplication<Application>().getString(R.string.history_timer_finished))
                cancelSleepTimer() 
            }
        }.start()
    }

    fun cancelSleepTimer() {
        stopShakeDetection()
        sleepTimer?.cancel()
        sleepTimer = null
        _uiState.value = _uiState.value.copy(sleepTimerMinutes = 0, isShakeWaiting = false)
    }

    fun returnToTimerStart() {
        val pos = _uiState.value.timerStartPosition ?: return
        seekTo(pos)
        logAction(getApplication<Application>().getString(R.string.return_to_timer_start))
    }

    private var pendingPlaylist: Pair<List<MediaItem>, Int>? = null

    fun playPlaylist(mediaItems: List<MediaItem>, startIndex: Int) {
        viewModelScope.launch {
            val player = controller
            if (player != null) {
                val mediaId = mediaItems.getOrNull(startIndex)?.mediaId
                val rawSaved = if (mediaId != null) {
                    withContext(Dispatchers.IO) {
                        progressRepository.getProgress(mediaId)?.lastPosition ?: 0L
                    }
                } else 0L
                val savedDuration = if (mediaId != null) {
                    withContext(Dispatchers.IO) {
                        progressRepository.getProgress(mediaId)?.duration ?: 0L
                    }
                } else 0L
                val savedPosition = sanitizePosition(rawSaved, sanitizeDuration(savedDuration))

                // Optimistic UI update so slider doesn't flash at 0
                _uiState.value = _uiState.value.copy(currentPosition = savedPosition)

                val isSingleBook = mediaItems.size == 1
                val alreadyHasQueue = player.mediaItemCount > 0

                if (isSingleBook && alreadyHasQueue) {
                    // Add single book to existing queue and play it without clearing
                    val newItem = mediaItems[startIndex]
                    val existingIndex = (0 until player.mediaItemCount).indexOfFirst {
                        player.getMediaItemAt(it).mediaId == newItem.mediaId
                    }
                    if (existingIndex >= 0) {
                        // Book already in queue: jump to it at saved position
                        player.seekTo(existingIndex, savedPosition)
                        player.play()
                    } else {
                        // New book: append and play it at saved position
                        player.addMediaItem(newItem)
                        player.seekTo(player.mediaItemCount - 1, savedPosition)
                        player.play()
                    }
                } else {
                    // Multi-book selection or empty player: replace queue
                    player.stop()
                    player.setMediaItems(mediaItems, startIndex, savedPosition)
                    player.prepare()
                    player.play()
                }
            } else {
                pendingPlaylist = Pair(mediaItems, startIndex)
            }
        }
    }

    fun shareProgress(context: Context) {
        val state = _uiState.value
        val title = state.currentMediaItem?.mediaMetadata?.title ?: "Audiolibro"
        val artist = state.currentMediaItem?.mediaMetadata?.artist ?: "Desconocido"
        val position = formatDuration(state.currentPosition)
        val duration = formatDuration(state.duration)
        val percentage = if (state.duration > 0) (state.currentPosition * 100 / state.duration).toInt() else 0

        val shareText = "🎧 Estoy escuchando '$title' de $artist en mi reproductor. \n¡Voy por el minuto $position de $duration ($percentage%)! 📖✨"
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Avance de audiolibro")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        
        val chooser = Intent.createChooser(intent, "Compartir avance")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareFile(context: Context) {
        val state = _uiState.value
        val currentMediaItem = state.currentMediaItem
        
        // Intentar obtener la URI desde el MediaItem o desde los detalles de música
        val uriToShare: Uri? = currentMediaItem?.localConfiguration?.uri 
            ?: state.currentMusicDetails?.path?.let { 
                if (it.startsWith("content://")) Uri.parse(it) else Uri.fromFile(File(it))
            }

        if (uriToShare == null) {
            Toast.makeText(context, "No se encontró el archivo para compartir", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val finalUri: Uri = if (uriToShare.scheme == "content") {
                uriToShare
            } else {
                val file = File(uriToShare.path ?: "")
                if (!file.exists()) {
                    Toast.makeText(context, "Archivo físico no encontrado", Toast.LENGTH_SHORT).show()
                    return
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, finalUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Compartir audiolibro")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Error al compartir archivo", e)
            Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun setEqPreset(preset: EqPreset) {
        equalizerManager.applyPreset(preset)
        _uiState.value = _uiState.value.copy(eqPreset = preset, eqAvailable = equalizerManager.isAvailable)
        // Persist eq preset per-book
        _uiState.value.currentMusicDetails?.id?.let { mediaId ->
            viewModelScope.launch(Dispatchers.IO) {
                val existing = progressRepository.getProgress(mediaId)
                if (existing != null) {
                    progressRepository.saveProgress(existing.copy(eqPresetName = preset.name))
                } else {
                    // Create a minimal record so the preset is preserved even for never-played books
                    progressRepository.saveProgress(
                        AudiobookProgress(mediaId = mediaId, lastPosition = 0L, duration = 0L, eqPresetName = preset.name)
                    )
                }
            }
        }
    }

    private fun attachEqualizer() {
        val app = getApplication<Application>() as com.raulburgosmurray.musicplayer.ApplicationClass
        val sessionId = app.audioSessionId
        if (sessionId != -1) {
            equalizerManager.attach(sessionId)
            _uiState.value = _uiState.value.copy(eqAvailable = equalizerManager.isAvailable)
        }
    }

    private fun restorePerBookSettings(mediaId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val progress = progressRepository.getProgress(mediaId)
            withContext(Dispatchers.Main) {
                if (progress != null) {
                    val speed = progress.playbackSpeed.coerceAtLeast(0.1f)
                    val pitch = progress.pitch.coerceAtLeast(0.1f)
                    controller?.setPlaybackParameters(
                        androidx.media3.common.PlaybackParameters(speed, pitch)
                    )
                    _uiState.value = _uiState.value.copy(playbackSpeed = speed, pitch = pitch)
                }
                // Restore EQ preset: saved per-book or fallback to default
                val presetToApply = when {
                    progress?.eqPresetName?.isNotEmpty() == true -> {
                        try { EqPreset.valueOf(progress.eqPresetName) } catch (_: IllegalArgumentException) { null }
                    }
                    else -> null
                }
                val finalPreset = presetToApply ?: run {
                    val settingsPrefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val defaultName = settingsPrefs.getString("default_eq_preset", EqPreset.FLAT.name) ?: EqPreset.FLAT.name
                    try { EqPreset.valueOf(defaultName) } catch (_: Exception) { EqPreset.FLAT }
                }
                equalizerManager.applyPreset(finalPreset)
                _uiState.value = _uiState.value.copy(eqPreset = finalPreset)
            }
        }
    }

    /**
     * Maneja la detección de sueño desde el reloj Amazfit.
     * Calcula el tiempo transcurrido desde que el usuario se durmió
     * y retrocede la reproducción a ese punto.
     */
    fun handleSleepDetected(sleepOnsetMinutes: Int) {
        val player = controller ?: return
        if (!player.isPlaying) {
            Log.d("PlaybackVM", "Sleep detected but player not playing, ignoring")
            return
        }

        // Leer ajustes de SharedPreferences
        val prefs = getApplication<Application>().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val rewindMinutes = prefs.getInt("sleep_rewind_minutes", 0)
        val fallbackMinutes = prefs.getInt("sleep_fallback_minutes", 0)

        // Convertir startTime (minutos desde medianoche) a timestamp
        val sleepStartMillis = zeppStartTimeToMillis(sleepOnsetMinutes)
        val nowMillis = System.currentTimeMillis()
        
        // Verificar que el tiempo de sueño sea razonable (no futuro)
        if (sleepStartMillis > nowMillis) {
            Log.w("PlaybackVM", "Sleep onset time is in the future, ignoring")
            return
        }

        // Calcular bookmark exacto usando snapshots históricos
        val bookmark = smartBookmarkManager.calculateSleepBookmark(sleepStartMillis)
        
        // Pausar inmediatamente
        player.pause()
        logAction(getApplication<Application>().getString(R.string.history_sleep_detected))
        
        val currentPos = player.currentPosition
        
        // Si tenemos un bookmark válido, retroceder al punto donde te dormiste + buffer
        if (bookmark >= 0) {
            val bufferMs = rewindMinutes * 60 * 1000L
            val targetPos = (bookmark - bufferMs).coerceAtLeast(0L)
            val rewindMs = (currentPos - targetPos).coerceAtLeast(0L)
            
            player.seekTo(targetPos)
            Log.i("PlaybackVM", "Sleep rewind: current=$currentPos, bookmark=$bookmark, buffer=${bufferMs}ms, target=$targetPos, rewound=${rewindMs}ms")
            
            if (rewindMs > 0) {
                logAction(getApplication<Application>().getString(
                    R.string.history_sleep_rewind,
                    formatDuration(rewindMs)
                ))
            }
        } else {
            // Fallback: retroceder según ajustes si no hay snapshots
            val fallbackRewindMs = fallbackMinutes * 60 * 1000L
            val rewindTo = (currentPos - fallbackRewindMs).coerceAtLeast(0L)
            
            player.seekTo(rewindTo)
            Log.w("PlaybackVM", "No bookmark data, using fallback rewind: current=$currentPos, rewindTo=$rewindTo, fallbackMinutes=$fallbackMinutes")
            
            logAction(getApplication<Application>().getString(
                R.string.history_sleep_rewind,
                formatDuration(currentPos - rewindTo)
            ))
        }
    }

    /**
     * Convierte startTime de Zepp OS (minutos desde medianoche) a timestamp UTC.
     * Maneja correctamente el cruce de medianoche.
     */
    private fun zeppStartTimeToMillis(startTimeMinutes: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val nowMinutes = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
        
        // Si startTime > nowMinutes + 120, probablemente fue ayer (cruzó medianoche)
        if (startTimeMinutes > nowMinutes + 120) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        
        cal.add(Calendar.MINUTE, startTimeMinutes)
        return cal.timeInMillis
    }

    override fun onCleared() {
        sleepTimer?.cancel()
        stopTimeAnnouncementLoop()
        smartBookmarkManager.stopTracking()
        tts?.shutdown()
        tts = null
        equalizerManager.release()
        releaseController()
        super.onCleared()
    }

    fun clearCurrentPlayback() {
        controller?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        _uiState.value = _uiState.value.copy(
            currentMediaItem = null,
            currentMusicDetails = null,
            currentMetadata = null,
            isPlaying = false,
            playWhenReady = false,
            duration = 0L,
            currentPosition = 0L,
            chapters = emptyList(),
            bookmarks = emptyList(),
            dominantColor = null
        )
    }

    fun cleanupAfterDeletion(mediaId: String) {
        val player = controller
        val isCurrent = _uiState.value.currentMediaItem?.mediaId == mediaId

        if (isCurrent && player != null) {
            clearCurrentPlayback()
        } else if (player != null) {
            // Remove from queue if present
            val idx = (0 until player.mediaItemCount).indexOfFirst {
                player.getMediaItemAt(it).mediaId == mediaId
            }
            if (idx >= 0) {
                player.removeMediaItem(idx)
                persistQueue()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            progressRepository.deleteProgress(mediaId)
            favoriteRepository.removeFavorite(mediaId)
            bookmarkRepository.deleteBookmarksForMedia(mediaId)
            MetadataJsonHelper.deleteMetadata(getApplication(), mediaId)

            // Clean up SharedPreferences
            val playbackPrefs = getApplication<Application>().getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)
            if (playbackPrefs.getString("last_played_media_id", null) == mediaId) {
                playbackPrefs.edit().remove("last_played_media_id").apply()
            }
            if (timerPrefs.getString("last_timer_media_id", null) == mediaId) {
                timerPrefs.edit().remove("last_timer_media_id").remove("last_timer_position").apply()
            }
        }
    }

    fun releaseController() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
            controllerFuture = null
            controller = null
        }
    }
}
