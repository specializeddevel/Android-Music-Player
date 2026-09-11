package com.raulburgosmurray.musicplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Keeps the cover hue while reserving enough contrast for text and playback controls. */
internal fun artworkColorScheme(seed: Color, darkTheme: Boolean): ColorScheme {
    val opaqueSeed = seed.copy(alpha = 1f)
    val primary = readableAccent(opaqueSeed, darkTheme)
    val neutral = if (darkTheme) Color(0xFF121416) else Color(0xFFFCFCFA)
    val ink = if (darkTheme) Color(0xFFF2F3F4) else Color(0xFF202326)
    val secondaryInk = if (darkTheme) Color(0xFFC8CCD0) else Color(0xFF50545A)
    fun surface(amount: Float) = lerp(neutral, primary, amount)
    val container = surface(if (darkTheme) 0.10f else 0.07f)
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()

    return base.copy(
        primary = primary,
        onPrimary = if (darkTheme) Color.Black else Color.White,
        primaryContainer = container,
        onPrimaryContainer = ink,
        inversePrimary = readableAccent(opaqueSeed, !darkTheme),
        secondary = secondaryInk,
        onSecondary = if (darkTheme) Color.Black else Color.White,
        secondaryContainer = container,
        onSecondaryContainer = ink,
        tertiary = primary,
        onTertiary = if (darkTheme) Color.Black else Color.White,
        tertiaryContainer = container,
        onTertiaryContainer = ink,
        background = surface(0.01f),
        onBackground = ink,
        surface = surface(0.01f),
        onSurface = ink,
        surfaceVariant = surface(0.08f),
        onSurfaceVariant = secondaryInk,
        surfaceTint = primary,
        surfaceDim = surface(if (darkTheme) 0f else 0.10f),
        surfaceBright = surface(if (darkTheme) 0.12f else 0f),
        surfaceContainerLowest = neutral,
        surfaceContainerLow = surface(0.03f),
        surfaceContainer = surface(0.05f),
        surfaceContainerHigh = surface(0.07f),
        surfaceContainerHighest = surface(0.10f),
        outline = lerp(neutral, ink, 0.55f),
        outlineVariant = lerp(neutral, ink, 0.20f)
    )
}

private fun readableAccent(seed: Color, darkTheme: Boolean): Color {
    val target = if (darkTheme) 0.60f else 0.10f
    if (if (darkTheme) seed.luminance() >= target else seed.luminance() <= target) return seed
    val endpoint = if (darkTheme) Color.White else Color.Black
    var low = 0f
    var high = 1f
    repeat(16) {
        val fraction = (low + high) / 2f
        val candidate = lerp(seed, endpoint, fraction)
        if (if (darkTheme) candidate.luminance() >= target else candidate.luminance() <= target) {
            high = fraction
        } else {
            low = fraction
        }
    }
    return lerp(seed, endpoint, high)
}
