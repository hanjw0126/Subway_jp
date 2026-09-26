package io.github.jpsubway.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF3B5BDB)
val FromGreen = Color(0xFF2F9E44)
val ToRed = Color(0xFFE03131)
val DelayOrange = Color(0xFFF08C00)
val MapBackground = Color(0xFFFAFAFA)

private val LightColors = lightColorScheme(
    primary = Brand,
    secondary = Color(0xFF12B886),
    background = Color(0xFFF6F7F9),
    surface = Color.White,
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF91A7FF),
    secondary = Color(0xFF63E6BE),
)

@Composable
fun JpSubwayTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography(), content = content)
}

/** "#RRGGBB" / "RRGGBB" / "#AARRGGBB" → Color */
fun parseColor(hex: String, fallback: Color = Color(0xFF868E96)): Color = try {
    val h = hex.trim().removePrefix("#")
    when (h.length) {
        6 -> Color(0xFF000000 or h.toLong(16))
        8 -> Color(h.toLong(16))
        else -> fallback
    }
} catch (e: NumberFormatException) {
    fallback
}
