package com.raulburgosmurray.musicplayer.sleep

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.media3.session.MediaController

/**
 * Gestiona snapshots periódicos del estado de reproducción
 * para calcular el bookmark exacto cuando se detecta sueño.
 */
class SmartBookmarkManager {

    data class PlaybackSnapshot(
        val position: Long,        // posición en ms del audiolibro
        val timestamp: Long,         // System.currentTimeMillis()
        val playbackSpeed: Float     // velocidad actual (1.0x, 1.5x, etc.)
    )

    private val snapshots = mutableListOf<PlaybackSnapshot>()
    private var trackingJob: Job? = null

    /**
     * Inicia el tracking periódico de snapshots.
     * @param scope CoroutineScope donde ejecutar el job
     * @param playerProvider Lambda que retorna el MediaController actual
     */
    fun startTracking(scope: CoroutineScope, playerProvider: () -> MediaController?) {
        stopTracking()
        trackingJob = scope.launch {
            while (isActive) {
                playerProvider()?.let { player ->
                    if (player.isPlaying) {
                        snapshots.add(
                            PlaybackSnapshot(
                                position = player.currentPosition.coerceAtLeast(0L),
                                timestamp = System.currentTimeMillis(),
                                playbackSpeed = player.playbackParameters.speed.coerceAtLeast(0.1f)
                            )
                        )
                        pruneOldSnapshots()
                    }
                }
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }
    }

    /**
     * Detiene el tracking.
     */
    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        snapshots.clear()
    }

    /**
     * Calcula la posición exacta en el audiolibro correspondiente
     * al momento en que el usuario se durmió.
     *
     * @param sleepStartTimeMillis Timestamp (ms) estimado de inicio de sueño
     * @return Posición en ms donde se durmió, o -1 si no hay datos suficientes
     */
    fun calculateSleepBookmark(sleepStartTimeMillis: Long): Long {
        if (snapshots.isEmpty()) return -1L

        // Encontrar snapshot más cercano ANTES del momento de dormirse
        val closestSnapshot = snapshots
            .filter { it.timestamp <= sleepStartTimeMillis }
            .maxByOrNull { it.timestamp }
            ?: return -1L

        // Calcular cuánto audio avanzó entre el snapshot y el momento de dormirse
        val timeDeltaMs = sleepStartTimeMillis - closestSnapshot.timestamp
        val audioAdvanceMs = (timeDeltaMs * closestSnapshot.playbackSpeed).toLong()

        return (closestSnapshot.position + audioAdvanceMs).coerceAtLeast(0L)
    }

    /**
     * Elimina snapshots más antiguos que 1 hora para ahorrar memoria.
     */
    private fun pruneOldSnapshots() {
        val cutoff = System.currentTimeMillis() - MAX_SNAPSHOT_AGE_MS
        snapshots.removeAll { it.timestamp < cutoff }
    }

    companion object {
        private const val SNAPSHOT_INTERVAL_MS = 30_000L  // cada 30 segundos
        private const val MAX_SNAPSHOT_AGE_MS = 3_600_000L  // 1 hora
    }
}
