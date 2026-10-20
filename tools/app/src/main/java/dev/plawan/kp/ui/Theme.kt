package dev.plawan.kp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Semantic accents used across the app (independent of the M3 roles). */
object KpColors {
    val Good = Color(0xFF3DBE8B)
    val Bad = Color(0xFFE5646A)
    val Gold = Color(0xFFD9A93E)
    val Indigo = Color(0xFF8A93F7)
}

private val Dark = darkColorScheme(
    primary = Color(0xFFE7C26B), onPrimary = Color(0xFF241A00),
    secondary = Color(0xFF9AA5FF), onSecondary = Color(0xFF101436),
    tertiary = Color(0xFF6FD6B0),
    background = Color(0xFF0A0D18), onBackground = Color(0xFFE6E8F2),
    surface = Color(0xFF121627), onSurface = Color(0xFFE6E8F2),
    surfaceVariant = Color(0xFF1B2135), onSurfaceVariant = Color(0xFF9BA3C4),
    surfaceContainerLowest = Color(0xFF0C1020), surfaceContainerLow = Color(0xFF10142A),
    surfaceContainer = Color(0xFF141930), surfaceContainerHigh = Color(0xFF1A203A),
    surfaceContainerHighest = Color(0xFF212848),
    outline = Color(0xFF2C3350), outlineVariant = Color(0xFF232A44), error = Color(0xFFE5646A),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A6200), onPrimary = Color.White,
    secondary = Color(0xFF4B57C9), onSecondary = Color.White,
    tertiary = Color(0xFF1F7A5E),
    background = Color(0xFFF7F5EF), onBackground = Color(0xFF171A28),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF171A28),
    surfaceVariant = Color(0xFFF0ECE0), onSurfaceVariant = Color(0xFF5A5E74),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFBF9F4),
    surfaceContainer = Color(0xFFF5F2EA), surfaceContainerHigh = Color(0xFFEFEBE0),
    surfaceContainerHighest = Color(0xFFE9E4D6),
    outline = Color(0xFFD3CCBA), outlineVariant = Color(0xFFE6E1D4), error = Color(0xFFB3261E),
)

private val KpShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun KpTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) Dark else Light,
    shapes = KpShapes,
    content = content,
)

fun scoreColor(score: Int): Color = when {
    score >= 60 -> KpColors.Good; score >= 40 -> KpColors.Gold; else -> KpColors.Bad
}
