package com.copyback.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 品牌色 */
private val Primary = Color(0xFF1565C0)
private val PrimaryVariant = Color(0xFF0D47A1)
private val Secondary = Color(0xFF26A69A)
private val SecondaryVariant = Color(0xFF00897B)
private val Background = Color(0xFFF5F5F5)
private val Surface = Color(0xFFFFFFFF)
private val Error = Color(0xFFD32F2F)

private val DarkPrimary = Color(0xFF90CAF9)
private val DarkPrimaryVariant = Color(0xFF64B5F6)
private val DarkSecondary = Color(0xFF80CBC4)
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    background = Background,
    onBackground = Color(0xFF212121),
    surface = Surface,
    onSurface = Color(0xFF212121),
    error = Error,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    secondary = DarkSecondary,
    onSecondary = Color(0xFF004D40),
    secondaryContainer = Color(0xFF00695C),
    background = DarkBackground,
    onBackground = Color(0xFFE0E0E0),
    surface = DarkSurface,
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFEF5350),
    onError = Color.Black,
)

@Composable
fun CopyBackTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
