package com.raulburgosmurray.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BookPlaceholder(
    title: String,
    author: String? = null,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), 
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFD54F)
    )
    val backgroundColor = colors[Math.abs(title.hashCode()) % colors.size]

    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (isLarge) {
            // Diseño de Cubierta Completa para el Reproductor
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (author != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = author,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        } else {
            // Diseño de Inicial para la Lista y MiniPlayer
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
