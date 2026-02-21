package com.raulburgosmurray.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private fun hueToRgb(p: Float, q: Float, t: Float): Float {
    var tNorm = t
    if (tNorm < 0f) tNorm += 1f
    if (tNorm > 1f) tNorm -= 1f
    if (tNorm < 1f/6f) return p + (q - p) * 6f * tNorm
    if (tNorm < 1f/2f) return q
    if (tNorm < 2f/3f) return p + (q - p) * (2f/3f - tNorm) * 6f
    return p
}

private fun hslToColor(h: Int, s: Int, l: Int): Color {
    val hNorm = h / 360f
    val sNorm = s / 100f
    val lNorm = l / 100f
    
    val r: Float
    val g: Float
    val b: Float
    
    if (sNorm == 0f) {
        r = lNorm
        g = lNorm
        b = lNorm
    } else {
        val q = if (lNorm < 0.5f) lNorm * (1f + sNorm) else lNorm + sNorm - lNorm * sNorm
        val p = 2f * lNorm - q
        r = hueToRgb(p, q, hNorm + 1f/3f)
        g = hueToRgb(p, q, hNorm)
        b = hueToRgb(p, q, hNorm - 1f/3f)
    }
    
    return Color(r, g, b)
}

fun generateBookColors(title: String): Pair<Color, Color> {
    val hash = title.hashCode()
    
    val hue = abs(hash % 360)
    val saturation = 20 + (abs(hash / 360) % 20)
    val lightness1 = 15 + (abs(hash / 3600) % 10)
    val lightness2 = 25 + (abs(hash / 36000) % 10)
    
    return Pair(
        hslToColor(hue, saturation, lightness1),
        hslToColor((hue + 20) % 360, saturation, lightness2)
    )
}

@Composable
fun BookPlaceholder(title: String, modifier: Modifier = Modifier) {
    val colors = remember(title) { generateBookColors(title) }
    val gradient = Brush.verticalGradient(
        colors = listOf(colors.first, colors.second),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📚",
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CompactBookPlaceholder(title: String, modifier: Modifier = Modifier) {
    val colors = remember(title) { generateBookColors(title) }
    val gradient = Brush.verticalGradient(
        colors = listOf(colors.first, colors.second),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📖",
                fontSize = 18.sp
            )
            
            Spacer(Modifier.height(2.dp))
            
            Text(
                text = title.takeIf { it.length <= 12 } ?: "${title.take(10)}...",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 9.sp
            )
        }
    }
}

fun formatDuration(duration: Long): String {
    val totalSeconds = duration / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
}
