package com.raulburgosmurray.musicplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LiteraBlueDark,
    onPrimary = Color(0xFF003258),
    primaryContainer = LiteraBlueContainerDark,
    onPrimaryContainer = Color(0xFFD4E4FF),
    secondary = LiteraSlateDark,
    secondaryContainer = LiteraSlateContainerDark,
    tertiary = LiteraAmberDark,
    tertiaryContainer = LiteraAmberContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = LiteraBlue,
    onPrimary = Color.White,
    primaryContainer = LiteraBlueContainer,
    onPrimaryContainer = Color(0xFF001C38),
    secondary = LiteraSlate,
    secondaryContainer = LiteraSlateContainer,
    tertiary = LiteraAmber,
    tertiaryContainer = LiteraAmberContainer
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Colores dinámicos de Android 12+
    seedColor: Color? = null,    // Color extraído de la portada
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 1. Prioridad: Si hay un color de portada (Seed Color)
        seedColor != null && dynamicColor -> {
            remember(seedColor, darkTheme) {
                artworkColorScheme(seedColor, darkTheme)
            }
        }
        
        // 2. Colores dinámicos del sistema (SOLO Android 12+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // 3. Esquemas por defecto
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LiteraTypography,
        content = content
    )
}
