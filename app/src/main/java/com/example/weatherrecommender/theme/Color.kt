package com.example.weatherrecommender.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 color roles for light and dark schemes.
 *
 * Dark surfaces follow the baseline M3 near-black stack (`#1C1B1F` and tonal
 * `surfaceContainer*` layers) so elevation comes from tone, not navy slabs or
 * heavy strokes. Primary stays a light blue/lavender accent for selected chips.
 * Light uses the matching M3 surface roles so theme toggles feel like one app.
 *
 * Flyer-only aliases at the bottom keep the share PNG on a fixed light palette.
 */

// Dark — M3 surface + blue primary (Google Calendar / Clock chip accent)
val MdDarkPrimary = Color(0xFF8FCBFF)
val MdDarkOnPrimary = Color(0xFF1C1B1F)
val MdDarkPrimaryContainer = Color(0xFF004A77)
val MdDarkOnPrimaryContainer = Color(0xFFD1E4FF)
val MdDarkSecondary = Color(0xFFB8C8DB)
val MdDarkOnSecondary = Color(0xFF233140)
val MdDarkSecondaryContainer = Color(0xFF394757)
val MdDarkOnSecondaryContainer = Color(0xFFD4E4F7)
val MdDarkTertiary = Color(0xFFD0BCFF)
val MdDarkOnTertiary = Color(0xFF381E72)
val MdDarkSurface = Color(0xFF1C1B1F)
val MdDarkOnSurface = Color(0xFFE6E1E5)
val MdDarkSurfaceVariant = Color(0xFF49454F)
val MdDarkOnSurfaceVariant = Color(0xFFCAC4D0)
val MdDarkOutline = Color(0xFF938F99)
val MdDarkOutlineVariant = Color(0xFF49454F)
val MdDarkSurfaceDim = Color(0xFF141218)
val MdDarkSurfaceBright = Color(0xFF3B383E)
val MdDarkSurfaceContainerLowest = Color(0xFF0F0D13)
val MdDarkSurfaceContainerLow = Color(0xFF1D1B20)
val MdDarkSurfaceContainer = Color(0xFF211F26)
val MdDarkSurfaceContainerHigh = Color(0xFF2B2930)
val MdDarkSurfaceContainerHighest = Color(0xFF36343B)
val MdDarkInverseSurface = Color(0xFFE6E1E5)
val MdDarkInverseOnSurface = Color(0xFF313033)

val MdDarkError = Color(0xFFFFB4AB)
val MdDarkOnError = Color(0xFF690005)
val MdDarkErrorContainer = Color(0xFF93000A)
val MdDarkOnErrorContainer = Color(0xFFFFDAD6)

// Light — M3 surface roles with the same blue seed as dark
val MdLightPrimary = Color(0xFF0B6BC2)
val MdLightOnPrimary = Color(0xFFFFFFFF)
val MdLightPrimaryContainer = Color(0xFFD3E9FF)
val MdLightOnPrimaryContainer = Color(0xFF063E70)
val MdLightSecondary = Color(0xFF535F70)
val MdLightOnSecondary = Color(0xFFFFFFFF)
val MdLightSecondaryContainer = Color(0xFFDDE7F0)
val MdLightOnSecondaryContainer = Color(0xFF25384A)
val MdLightTertiary = Color(0xFF6750A4)
val MdLightOnTertiary = Color(0xFFFFFFFF)
val MdLightSurface = Color(0xFFFFFBFE)
val MdLightOnSurface = Color(0xFF1C1B1F)
val MdLightSurfaceVariant = Color(0xFFE7E0EC)
val MdLightOnSurfaceVariant = Color(0xFF49454F)
val MdLightOutline = Color(0xFF79747E)
val MdLightOutlineVariant = Color(0xFFCAC4D0)
val MdLightSurfaceDim = Color(0xFFDED8E1)
val MdLightSurfaceBright = Color(0xFFFFFBFE)
val MdLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val MdLightSurfaceContainerLow = Color(0xFFF7F2FA)
val MdLightSurfaceContainer = Color(0xFFF3EDF7)
val MdLightSurfaceContainerHigh = Color(0xFFECE6F0)
val MdLightSurfaceContainerHighest = Color(0xFFE6E0E9)
val MdLightInverseSurface = Color(0xFF313033)
val MdLightInverseOnSurface = Color(0xFFF4EFF4)

val MdLightError = Color(0xFFBA1A1A)
val MdLightOnError = Color(0xFFFFFFFF)
val MdLightErrorContainer = Color(0xFFFFDAD6)
val MdLightOnErrorContainer = Color(0xFF410002)

/** Weather day-chip pastels (light) — used by the share flyer’s 7-day strip. */
val PastelSunnyLight = Color(0xFFFFF4CC)
val PastelRainLight = Color(0xFFD4DEED)
val PastelSnowLight = Color(0xFFE0EEF4)
val PastelThunderLight = Color(0xFFE4DDF0)

val PastelSunnyDark = Color(0xFF3A3420)
val PastelRainDark = Color(0xFF1E2A3C)
val PastelSnowDark = Color(0xFF1A2C34)
val PastelThunderDark = Color(0xFF2A2438)

// Share flyer keeps a fixed light palette so exported images do not follow theme.
val PremiumPrimary = MdDarkPrimary
val PremiumPrimaryDark = MdLightPrimary
val PremiumAccent = Color(0xFF9FB3C8)
val PremiumPrimaryContainerLight = MdLightPrimaryContainer
val PremiumSurfaceLight = Color(0xFFFFFFFF)
val PremiumSurfaceVariantLight = Color(0xFFE4EAF1)
val PremiumOnLightText = Color(0xFF17222D)
val PremiumOnSurfaceVariantLight = Color(0xFF5A6876)
val PremiumErrorLight = MdLightError
