package pt.ismai.farol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import pt.ismai.farol.domain.model.ThemePref

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    secondary = Cyan,
    background = NightBlue,
    onBackground = LightText,
    surface = SlateBlue,
    onSurface = LightText,
    surfaceVariant = SlateBlue,
    onSurfaceVariant = StateNeutral,
    error = StateCritical,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF9A6700),
    onPrimary = Color.White,
    secondary = Color(0xFF0369A1),
    background = LightBackground,
    onBackground = DarkTextOnLight,
    surface = Color.White,
    onSurface = DarkTextOnLight,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFB91C1C),
)

/** State colors of the current theme (dark or light). */
val LocalStateColors = staticCompositionLocalOf { DarkStateColors }

/** Resolves the preference to dark/light; shared with MainActivity (system bars). */
@Composable
fun ThemePref.isDark(): Boolean = when (this) {
    ThemePref.SYSTEM -> isSystemInDarkTheme()
    ThemePref.DARK -> true
    ThemePref.LIGHT -> false
}

/** dynamicColor is turned off on purpose: keeps the Farol identity. */
@Composable
fun FarolTheme(theme: ThemePref = ThemePref.SYSTEM, content: @Composable () -> Unit) {
    val dark = theme.isDark()
    CompositionLocalProvider(LocalStateColors provides if (dark) DarkStateColors else LightStateColors) {
        MaterialTheme(colorScheme = if (dark) DarkColorScheme else LightColorScheme, content = content)
    }
}
