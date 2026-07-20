package org.bibletranslationtools.wat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import wordanalysistool.shared.generated.resources.Res
import wordanalysistool.shared.generated.resources.noto_sans_arabic_bold
import wordanalysistool.shared.generated.resources.noto_sans_arabic_regular
import wordanalysistool.shared.generated.resources.noto_sans_bold
import wordanalysistool.shared.generated.resources.noto_sans_chinese_simplified_bold
import wordanalysistool.shared.generated.resources.noto_sans_chinese_simplified_regular
import wordanalysistool.shared.generated.resources.noto_sans_korean_bold
import wordanalysistool.shared.generated.resources.noto_sans_korean_regular
import wordanalysistool.shared.generated.resources.noto_sans_malayalam_bold
import wordanalysistool.shared.generated.resources.noto_sans_malayalam_regular
import wordanalysistool.shared.generated.resources.noto_sans_regular
import wordanalysistool.shared.generated.resources.noto_serif_tibetan_bold
import wordanalysistool.shared.generated.resources.noto_serif_tibetan_regular

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF478CFF),
    primaryContainer = Color(0xFFEEF0FF),
    secondary = Color(0xFFE99A2E),
    secondaryContainer = Color(0xFFFFEEDF),
    tertiary = Color(0xFF63C76C),
    tertiaryContainer = Color(0xFFE2F7E7),
    onTertiaryContainer = Color(0xFF999999),
    background = Color(0xFFF2F2F2),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFC3362D),
    onPrimary = Color(0xFFF3F3F3),
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF444444),
    onSurface = Color(0xFF0F2F4C),
    onSurfaceVariant = Color(0xFF516B86),
    scrim = Color(0xFF444444),
    outline = Color(0xFFE6E6E6)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4B8EFF),
    primaryContainer = Color(0xFFEEF0FF),
    secondary = Color(0xff9c6f33),
    secondaryContainer = Color(0xff534a40),
    tertiary = Color(0xFF7EE588),
    tertiaryContainer = Color(0xFFE2F7E7),
    onTertiaryContainer = Color(0xFF999999),
    background = Color(0xFF141516),
    surface = Color(0xFF0F1011),
    error = Color(0xFFFF6B62),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFC9C9C9),
    onSurface = Color(0xFFC9C9C9),
    onSurfaceVariant = Color(0xFF516B86),
    scrim = Color(0xFF444444),
    outline = Color(0xFFE6E6E6)
)

@Composable
fun defaultFontFamily() = FontFamily(
    Font(Res.font.noto_sans_regular, FontWeight.Normal),
    Font(Res.font.noto_sans_bold, FontWeight.Bold)
)

@Composable
fun arabicFontFamily() = FontFamily(
    Font(Res.font.noto_sans_arabic_regular, FontWeight.Normal),
    Font(Res.font.noto_sans_arabic_bold, FontWeight.Bold)
)

@Composable
fun chineseSimplifiedFontFamily() = FontFamily(
    Font(Res.font.noto_sans_chinese_simplified_regular, FontWeight.Normal),
    Font(Res.font.noto_sans_chinese_simplified_bold, FontWeight.Bold)
)

@Composable
fun tibetanFontFamily() = FontFamily(
    Font(Res.font.noto_serif_tibetan_regular, FontWeight.Normal),
    Font(Res.font.noto_serif_tibetan_bold, FontWeight.Bold)
)

@Composable
fun koreanFontFamily() = FontFamily(
    Font(Res.font.noto_sans_korean_regular, FontWeight.Normal),
    Font(Res.font.noto_sans_korean_bold, FontWeight.Bold)
)

@Composable
fun malayalamFontFamily() = FontFamily(
    Font(Res.font.noto_sans_malayalam_regular, FontWeight.Normal),
    Font(Res.font.noto_sans_malayalam_bold, FontWeight.Bold)
)

@Composable
fun getFontFamilyForText(text: String): FontFamily {
    val arabicRegex = Regex(".*[\\u0600-\\u06FF].*")
    val chineseSimplifiedRegex = Regex(".*[\\u4E00-\\u9FFF\\u3400-\\u4DBF\\uFF00-\\uFFEF].*")
    val tibetanRegex = Regex(".*[\\u0F00-\\u0FFF].*")
    val koreanRegex = Regex(".*[\\uAC00-\\uD7A3\\u1100-\\u11FF\\u3130-\\u318F\\uA960-\\uA97F\\uD7B0-\\uD7FF].*")
    val malayalamRegex = Regex(".*[\\u0D00-\\u0D7F].*")

    return when {
        arabicRegex.matches(text) -> arabicFontFamily()
        chineseSimplifiedRegex.matches(text) -> chineseSimplifiedFontFamily()
        tibetanRegex.matches(text) -> tibetanFontFamily()
        koreanRegex.matches(text) -> koreanFontFamily()
        malayalamRegex.matches(text) -> malayalamFontFamily()
        else -> defaultFontFamily()
    }
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
