package com.rgbtv.app.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Premium Cinematic design tokens (§2). */
object Cine {
    val bg = Color(0xFF07080C)
    val bg2 = Color(0xFF0E1016)
    val card = Color(0xFF151821)
    val card2 = Color(0xFF1C202B)
    val blue = Color(0xFF3B82F6)
    val blueHi = Color(0xFF60A5FA)
    val live = Color(0xFFEF4444)
    val ok = Color(0xFF22C55E)
    val warn = Color(0xFFF59E0B)
    val text = Color(0xFFFFFFFF)
    val sub = Color(0xFFA1A8B7)
    val dim = Color(0xFF697386)
    val line = Color(0xFF252A36)
    val accent = blue
    val accentDark = Color(0xFF1E40AF)
    val onAccent = text
    val red = live

    val display = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = text)
    val h1 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = text)
    val h2 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text)
    val h3 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = text)
    val body = TextStyle(fontSize = 15.sp, color = text)
    val small = TextStyle(fontSize = 13.sp, color = sub)
    val cap = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = sub)
    val section = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = blue)
    val cat = section
    val tiny = TextStyle(fontSize = 11.sp, color = dim)
}

private val Scheme = darkColorScheme(
    primary = Cine.blue,
    onPrimary = Cine.text,
    secondary = Cine.blue,
    background = Cine.bg,
    onBackground = Cine.text,
    surface = Cine.bg2,
    onSurface = Cine.text,
    surfaceVariant = Cine.card,
    onSurfaceVariant = Cine.sub,
    error = Cine.live,
    outline = Cine.line
)

@Composable
fun CinematicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
