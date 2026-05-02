package com.raulburgosmurray.musicplayer

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.AudiobookProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {

    companion object {
        const val SYNC_ACTION = "com.raulburgosmurray.musicplayer.SYNC_PROGRESS"
    }

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var saveProgressJob: Job? = null
    private var smartRewindJob: Job? = null
    private lateinit var database: AppDatabase

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)

        // Configuración profesional para Audiolibros (Voz humana)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH) // Optimizado para voz
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // TRUE activa el manejo de Audio Focus automático
            .setHandleAudioBecomingNoisy(true)         // TRUE pausa automáticamente al desconectar audífonos
            .setWakeMode(C.WAKE_MODE_LOCAL)           // Optimizado para archivos locales
            .build().apply {
                (application as ApplicationClass).audioSessionId = audioSessionId
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.let { item ->
                            restorePositionOnTransition(item.mediaId)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val p = player ?: return
                        if (isPlaying) {
                            applySmartRewindOnPlay()
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
                        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                            saveCurrentProgress()
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
            elapsed < Constants.SMART_REWIND_VERY_SHORT_PAUSE_MS -> Constants.SMART_REWIND_VERY_SHORT_AMOUNT_MS
            elapsed < Constants.SMART_REWIND_SHORT_PAUSE_MS -> Constants.SMART_REWIND_SHORT_AMOUNT_MS
            elapsed < Constants.SMART_REWIND_MEDIUM_PAUSE_MS -> Constants.SMART_REWIND_MEDIUM_AMOUNT_MS
            elapsed < Constants.SMART_REWIND_LONG_PAUSE_MS -> Constants.SMART_REWIND_LONG_AMOUNT_MS
            else -> Constants.SMART_REWIND_VERY_LONG_AMOUNT_MS
        }
    }

    private fun restorePositionOnTransition(mediaId: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val progress = database.progressDao().getProgress(mediaId)
                withContext(Dispatchers.Main) {
                    val currentPlayer = player ?: return@withContext
                    if (progress != null) {
                        if (Math.abs(currentPlayer.currentPosition - progress.lastPosition) > 1000) {
                            currentPlayer.seekTo(progress.lastPosition)
                        }
                        // Restaurar la velocidad y pitch guardados (mínimo 0.1 para evitar errores)
                        val speed = progress.playbackSpeed.coerceAtLeast(0.1f)
                        val pitch = progress.pitch.coerceAtLeast(0.1f)
                        currentPlayer.setPlaybackParameters(
                            androidx.media3.common.PlaybackParameters(speed, pitch)
                        )
                        // Restaurar equalizer preset si existe
                        if (progress.eqPresetName.isNotEmpty()) {
                            try {
                                val preset = EqPreset.valueOf(progress.eqPresetName)
                                // El equalizer se restaurará via PlaybackViewModel.attachEqualizer
                                // Guardamos en prefs para que el ViewModel lo lea
                                getSharedPreferences("eq_prefs", MODE_PRIVATE).edit()
                                    .putString("eq_preset", preset.name).apply()
                            } catch (_: IllegalArgumentException) {}
                        }
                    } else {
                        // Libro nuevo: resetear velocidad y pitch a 1.0f por defecto
                        currentPlayer.setPlaybackParameters(
                            androidx.media3.common.PlaybackParameters(1.0f, 1.0f)
                        )
                    }
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

                    progress?.let {
                        database.progressDao().saveProgress(it.copy(lastPauseTimestamp = 0L))
                    }

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
        
        if (duration <= 0 || position < 0) return
        
        val newPauseTimestamp = if (isPausing) System.currentTimeMillis() else 0L
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val currentProgress = database.progressDao().getProgress(currentMediaItem.mediaId)
                val pauseToSave = if (isPausing) newPauseTimestamp else (currentProgress?.lastPauseTimestamp ?: 0L)
                
                // Auto-mark as read when progress reaches 99% or more
                val progressPercent = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
                val shouldMarkAsRead = progressPercent >= 0.99f
                val currentIsRead = currentProgress?.isRead ?: false
                val finalIsRead = currentIsRead || shouldMarkAsRead

                val eqName = getSharedPreferences("eq_prefs", MODE_PRIVATE)
                    .getString("eq_preset", "") ?: ""
                database.progressDao().saveProgress(
                    AudiobookProgress(
                        mediaId = currentMediaItem.mediaId,
                        lastPosition = position,
                        duration = duration,
                        lastPauseTimestamp = pauseToSave,
                        playbackSpeed = speed,
                        pitch = pitch,
                        eqPresetName = eqName,
                        isRead = finalIsRead
                    )
                )
                
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
        if (duration <= 0 || position < 0) return

        runBlocking(Dispatchers.IO) {
            try {
                val existing = database.progressDao().getProgress(currentMediaItem.mediaId)
                val progressPercent = position.toFloat() / duration.toFloat()
                val eqName = getSharedPreferences("eq_prefs", MODE_PRIVATE)
                    .getString("eq_preset", "") ?: ""
                database.progressDao().saveProgress(
                    AudiobookProgress(
                        mediaId = currentMediaItem.mediaId,
                        lastPosition = position,
                        duration = duration,
                        lastPauseTimestamp = System.currentTimeMillis(),
                        playbackSpeed = speed,
                        pitch = pitch,
                        eqPresetName = eqName,
                        isRead = (existing?.isRead ?: false) || progressPercent >= 0.99f
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
}
