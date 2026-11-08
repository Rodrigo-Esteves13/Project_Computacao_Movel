package pt.ismai.farol.ui.theme

import androidx.compose.ui.graphics.Color

val NightBlue = Color(0xFF0B1220)
val SlateBlue = Color(0xFF131C2E)
val Amber = Color(0xFFFFB300)
val Cyan = Color(0xFF38BDF8)
val StateCritical = Color(0xFFEF4444)
val StateWarning = Color(0xFFF97316)
val StateGood = Color(0xFF38BDF8)
val StateNeutral = Color(0xFF94A3B8)
val LightText = Color(0xFFE2E8F0)
val DarkTextOnLight = Color(0xFF0F172A)
val LightBackground = Color(0xFFF1F5F9)

/** State colors; dark variants for text/icons on a light background (contrast >= 4.5:1). */
class StateColors(val good: Color, val warning: Color, val critical: Color, val neutral: Color)

val DarkStateColors = StateColors(good = StateGood, warning = StateWarning, critical = StateCritical, neutral = StateNeutral)
val LightStateColors = StateColors(
    good = Color(0xFF0369A1),
    warning = Color(0xFFC2410C),
    critical = Color(0xFFB91C1C),
    neutral = Color(0xFF475569),
)
