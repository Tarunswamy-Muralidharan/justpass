package com.example.attendancewidgetlaudea.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlassDarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),            // Bright blue for glass
    onPrimary = Color(0xFF002E6B),
    primaryContainer = Color(0xFF1565C0).copy(alpha = 0.35f),
    onPrimaryContainer = Color(0xFFD4E8FF),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF003060),
    secondaryContainer = Color(0xFF1976D2).copy(alpha = 0.25f),
    onSecondaryContainer = Color(0xFFCDE5FF),
    tertiary = Color(0xFFA5D6A7),
    onTertiary = Color(0xFF003A00),
    tertiaryContainer = Color(0xFF388E3C).copy(alpha = 0.30f),
    onTertiaryContainer = Color(0xFFC8F5C8),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFFD32F2F).copy(alpha = 0.25f),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0A1628),
    onBackground = Color(0xFFE3E3E3),
    surface = Color.White.copy(alpha = 0.06f),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color.White.copy(alpha = 0.08f),
    onSurfaceVariant = Color(0xFFBEC8D4),
    outline = Color.White.copy(alpha = 0.15f),
    outlineVariant = Color.White.copy(alpha = 0.08f),
    inverseSurface = Color(0xFFE3E3E3),
    inverseOnSurface = Color(0xFF1A1C1E),
    surfaceTint = Color(0xFF64B5F6),
    surfaceContainerLowest = Color.White.copy(alpha = 0.03f),
    surfaceContainerLow = Color.White.copy(alpha = 0.05f),
    surfaceContainer = Color.White.copy(alpha = 0.07f),
    surfaceContainerHigh = Color.White.copy(alpha = 0.10f),
    surfaceContainerHighest = Color.White.copy(alpha = 0.14f)
)

private val GlassLightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1976D2).copy(alpha = 0.15f),
    onPrimaryContainer = Color(0xFF002C6B),
    secondary = Color(0xFF42A5F5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF64B5F6).copy(alpha = 0.20f),
    onSecondaryContainer = Color(0xFF002D5F),
    tertiary = Color(0xFF2E7D32),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4CAF50).copy(alpha = 0.15f),
    onTertiaryContainer = Color(0xFF003A00),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFF44336).copy(alpha = 0.12f),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFE8F0FE),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White.copy(alpha = 0.60f),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color.White.copy(alpha = 0.55f),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color.White.copy(alpha = 0.70f),
    outlineVariant = Color.White.copy(alpha = 0.40f),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    surfaceTint = Color(0xFF1565C0),
    surfaceContainerLowest = Color.White.copy(alpha = 0.40f),
    surfaceContainerLow = Color.White.copy(alpha = 0.50f),
    surfaceContainer = Color.White.copy(alpha = 0.55f),
    surfaceContainerHigh = Color.White.copy(alpha = 0.65f),
    surfaceContainerHighest = Color.White.copy(alpha = 0.75f)
)

@Composable
fun AttendanceWidgetLaudeaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Use custom glass color schemes instead of dynamic colors
    // This ensures the glass effect is consistent across all devices
    val colorScheme = if (darkTheme) GlassDarkColorScheme else GlassLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
