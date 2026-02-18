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

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var saveProgressJob: Job? = null
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
            elapsed < 10_000 -> 2_000L
            elapsed < 300_000 -> 3_000L
            elapsed < 1_800_000 -> 5_000L
            elapsed < 7_200_000 -> 10_000L
            else -> 20_000L
        }
    }

    private fun restorePositionOnTransition(mediaId: String) {
        val p = player ?: return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val progress = database.progressDao().getProgress(mediaId) ?: return@launch
                launch(Dispatchers.Main) {
                    val currentPlayer = player ?: return@launch
                    if (Math.abs(currentPlayer.currentPosition - progress.lastPosition) > 1000) {
                        currentPlayer.seekTo(progress.lastPosition)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error restaurando posición", e)
            }
        }
    }

    private fun applySmartRewindOnPlay() {
        val p = player ?: return
        val currentItem = p.currentMediaItem ?: return
        val mediaId = currentItem.mediaId
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val progress = database.progressDao().getProgress(mediaId)
                val lastPause = progress?.lastPauseTimestamp ?: 0L
                
                if (lastPause > 0) {
                    val elapsed = System.currentTimeMillis() - lastPause
                    val rewindMs = calculateRewindMs(elapsed)
                    
                    progress?.let {
                        database.progressDao().saveProgress(it.copy(lastPauseTimestamp = 0L))
                    }

                    if (rewindMs > 0) {
                        launch(Dispatchers.Main) {
                            val currentPlayer = player ?: return@launch
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
                delay(10000) // Aumentado a 10s para reducir carga
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
            saveCurrentProgress(isPausing = true)
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
        
        if (duration <= 0 || position < 0) return
        
        val newPauseTimestamp = if (isPausing) System.currentTimeMillis() else 0L
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val currentProgress = database.progressDao().getProgress(currentMediaItem.mediaId)
                val pauseToSave = if (isPausing) newPauseTimestamp else (currentProgress?.lastPauseTimestamp ?: 0L)

                database.progressDao().saveProgress(
                    AudiobookProgress(
                        mediaId = currentMediaItem.mediaId, 
                        lastPosition = position, 
                        duration = duration,
                        lastPauseTimestamp = pauseToSave
                    )
                )
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error guardando progreso", e)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCurrentProgress(isPausing = true)
        releaseResources()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopPeriodicSave()
        saveCurrentProgress(isPausing = true)
        releaseResources()
        super.onDestroy()
    }
}
