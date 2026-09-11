package com.raulburgosmurray.musicplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkColorSchemeTest {
    @Test
    fun `artwork palettes keep text readable for bright dark and neutral covers`() {
        val covers = listOf(
            Color.White, Color.Black, Color.Yellow, Color.Cyan, Color.Blue,
            Color.Red, Color.Green, Color.Gray, Color(0xFFFF8800), Color(0xFF321540)
        )
        for (dark in listOf(false, true)) {
            for (cover in covers) {
                val scheme = artworkColorScheme(cover, dark)
                val pairs = listOf(
                    scheme.onPrimary to scheme.primary,
                    scheme.primary to scheme.surface,
                    scheme.primary to scheme.primaryContainer,
                    scheme.onPrimaryContainer to scheme.primaryContainer,
                    scheme.onSecondary to scheme.secondary,
                    scheme.secondary to scheme.surface,
                    scheme.onSecondaryContainer to scheme.secondaryContainer,
                    scheme.onSurface to scheme.background,
                    scheme.onSurfaceVariant to scheme.surfaceVariant,
                    scheme.onSurfaceVariant to scheme.surfaceContainerLow,
                    scheme.onSurfaceVariant to scheme.primaryContainer
                )
                for ((foreground, background) in pairs) {
                    val lighter = maxOf(foreground.luminance(), background.luminance())
                    val darker = minOf(foreground.luminance(), background.luminance())
                    assertTrue("Insufficient contrast for $cover, dark=$dark", (lighter + 0.05f) / (darker + 0.05f) >= 4.5f)
                    assertEquals(1f, background.alpha, 0f)
                }
            }
        }
    }
}
