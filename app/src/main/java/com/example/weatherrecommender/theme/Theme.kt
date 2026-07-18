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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PremiumPrimary,
    onPrimary = PremiumBackgroundDark,
    primaryContainer = PremiumPrimaryContainerDark,
    onPrimaryContainer = PremiumOnPrimaryContainerDark,
    secondary = PremiumAccent,
    onSecondary = PremiumBackgroundDark,
    secondaryContainer = PremiumSecondaryContainerDark,
    onSecondaryContainer = PremiumOnSecondaryContainerDark,
    tertiary = PremiumPrimaryDark,
    onTertiary = PremiumOnDarkText,
    background = PremiumBackgroundDark,
    onBackground = PremiumOnDarkText,
    surface = PremiumSurfaceDark,
    onSurface = PremiumOnDarkText,
    surfaceVariant = PremiumSurfaceVariantDark,
    onSurfaceVariant = PremiumOnSurfaceVariantDark,
    outline = PremiumOutlineDark,
    outlineVariant = PremiumSurfaceVariantDark,
    error = PremiumErrorDark,
    onError = PremiumOnErrorDark,
    errorContainer = PremiumErrorContainerDark,
    onErrorContainer = PremiumOnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = PremiumPrimaryDark,
    onPrimary = PremiumSurfaceLight,
    primaryContainer = PremiumPrimaryContainerLight,
    onPrimaryContainer = PremiumOnPrimaryContainerLight,
    secondary = PremiumAccent,
    onSecondary = PremiumOnLightText,
    secondaryContainer = PremiumSecondaryContainerLight,
    onSecondaryContainer = PremiumOnSecondaryContainerLight,
    tertiary = PremiumPrimary,
    onTertiary = PremiumOnLightText,
    background = PremiumBackgroundLight,
    onBackground = PremiumOnLightText,
    surface = PremiumSurfaceLight,
    onSurface = PremiumOnLightText,
    surfaceVariant = PremiumSurfaceVariantLight,
    onSurfaceVariant = PremiumOnSurfaceVariantLight,
    outline = PremiumOutlineLight,
    outlineVariant = PremiumSurfaceVariantLight,
    error = PremiumErrorLight,
    onError = PremiumOnErrorLight,
    errorContainer = PremiumErrorContainerLight,
    onErrorContainer = PremiumOnErrorContainerLight
)

/**
 * The main Compose Material 3 Theme for the application.
 * Adapts to system dark mode settings and keeps system bar icon contrast in sync.
 *
 * The activity draws edge-to-edge (see `MainActivity`), so system bars are transparent and no
 * deprecated `statusBarColor` handling is needed — only the icon appearance is toggled here.
 *
 * @param darkTheme Whether to use the dark color scheme.
 * @param dynamicColor Whether to use Android 12+ dynamic colors (disabled by default to enforce premium branding).
 * @param typography Typography to apply. Overridable so JVM-rendered tests (Paparazzi) can supply
 *   system fonts — the default uses downloadable Google Fonts, whose fetcher thread requires a real
 *   Android runtime.
 * @param content The composable content to apply the theme to.
 */
@Composable
fun WeatherRecommenderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep dynamic disabled to show off our fun theme
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
