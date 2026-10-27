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

class EqualizerManager(private val createEqualizer: (Int) -> Equalizer = { Equalizer(0, it) }) {
    private var equalizer: Equalizer? = null
    private var sessionId: Int = -1

    val isAvailable: Boolean get() = equalizer != null

    var currentPreset: EqPreset = EqPreset.FLAT
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) {
            release()
            return
        }
        if (audioSessionId == sessionId) return
        release()
        try {
            equalizer = createEqualizer(audioSessionId)
            check(equalizer!!.setEnabled(true) == android.media.audiofx.AudioEffect.SUCCESS) {
                "Could not enable equalizer"
            }
            sessionId = audioSessionId
            applyPreset(currentPreset)
            Log.d(TAG, "Equalizer attached to session $audioSessionId")
        } catch (e: Exception) {
            release()
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
                    boostBand(eq, 1, 1.0f)
                    boostBand(eq, 2, 1.5f)
                    boostBand(eq, 3, 2.0f)
                    boostBand(eq, 4, 1.5f)
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
                    boostBand(eq, 3, 2.0f)
                    boostBand(eq, 4, 2.5f)
                    boostBand(eq, 5, 1.5f)
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

    private fun boostBand(eq: Equalizer, band: Int, boostDb: Float) {
        setBandLevel(eq, band, boostDb)
    }

    private fun cutBand(eq: Equalizer, band: Int, level: Float) {
        setBandLevel(eq, band, level)
    }

    private fun setBandLevel(eq: Equalizer, band: Int, levelDb: Float) {
        if (band !in 0 until eq.numberOfBands.toInt()) return

        val supportedRange = eq.bandLevelRange
        val requestedLevel = (levelDb * 100).toInt()
        val safeLevel = requestedLevel.coerceIn(
            supportedRange.first().toInt(),
            supportedRange.last().toInt()
        )
        eq.setBandLevel(band.toShort(), safeLevel.toShort())
    }
}
