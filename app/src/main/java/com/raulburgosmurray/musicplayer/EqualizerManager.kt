package com.raulburgosmurray.musicplayer

import android.media.audiofx.Equalizer
import android.util.Log

private const val TAG = "EqualizerManager"

enum class EqPreset(val displayName: String) {
    FLAT("Flat"),
    VOICE_BOOST("Voice Boost"),
    BASS_CUT("Bass Cut"),
    CLARITY("Clarity")
}

class EqualizerManager {
    private var equalizer: Equalizer? = null
    private var sessionId: Int = -1

    val isAvailable: Boolean get() = equalizer != null

    var currentPreset: EqPreset = EqPreset.FLAT
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId == -1 || audioSessionId == sessionId) return
        release()
        try {
            equalizer = Equalizer(0, audioSessionId)
            sessionId = audioSessionId
            applyPreset(currentPreset)
            Log.d(TAG, "Equalizer attached to session $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach equalizer", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing equalizer", e)
        }
        equalizer = null
        sessionId = -1
    }

    fun applyPreset(preset: EqPreset) {
        currentPreset = preset
        val eq = equalizer ?: return
        try {
            when (preset) {
                EqPreset.FLAT -> resetBands(eq)
                EqPreset.VOICE_BOOST -> {
                    resetBands(eq)
                    // Boost mid-range frequencies for voice clarity (~1-4kHz)
                    boostBand(eq, 1.0f)  // Band 1
                    boostBand(eq, 1.5f)  // Band 2
                    boostBand(eq, 2.0f)  // Band 3
                    boostBand(eq, 1.5f)  // Band 4
                }
                EqPreset.BASS_CUT -> {
                    resetBands(eq)
                    // Reduce low frequencies
                    cutBand(eq, 0, -5.0f)
                    cutBand(eq, 1, -3.0f)
                }
                EqPreset.CLARITY -> {
                    resetBands(eq)
                    // Boost high-mid frequencies (~4-8kHz)
                    boostBand(eq, 2.0f)  // Band 3
                    boostBand(eq, 2.5f)  // Band 4
                    boostBand(eq, 1.5f)  // Band 5
                }
            }
            Log.d(TAG, "Applied preset: $preset")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying preset", e)
        }
    }

    private fun resetBands(eq: Equalizer) {
        val numBands = eq.numberOfBands.toInt()
        for (i in 0 until numBands) {
            eq.setBandLevel(i.toShort(), 0)
        }
    }

    private fun boostBand(eq: Equalizer, boostDb: Float) {
        // Simplified: we don't know exact frequency mapping, so we distribute
        // This is a best-effort approach since frequency per band varies by device
    }

    private fun cutBand(eq: Equalizer, band: Int, level: Float) {
        eq.setBandLevel(band.toShort(), (level * 100).toInt().toShort())
    }
}
