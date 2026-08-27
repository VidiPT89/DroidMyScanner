package dev.ividi.myscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AccentOrange,
    onPrimary = OnBlack,
    secondary = AccentAmber,
    onSecondary = BackgroundBlack,
    background = BackgroundBlack,
    onBackground = OnBlack,
    surface = SurfaceBlack,
    onSurface = OnBlack,
    surfaceVariant = SurfaceBlackElevated,
    onSurfaceVariant = OnBlackMuted,
    error = ErrorRed
)

private val LightColors = lightColorScheme(
    primary = AccentOrangeLight,
    onPrimary = SurfaceLight,
    secondary = AccentAmberLight,
    onSecondary = SurfaceLight,
    background = BackgroundLight,
    onBackground = OnLight,
    surface = SurfaceLight,
    onSurface = OnLight,
    surfaceVariant = SurfaceLightElevated,
    onSurfaceVariant = OnLightMuted,
    error = ErrorRed
)

/**
 * App theme wrapper. Dynamic (Material You) color is intentionally never used so the
 * custom brand palette stays consistent across every device.
 */
@Composable
fun DroidMyScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
