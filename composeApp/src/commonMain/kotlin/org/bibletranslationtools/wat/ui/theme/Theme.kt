package org.bibletranslationtools.wat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = Color(0xFFd85900),
    secondary = Color(0xFF3384AD),
    background = Color(0xFFF3F3F3),
    surface = Color(0xFFF3F3F3),
    onPrimary = Color(0xFFF3F3F3),
    onSecondary = Color.White,
    onBackground = Color(0xFF444444),
    onSurface = Color(0xFF444444)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE08500),
    secondary = Color(0xFF4496BD),
    background = Color(0xFF19191A),
    surface = Color(0xFF19191A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFC9C9C9),
    onSurface = Color(0xFFC9C9C9)
)

object CommonColors {
    val SemiTransparent = Color(0x88000000)
}

@Composable
fun MainAppTheme(
    themeColorScheme: ColorScheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        themeColorScheme != null -> themeColorScheme
        isSystemInDarkTheme() -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
