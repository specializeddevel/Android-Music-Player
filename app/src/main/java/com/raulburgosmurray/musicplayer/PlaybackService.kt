package com.raulburgosmurray.musicplayer

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.Bookmark
import com.raulburgosmurray.musicplayer.data.ProgressUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class PlaybackService : MediaSessionService() {

    private data class PlaybackSnapshot(val duration: Long, val speed: Float, val pitch: Float)

    companion object {
        const val SYNC_ACTION = "com.raulburgosmurray.musicplayer.SYNC_PROGRESS"
    }

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var saveProgressJob: Job? = null
    private var smartRewindJob: Job? = null
    private var restoreProgressJob: Job? = null
    private var isApplyingStoredParameters = false
    private val playbackSnapshots = mutableMapOf<String, PlaybackSnapshot>()
    private lateinit var database: AppDatabase

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        createPlayerAndSession()
    }

    @OptIn(UnstableApi::class)
    private fun createPlayerAndSession() {
        // Configuración profesional para Audiolibros (Voz humana)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH) // Optimizado para voz
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableAudioTrackPlaybackParams(false) // Force SonicAudioProcessor for stable pitch support

        val extractorsFactory = DefaultExtractorsFactory()
            .setMp3ExtractorFlags(
                Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,  // minBufferMs
                20_000,  // maxBufferMs
                250,     // bufferForPlaybackMs (instant startup)
                500      // bufferForPlaybackAfterRebufferMs (fast rebuffer)
            )
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // TRUE activa el manejo de Audio Focus automático
            .setHandleAudioBecomingNoisy(true)         // TRUE pausa automáticamente al desconectar audífonos
            .setWakeMode(C.WAKE_MODE_LOCAL)           // Optimizado para archivos locales
            .setLoadControl(loadControl)
            .build().apply {
                (application as ApplicationClass).audioSessionId = audioSessionId
                addListener(object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        (application as ApplicationClass).audioSessionId = audioSessionId
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.let { item ->
                            applyStoredParameters(1f, 1f)
                            restorePositionOnTransition(item.mediaId)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val p = player ?: return
                        if (isPlaying) {
                            startPeriodicSave()
                        } else {
                            stopPeriodicSave()
                            // Solo guardamos marca de pausa si el usuario pausó manualmente
                            if (!p.playWhenReady) {
                                saveCurrentProgress(isPausing = true)
                            } else {
                                saveCurrentProgress(isPausing = false)
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        player?.let(::rememberCurrentSnapshot)
                        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                            saveCurrentProgress()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val p = player ?: return
                        Log.e(
                            "PlaybackService",
                            "Player error=${error.errorCodeName}, mediaId=${p.currentMediaItem?.mediaId}, " +
                                "position=${p.currentPosition}, duration=${p.duration}, cause=${error.cause?.message}",
                            error
                        )
                        saveCurrentProgress()
                        // A damaged frame would fail again at the same position. Keep the session and
                        // queue intact, but require an explicit user retry instead of looping prepare().
                        p.playWhenReady = false
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (playWhenReady) {
                            // This callback represents a play request. Buffering during a seek does
                            // not retrigger it, so smart rewind cannot overwrite a manual destination.
                            applySmartRewindOnPlay()
                        }
                    }

                    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                        if (!isApplyingStoredParameters) {
                            // A manual change wins over a database read that is still in flight.
                            restoreProgressJob?.cancel()
                        }
                        player?.let(::rememberCurrentSnapshot)
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                            val oldMediaId = oldPosition.mediaItem?.mediaId
                            saveOutgoingProgress(oldMediaId, oldPosition.positionMs)
                            return
                        }
                        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                        val seekedMediaId = newPosition.mediaItem?.mediaId
                            ?: player?.currentMediaItem?.mediaId
                        saveOutgoingProgress(seekedMediaId, newPosition.positionMs)
                        val backward = oldPosition.positionMs - newPosition.positionMs
                        if (backward > Constants.SKIP_BACKWARD_MS * 2 && newPosition.positionMs < 5_000L) {
                            val savedPos = oldPosition.positionMs
                            val mediaId = player?.currentMediaItem?.mediaId ?: return
                            serviceScope.launch(Dispatchers.IO) {
                                try {
                                    database.bookmarkDao().insertBookmark(
                                        Bookmark(
                                             mediaId = mediaId,
                                             position = savedPos,
                                             note = getString(R.string.bookmark_accidental_seek)
                                        )
                                    )
                                } catch (e: Exception) {
                                    Log.e("PlaybackService", "Error guardando marcador de recuperación", e)
                                }
                            }
                        }
                    }
                })
            }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .build()
    }

    private fun calculateRewindMs(elapsed: Long): Long {
        return when {
            elapsed < 3_000L -> 0L // No rewind for rapid taps / immediate pauses
            elapsed < Constants.SMART_REWIND_VERY_SHORT_PAUSE_MS -> Constants.SMART_REWIND_VERY_SHORT_AMOUNT_MS
            elapsed < Constants.SMART_REWIND_SHORT_PAUSE_MS -> Constants.SMART_REWIND_SHORT_AMOUNT_MS
            elapsed < Constants.SMART_REWIND_MEDIUM_PAUSE_MS -> Constants.SMART_REWIND_MEDIUM_AMOUNT_MS
            elapsed < Constants.SMART_REWIND_LONG_PAUSE_MS -> Constants.SMART_REWIND_LONG_AMOUNT_MS
            else -> Constants.SMART_REWIND_VERY_LONG_AMOUNT_MS
        }
    }

    private fun restorePositionOnTransition(mediaId: String) {
        restoreProgressJob?.cancel()
        restoreProgressJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val progress = database.progressDao().getProgress(mediaId)
                withContext(Dispatchers.Main) {
                    val currentPlayer = player ?: return@withContext
                    if (currentPlayer.currentMediaItem?.mediaId != mediaId) return@withContext
                    if (progress != null) {
                        // Restaurar la velocidad y pitch guardados (mínimo 0.1 para evitar errores)
                        val speed = progress.playbackSpeed.coerceAtLeast(0.1f)
                        val pitch = progress.pitch.coerceAtLeast(0.1f)
                        applyStoredParameters(speed, pitch)
                        // Restaurar equalizer preset si existe
                        if (progress.eqPresetName.isNotEmpty()) {
                            try {
                                val preset = EqPreset.valueOf(progress.eqPresetName)
                                getSharedPreferences("eq_prefs", MODE_PRIVATE).edit()
                                    .putString("eq_preset", preset.name).apply()
                            } catch (_: IllegalArgumentException) {}
                        }
                        // Solo hacer seek si la posición actual no fue seteada por setMediaItems/seekTo
                        if (currentPlayer.currentPosition == 0L && progress.lastPosition > 1000L) {
                            currentPlayer.seekTo(progress.lastPosition)
                        }
                    } else {
                        // Libro nuevo: resetear velocidad y pitch a 1.0f por defecto
                        applyStoredParameters(1.0f, 1.0f)
                    }
                    rememberCurrentSnapshot(currentPlayer)
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error restaurando posición", e)
            }
        }
    }

    private fun applySmartRewindOnPlay() {
        // Cancel any in-flight rewind from a previous rapid play/pause to avoid
        // multiple concurrent seeks, which can crash ExoPlayer with BT headphones.
        smartRewindJob?.cancel()
        val p = player ?: return
        val currentItem = p.currentMediaItem ?: return
        val mediaId = currentItem.mediaId

        smartRewindJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val progress = database.progressDao().getProgress(mediaId)
                val lastPause = progress?.lastPauseTimestamp ?: 0L

                if (lastPause > 0) {
                    val elapsed = System.currentTimeMillis() - lastPause
                    val rewindMs = calculateRewindMs(elapsed)

                    database.progressDao().applyUpdate(
                        mediaId,
                        ProgressUpdate(lastPauseTimestamp = 0L)
                    )

                    if (rewindMs > 0 && isActive) {
                        withContext(Dispatchers.Main) {
                            val currentPlayer = player ?: return@withContext
                            val newPos = (currentPlayer.currentPosition - rewindMs).coerceAtLeast(0L)
                            currentPlayer.seekTo(newPos)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error en Smart Rewind", e)
            }
        }
    }

    private fun startPeriodicSave() {
        saveProgressJob?.cancel()
        saveProgressJob = serviceScope.launch {
            while (isActive) {
                delay(Constants.POSITION_SAVE_INTERVAL_MS)
                saveCurrentProgress(isPausing = false)
            }
        }
    }

    private fun stopPeriodicSave() {
        saveProgressJob?.cancel()
        saveProgressJob = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ApplicationClass.EXIT) {
            stopPeriodicSave()
            saveCurrentProgressBlocking()
            releaseResources()
            stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun releaseResources() {
        player?.let {
            it.pause()
            it.stop()
            it.release()
        }
        player = null
        (application as ApplicationClass).audioSessionId = -1
        mediaSession?.let {
            it.release()
            mediaSession = null
        }
    }

    private fun saveCurrentProgress(isPausing: Boolean = false) {
        val p = player ?: return
        if (p.playbackState == Player.STATE_IDLE) return
        
        val currentMediaItem = p.currentMediaItem ?: return
        val position = p.currentPosition
        val duration = p.duration
        val speed = p.playbackParameters.speed
        val pitch = p.playbackParameters.pitch
        rememberCurrentSnapshot(p)
        
        // Defensive: reject obviously corrupt values before they poison the DB
        if (duration <= 0 || position < 0 || position > duration * 2) return
        if (duration > com.raulburgosmurray.musicplayer.ui.PlaybackViewModel.MAX_REASONABLE_DURATION_MS) return
        
        val newPauseTimestamp = if (isPausing) System.currentTimeMillis() else 0L
        
        val rawMediaId = currentMediaItem.mediaId
        val mainMediaId = rawMediaId.substringBefore("_part_")

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Auto-mark as read when progress reaches 99% or more
                val progressPercent = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
                val shouldMarkAsRead = progressPercent >= 0.99f
                database.progressDao().applyUpdate(
                    mainMediaId,
                    ProgressUpdate(
                        lastPosition = position,
                        duration = duration,
                        lastPauseTimestamp = if (isPausing) newPauseTimestamp else null,
                        playbackSpeed = speed,
                        pitch = pitch,
                        isRead = if (shouldMarkAsRead) true else null
                    )
                )

                if (mainMediaId.startsWith("abs_")) {
                    val absRepo = com.raulburgosmurray.musicplayer.data.AudiobookshelfRepository(applicationContext)
                    absRepo.syncPlaybackProgress(
                        mediaId = mainMediaId,
                        positionMs = position,
                        durationMs = duration,
                        isFinished = progressPercent >= 0.99f
                    )
                }

                // Si estamos pausando, notificamos a la app que debe sincronizar con la nube
                if (isPausing) {
                    val syncIntent = Intent(SYNC_ACTION)
                    sendBroadcast(syncIntent)
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error guardando progreso", e)
            }
        }
    }

    /**
     * Saves progress synchronously using runBlocking to guarantee completion before the
     * service process is killed. Only called during shutdown paths (EXIT, onTaskRemoved,
     * onDestroy). Normal playback saves use the async saveCurrentProgress().
     */
    private fun saveCurrentProgressBlocking() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_IDLE) return
        val currentMediaItem = p.currentMediaItem ?: return
        val position = p.currentPosition
        val duration = p.duration
        val speed = p.playbackParameters.speed
        val pitch = p.playbackParameters.pitch
        if (duration <= 0 || position < 0 || position > duration * 2) return
        if (duration > com.raulburgosmurray.musicplayer.ui.PlaybackViewModel.MAX_REASONABLE_DURATION_MS) return

        runBlocking(Dispatchers.IO) {
            try {
                val progressPercent = position.toFloat() / duration.toFloat()
                database.progressDao().applyUpdate(
                    currentMediaItem.mediaId,
                    ProgressUpdate(
                        lastPosition = position,
                        duration = duration,
                        lastPauseTimestamp = System.currentTimeMillis(),
                        playbackSpeed = speed,
                        pitch = pitch,
                        isRead = if (progressPercent >= 0.99f) true else null
                    )
                )
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error guardando progreso al cerrar", e)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPeriodicSave()
        saveCurrentProgressBlocking()
        releaseResources()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopPeriodicSave()
        saveCurrentProgressBlocking()
        releaseResources()
        super.onDestroy()
    }

    private fun saveOutgoingProgress(mediaId: String?, position: Long) {
        if (mediaId == null || position < 0L) return
        val snapshot = playbackSnapshots[mediaId] ?: return
        if (snapshot.duration <= 0L) return
        serviceScope.launch(Dispatchers.IO) {
            database.progressDao().applyUpdate(
                mediaId,
                ProgressUpdate(
                    lastPosition = position.coerceAtMost(snapshot.duration),
                    duration = snapshot.duration,
                    playbackSpeed = snapshot.speed,
                    pitch = snapshot.pitch
                )
            )
        }
    }

    private fun rememberCurrentSnapshot(currentPlayer: Player) {
        val mediaId = currentPlayer.currentMediaItem?.mediaId ?: return
        val duration = currentPlayer.duration.takeIf { it > 0L }
            ?: playbackSnapshots[mediaId]?.duration
            ?: 0L
        playbackSnapshots[mediaId] = PlaybackSnapshot(
            duration,
            currentPlayer.playbackParameters.speed,
            currentPlayer.playbackParameters.pitch
        )
    }

    private fun applyStoredParameters(speed: Float, pitch: Float) {
        val currentPlayer = player ?: return
        isApplyingStoredParameters = true
        try {
            currentPlayer.setPlaybackParameters(
                androidx.media3.common.PlaybackParameters(speed, pitch)
            )
        } finally {
            isApplyingStoredParameters = false
        }
    }
}
