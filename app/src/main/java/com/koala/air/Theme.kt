package com.koala.air

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val Navy = Color(0xFF00071A)
val Navy2 = Color(0xFF0A1630)
val Card = Color(0xFF101C38)
val CardHi = Color(0xFF1B2A4E)
val Line = Color(0xFF253661)
val Yellow = Color(0xFFFFD23F)
val Ice = Color(0xFF6FD3FF)
val Heat = Color(0xFFFF8A3D)
val Teal = Color(0xFF4FE3C1)
val Lavender = Color(0xFFB9A6FF)
val Red = Color(0xFFE63946)
val Muted = Color(0xFF8C9AB8)
val Soft = Color(0xFFC9D3EA)

val Raleway = FontFamily(
    Font(R.font.raleway_medium, FontWeight.Medium),
    Font(R.font.raleway_medium, FontWeight.Normal),
    Font(R.font.raleway_bold, FontWeight.Bold),
    Font(R.font.raleway_black, FontWeight.Black),
)

val BgBrush = Brush.verticalGradient(listOf(Navy, Navy2, Navy))

/** Couleur d'accent selon le mode (t_work_mode). */
fun modeColor(mode: String?) = when (mode) {
    "2" -> Ice
    "1" -> Heat
    "3" -> Teal
    "0" -> Lavender
    else -> Yellow
}

fun modeGlyph(mode: String?) = when (mode) {
    "2" -> G.Snow
    "1" -> G.Sun
    "3" -> G.Drop
    "0" -> G.Fan
    else -> G.Auto
}

@Composable
fun KoalairTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Yellow, background = Navy, surface = Card)) {
        // Raleway avec chiffres alignés (lnum) : sinon ses chiffres « elzéviriens » dansent sur la ligne
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = Raleway, fontWeight = FontWeight.Medium, color = Color.White, fontFeatureSettings = "lnum")
        ) { content() }
    }
}
