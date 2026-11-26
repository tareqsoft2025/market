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

/*
 * Palette taken from the qamariya (القمرية), the stained-glass arch windows of
 * Sana'a houses: deep indigo glass, emerald, ruby and saffron, set in white
 * gypsum on stone. Each colour has one job:
 *   Indigo  - the app itself (bars, main actions)
 *   Emerald - money received, collected, in stock
 *   Ruby    - debts, returns, deleting
 *   Saffron - things that need attention (pending, low stock)
 */
val Indigo = Color(0xFF24305E)
val IndigoDeep = Color(0xFF18203F)
val Emerald = Color(0xFF1F7A5A)
val Ruby = Color(0xFFB4374A)
val Saffron = Color(0xFFE3A13A)
val Stone = Color(0xFFF2F1EE)
val Gypsum = Color(0xFFFFFFFF)

// Names used across the screens.
val Green = Emerald
val DarkGreen = Color(0xFF145440)
val Danger = Ruby
val Warning = Color(0xFFB26A12)   // saffron darkened so text stays readable on white
val Info = Color(0xFF2F5C9E)

private val colors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2F3),
    onPrimaryContainer = IndigoDeep,
    secondary = Emerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5EDE3),
    onSecondaryContainer = DarkGreen,
    tertiary = Saffron,
    onTertiary = IndigoDeep,
    tertiaryContainer = Color(0xFFFBEBCF),
    error = Ruby,
    background = Stone,
    surface = Gypsum,
    surfaceVariant = Color(0xFFE9E8F0),
    onSurfaceVariant = Color(0xFF4A4E63),
    outline = Color(0xFFB9BBC8),
    outlineVariant = Color(0xFFDCDCE4),
)

/** IBM Plex Sans Arabic: designed for screens, clear Arabic and Latin digits side by side. */
val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

/** Receipt paper and ink, used by the checkout panel. */
val ReceiptPaper = Color(0xFFFFFDF8)
val ReceiptInk = IndigoDeep

/** Same type scale as Material, with the Plex face on every style. */
private val typography = Typography().run {
    fun TextStyle.plex() = copy(fontFamily = PlexArabic)
    Typography(
        displayLarge = displayLarge.plex(), displayMedium = displayMedium.plex(), displaySmall = displaySmall.plex(),
        headlineLarge = headlineLarge.plex(), headlineMedium = headlineMedium.plex(), headlineSmall = headlineSmall.plex(),
        titleLarge = titleLarge.plex().copy(fontWeight = FontWeight.Bold),
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
