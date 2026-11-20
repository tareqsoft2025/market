package com.tareqsoft.market.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tareqsoft.market.R
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

/** IBM Plex Sans Arabic: designed for screens, clear Arabic and Latin digits side by side. */
val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

/** Receipt paper and ink, used by the checkout panel. */
val ReceiptPaper = Color(0xFFFFFCF3)
val ReceiptInk = Color(0xFF14361F)

/** Same type scale as Material, with the Plex face on every style. */
private val typography = Typography().run {
    fun TextStyle.plex() = copy(fontFamily = PlexArabic)
    Typography(
        displayLarge = displayLarge.plex(), displayMedium = displayMedium.plex(), displaySmall = displaySmall.plex(),
        headlineLarge = headlineLarge.plex(), headlineMedium = headlineMedium.plex(), headlineSmall = headlineSmall.plex(),
        titleLarge = titleLarge.plex().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.plex().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.plex(),
        bodyLarge = bodyLarge.plex(), bodyMedium = bodyMedium.plex(), bodySmall = bodySmall.plex(),
        labelLarge = labelLarge.plex().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.plex(), labelSmall = labelSmall.plex(),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

/** App theme. Layout is always right-to-left (Arabic) regardless of device language. */
@Composable
fun MarketTheme(container: AppContainer, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalContainer provides container,
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
    }
}
