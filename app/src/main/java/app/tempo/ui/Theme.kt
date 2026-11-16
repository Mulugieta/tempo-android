package app.tempo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2347E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8FF),
    onPrimaryContainer = Color(0xFF131A2B),
    background = Color(0xFFEDF0F6),
    onBackground = Color(0xFF131A2B),
    surface = Color.White,
    onSurface = Color(0xFF131A2B),
    surfaceVariant = Color(0xFFE1E6F0),
    onSurfaceVariant = Color(0xFF566075),
    outline = Color(0xFFD8DEEA),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AA0FF),
    onPrimary = Color(0xFF0D1120),
    primaryContainer = Color(0xFF222C5C),
    onPrimaryContainer = Color(0xFFE8ECF6),
    background = Color(0xFF0D1120),
    onBackground = Color(0xFFE8ECF6),
    surface = Color(0xFF161B2E),
    onSurface = Color(0xFFE8ECF6),
    surfaceVariant = Color(0xFF252C45),
    onSurfaceVariant = Color(0xFF97A1B8),
    outline = Color(0xFF272E47),
    error = Color(0xFFFF8A80),
)

@Composable
fun TempoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
