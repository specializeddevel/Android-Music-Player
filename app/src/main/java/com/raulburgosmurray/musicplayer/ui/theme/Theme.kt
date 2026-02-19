package com.raulburgosmurray.musicplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
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
            // Generamos un esquema basado en el color de la portada de forma manual (Compatible con todas las versiones)
            if (darkTheme) {
                darkColorScheme(primary = seedColor, onPrimary = Color.White, primaryContainer = seedColor.copy(alpha = 0.3f))
            } else {
                lightColorScheme(primary = seedColor, onPrimary = Color.White, primaryContainer = seedColor.copy(alpha = 0.1f))
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
        typography = Typography,
        content = content
    )
}
