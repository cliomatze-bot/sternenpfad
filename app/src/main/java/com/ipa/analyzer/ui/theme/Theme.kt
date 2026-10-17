package com.ipa.analyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Black = Color(0xFF09090C)
val Anthracite = Color(0xFF141419)
val AnthraciteHigh = Color(0xFF1C1C23)
val AnthraciteHighest = Color(0xFF26262F)
val Violet = Color(0xFFA48BFF)
val VioletDeep = Color(0xFF6C4FD8)
val VioletContainer = Color(0xFF2C2350)
val TextPrimary = Color(0xFFEDECF2)
val TextSecondary = Color(0xFFA09FAD)
val Positive = Color(0xFF5FD39A)
val Negative = Color(0xFFFF6B7A)
val Neutral = Color(0xFF8E8D9A)
val Outline = Color(0xFF34343F)

private val colors = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF160B3D),
    primaryContainer = VioletContainer,
    onPrimaryContainer = Color(0xFFE3DBFF),
    secondary = VioletDeep,
    onSecondary = Color.White,
    secondaryContainer = AnthraciteHighest,
    onSecondaryContainer = TextPrimary,
    background = Black,
    onBackground = TextPrimary,
    surface = Anthracite,
    onSurface = TextPrimary,
    surfaceVariant = AnthraciteHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Anthracite,
    surfaceContainerHigh = AnthraciteHigh,
    surfaceContainerHighest = AnthraciteHighest,
    surfaceContainerLow = Color(0xFF101014),
    surfaceContainerLowest = Black,
    outline = Outline,
    outlineVariant = Color(0xFF26262F),
    error = Negative,
    onError = Color(0xFF3D0710),
    errorContainer = Color(0xFF4A1820),
    onErrorContainer = Color(0xFFFFDADF),
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AnalyzerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
