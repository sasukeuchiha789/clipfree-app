package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

private val ClipFreeDarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color(0xFF140D2A),
    primaryContainer = Color(0xFF3A296E),
    onPrimaryContainer = DarkTextLavender,
    secondary = LightSecondary,
    onSecondary = Color(0xFF1B1433),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextLavender,
    tertiary = StatusSuccess,
    background = DarkBackground,
    onBackground = DarkTextWhite,
    surface = DarkCard,
    onSurface = DarkTextWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextLavender,
    outline = DarkOutline,
    error = StatusError,
    onError = Color.White
)

private val ClipFreeLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE5FF),
    onPrimaryContainer = LightText,
    secondary = LightSecondary,
    onSecondary = LightText,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightText,
    tertiary = StatusSuccess,
    background = LightBackground,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = StatusError,
    onError = Color.White
)

val ClipFreeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ClipFreeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) ClipFreeDarkColorScheme else ClipFreeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClipFreeTypography,
        shapes = ClipFreeShapes,
        content = content
    )
}
