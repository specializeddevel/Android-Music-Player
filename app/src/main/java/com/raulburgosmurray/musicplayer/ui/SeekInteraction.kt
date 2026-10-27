package com.raulburgosmurray.musicplayer.ui

fun seekPositionForFractionOrNull(fraction: Float, duration: Long): Long? {
    if (duration <= 0L || duration > PlaybackViewModel.MAX_REASONABLE_DURATION_MS) return null
    return (fraction.coerceIn(0f, 1f) * duration).toLong().coerceIn(0L, duration)
}

fun seekPositionForFraction(fraction: Float, duration: Long): Long =
    seekPositionForFractionOrNull(fraction, duration) ?: 0L
