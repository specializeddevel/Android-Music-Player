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
        // 1. Si hay un color de portada y el usuario quiere colores dinámicos
        seedColor != null && dynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current).copy(primary = seedColor) 
            else dynamicLightColorScheme(LocalContext.current).copy(primary = seedColor)
            // Nota: Para una implementación más profunda de seedColor se usaColorScheme(fromSeed)
            // Pero por simplicidad ahora usaremos esto.
            if (darkTheme) darkColorScheme(primary = seedColor) else lightColorScheme(primary = seedColor)
        }
        
        // 2. Colores dinámicos del sistema (Android 12+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

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
