package com.tareqsoft.market.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.tareqsoft.market.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }

val Green = Color(0xFF2E7D32)
val DarkGreen = Color(0xFF1B5E20)
val Danger = Color(0xFFC62828)
val Warning = Color(0xFFEF6C00)
val Info = Color(0xFF1565C0)

private val colors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = DarkGreen,
    secondary = Color(0xFF00695C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    tertiary = Info,
    error = Danger,
    background = Color(0xFFF7F9F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2EE),
)

/** App theme. Layout is always right-to-left (Arabic) regardless of device language. */
@Composable
fun MarketTheme(container: AppContainer, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalContainer provides container,
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
