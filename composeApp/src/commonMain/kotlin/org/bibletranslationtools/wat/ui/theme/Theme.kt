package org.bibletranslationtools.wat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.noto_sans
import wordanalysistool.composeapp.generated.resources.noto_sans_arabic

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0056D1),
    secondary = Color(0xFFE99A2E),
    tertiary = Color(0xFF63C76C),
    background = Color(0xFFF2F2F2),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFC3362D),
    onPrimary = Color(0xFFF3F3F3),
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF444444),
    onSurface = Color(0xFF444444)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4B8EFF),
    secondary = Color(0xFFFFB655),
    tertiary = Color(0xFF7EE588),
    background = Color(0xFF141516),
    surface = Color(0xFF0F1011),
    error = Color(0xFFFF6B62),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFC9C9C9),
    onSurface = Color(0xFFC9C9C9)
)

val ColorScheme.semiTransparent: Color
    @Composable get() = Color(0x88000000)

@Composable
fun NotoSansFontFamily() = FontFamily(
    Font(Res.font.noto_sans, FontWeight.Normal),
)

@Composable
fun NotoSansArabicFontFamily() = FontFamily(
    Font(Res.font.noto_sans_arabic, FontWeight.Normal),
)

@Composable
fun NotoSansTypography(fontFamily: FontFamily) = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily =  fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily)
    )
}

@Composable
fun MainAppTheme(
    themeColorScheme: ColorScheme? = null,
    fontFamily: FontFamily = NotoSansFontFamily(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        themeColorScheme != null -> themeColorScheme
        isSystemInDarkTheme() -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
        typography = NotoSansTypography(fontFamily)
    )
}
