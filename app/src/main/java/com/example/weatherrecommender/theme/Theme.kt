package com.example.weatherrecommender.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MdDarkPrimary,
    onPrimary = MdDarkOnPrimary,
    primaryContainer = MdDarkPrimaryContainer,
    onPrimaryContainer = MdDarkOnPrimaryContainer,
    inversePrimary = MdLightPrimary,
    secondary = MdDarkSecondary,
    onSecondary = MdDarkOnSecondary,
    secondaryContainer = MdDarkSecondaryContainer,
    onSecondaryContainer = MdDarkOnSecondaryContainer,
    tertiary = MdDarkTertiary,
    onTertiary = MdDarkOnTertiary,
    background = MdDarkSurface,
    onBackground = MdDarkOnSurface,
    surface = MdDarkSurface,
    onSurface = MdDarkOnSurface,
    surfaceVariant = MdDarkSurfaceVariant,
    onSurfaceVariant = MdDarkOnSurfaceVariant,
    surfaceTint = MdDarkPrimary,
    inverseSurface = MdDarkInverseSurface,
    inverseOnSurface = MdDarkInverseOnSurface,
    outline = MdDarkOutline,
    outlineVariant = MdDarkOutlineVariant,
    scrim = Color.Black,
    surfaceBright = MdDarkSurfaceBright,
    surfaceDim = MdDarkSurfaceDim,
    surfaceContainer = MdDarkSurfaceContainer,
    surfaceContainerHigh = MdDarkSurfaceContainerHigh,
    surfaceContainerHighest = MdDarkSurfaceContainerHighest,
    surfaceContainerLow = MdDarkSurfaceContainerLow,
    surfaceContainerLowest = MdDarkSurfaceContainerLowest,
    error = MdDarkError,
    onError = MdDarkOnError,
    errorContainer = MdDarkErrorContainer,
    onErrorContainer = MdDarkOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = MdLightPrimary,
    onPrimary = MdLightOnPrimary,
    primaryContainer = MdLightPrimaryContainer,
    onPrimaryContainer = MdLightOnPrimaryContainer,
    inversePrimary = MdDarkPrimary,
    secondary = MdLightSecondary,
    onSecondary = MdLightOnSecondary,
    secondaryContainer = MdLightSecondaryContainer,
    onSecondaryContainer = MdLightOnSecondaryContainer,
    tertiary = MdLightTertiary,
    onTertiary = MdLightOnTertiary,
    background = MdLightSurface,
    onBackground = MdLightOnSurface,
    surface = MdLightSurface,
    onSurface = MdLightOnSurface,
    surfaceVariant = MdLightSurfaceVariant,
    onSurfaceVariant = MdLightOnSurfaceVariant,
    surfaceTint = MdLightPrimary,
    inverseSurface = MdLightInverseSurface,
    inverseOnSurface = MdLightInverseOnSurface,
    outline = MdLightOutline,
    outlineVariant = MdLightOutlineVariant,
    scrim = Color.Black,
    surfaceBright = MdLightSurfaceBright,
    surfaceDim = MdLightSurfaceDim,
    surfaceContainer = MdLightSurfaceContainer,
    surfaceContainerHigh = MdLightSurfaceContainerHigh,
    surfaceContainerHighest = MdLightSurfaceContainerHighest,
    surfaceContainerLow = MdLightSurfaceContainerLow,
    surfaceContainerLowest = MdLightSurfaceContainerLowest,
    error = MdLightError,
    onError = MdLightOnError,
    errorContainer = MdLightErrorContainer,
    onErrorContainer = MdLightOnErrorContainer
)

/**
 * The main Compose Material 3 Theme for the application.
 * Adapts to system dark mode settings and keeps system bar icon contrast in sync.
 *
 * The activity draws edge-to-edge (see `MainActivity`), so system bars are transparent and no
 * deprecated `statusBarColor` handling is needed — only the icon appearance is toggled here.
 *
 * @param darkTheme Whether to use the dark color scheme.
 * @param dynamicColor Whether to use Android 12+ dynamic colors (disabled by default so home
 *   and detail share the same M3 tokens).
 * @param typography Typography to apply. Overridable so JVM-rendered tests (Paparazzi) can
 *   supply an explicit [Typography] instance.
 * @param content The composable content to apply the theme to.
 */
@Composable
fun WeatherRecommenderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    typography: androidx.compose.material3.Typography = Typography,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Safe cast: in Paparazzi/preview environments the context is not an Activity.
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
