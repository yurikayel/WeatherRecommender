package com.example.weatherrecommender.theme

import androidx.compose.ui.graphics.Color

/**
 * Defines the custom color palette used across the application.
 * These colors follow a premium design language: a sky-blue brand hue over neutral,
 * slightly blue-tinted surfaces so light and dark modes feel deliberate rather than inverted.
 */

/** 
 * Brand Colors 
 * The primary blue hue used for key accents, active states, and primary actions.
 */
val PremiumPrimary = Color(0xFF8FCBFF)
val PremiumPrimaryDark = Color(0xFF0B6BC2)
val PremiumAccent = Color(0xFF9FB3C8)

/** 
 * Light Theme Containers 
 * Used for cards, sheets, and secondary surfaces in light mode. 
 */
val PremiumPrimaryContainerLight = Color(0xFFD3E9FF)
val PremiumOnPrimaryContainerLight = Color(0xFF063E70)
val PremiumSecondaryContainerLight = Color(0xFFDDE7F0)
val PremiumOnSecondaryContainerLight = Color(0xFF25384A)

/** 
 * Dark Theme Containers 
 * Used for cards, sheets, and secondary surfaces in dark mode. 
 */
val PremiumPrimaryContainerDark = Color(0xFF0A4E8C)
val PremiumOnPrimaryContainerDark = Color(0xFFD3E9FF)
val PremiumSecondaryContainerDark = Color(0xFF31445A)
val PremiumOnSecondaryContainerDark = Color(0xFFD5E3F1)

/** 
 * Light Theme Surfaces 
 * The main background and base layering colors for light mode.
 */
val PremiumBackgroundLight = Color(0xFFF4F7FA)
val PremiumSurfaceLight = Color(0xFFFFFFFF)
val PremiumSurfaceVariantLight = Color(0xFFE4EAF1)

/** 
 * Dark Theme Surfaces
 * Deep navy rather than flat gray for a premium night feel.
 */
val PremiumBackgroundDark = Color(0xFF0C1218)
val PremiumSurfaceDark = Color(0xFF141C24)
val PremiumSurfaceVariantDark = Color(0xFF1E2935)

/** 
 * Typography Colors
 * High contrast values for readability on base surfaces.
 */
val PremiumOnLightText = Color(0xFF17222D)
val PremiumOnDarkText = Color(0xFFE4EBF2)
val PremiumOnSurfaceVariantLight = Color(0xFF5A6876)
val PremiumOnSurfaceVariantDark = Color(0xFF9DACBC)

/** 
 * Borders and Dividers 
 * Used for subtle structural lines and outlines.
 */
val PremiumOutlineLight = Color(0xFFC3CDD8)
val PremiumOutlineDark = Color(0xFF3A4756)

/** 
 * Error States 
 * Semantic colors for communicating validation errors and destructive actions.
 */
val PremiumErrorLight = Color(0xFFBA1A1A)
val PremiumOnErrorLight = Color(0xFFFFFFFF)
val PremiumErrorContainerLight = Color(0xFFFFDAD6)
val PremiumOnErrorContainerLight = Color(0xFF410002)

val PremiumErrorDark = Color(0xFFFFB4AB)
val PremiumOnErrorDark = Color(0xFF690005)
val PremiumErrorContainerDark = Color(0xFF93000A)
val PremiumOnErrorContainerDark = Color(0xFFFFDAD6)

/** 
 * Weather Day-Chip Pastels (Light)
 * Soft tints used for unselected day selectors, correlating subtly with weather.
 */
val PastelSunnyLight = Color(0xFFFFF4CC)
val PastelRainLight = Color(0xFFD4DEED)
val PastelSnowLight = Color(0xFFE0EEF4)
val PastelThunderLight = Color(0xFFE4DDF0)

// Weather day-chip pastels (dark) — muted surface tints that keep onSurface readable
val PastelSunnyDark = Color(0xFF3A3420)
val PastelRainDark = Color(0xFF1E2A3C)
val PastelSnowDark = Color(0xFF1A2C34)
val PastelThunderDark = Color(0xFF2A2438)
