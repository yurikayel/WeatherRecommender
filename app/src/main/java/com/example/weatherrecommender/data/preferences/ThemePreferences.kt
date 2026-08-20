package com.example.weatherrecommender.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences"
)

private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_THEME_SOURCE = stringPreferencesKey("theme_source")

/**
 * Persisted light/dark preference.
 *
 * `null` on [ThemePreferences.themeMode] means first launch (no key written yet).
 * First-run settling writes [ThemeMode.LIGHT] or [ThemeMode.DARK] from day/night at the
 * device location. An explicit [ThemeMode.SYSTEM] is stored as `"system"` so it is distinct
 * from unset — later choosing System still follows the OS.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Who last wrote [ThemeMode]. Clock-provisional first-run may be replaced by GPS once;
 * GPS and explicit user/system choices are sticky.
 */
enum class ThemeSource {
    CLOCK,
    GPS,
    USER
}

/** Resolves whether Compose should use the dark color scheme for a stored preference. */
fun ThemeMode.resolveDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Like [ThemeMode.resolveDarkTheme], with first-run unset falling back to [unsetIsNight]
 * (clock or solar) until [ThemePreferences] is written.
 */
fun ThemeMode?.resolveRenderedDarkTheme(
    systemInDarkTheme: Boolean,
    unsetIsNight: Boolean
): Boolean = when (this) {
    null -> unsetIsNight
    else -> resolveDarkTheme(systemInDarkTheme)
}

@Singleton
class ThemePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val themeMode: Flow<ThemeMode?> = context.themeDataStore.data.map { prefs ->
        prefs.storedThemeMode()
    }

    /** Current stored mode, or null on first launch. */
    suspend fun currentMode(): ThemeMode? = themeMode.first()

    /**
     * Who wrote the stored mode. Missing source with an existing mode is [ThemeSource.USER]
     * so upgrades do not re-open first-run GPS override.
     */
    suspend fun currentSource(): ThemeSource? {
        val prefs = context.themeDataStore.data.first()
        return prefs.storedThemeSource()
    }

    /**
     * Writes Light, Dark, or System. [source] defaults to [ThemeSource.USER] so the sun/moon
     * toggle and an explicit System choice cannot be overwritten by a later GPS fix.
     */
    suspend fun setThemeMode(mode: ThemeMode, source: ThemeSource = ThemeSource.USER) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.storageValue()
            prefs[KEY_THEME_SOURCE] = source.storageValue()
        }
    }

    /**
     * Flips between light and dark based on the currently rendered theme.
     * After the first toggle, the explicit choice replaces first-run / system-follow.
     */
    suspend fun toggle(currentlyDark: Boolean) {
        setThemeMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }
}

/** Storage token for a [ThemeMode]. */
private fun ThemeMode.storageValue(): String = when (this) {
    ThemeMode.SYSTEM -> "system"
    ThemeMode.LIGHT -> "light"
    ThemeMode.DARK -> "dark"
}

/** Storage token for a [ThemeSource]. */
private fun ThemeSource.storageValue(): String = when (this) {
    ThemeSource.CLOCK -> "clock"
    ThemeSource.GPS -> "gps"
    ThemeSource.USER -> "user"
}

/** Reads the stored theme enum, or null when the key has never been written. */
private fun Preferences.storedThemeMode(): ThemeMode? = when (this[KEY_THEME_MODE]) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    "system" -> ThemeMode.SYSTEM
    else -> null
}

/**
 * Reads who wrote the theme. A legacy row with a mode but no source is treated as user-set
 * so a later GPS fix cannot flip an already-chosen appearance.
 */
private fun Preferences.storedThemeSource(): ThemeSource? {
    val source = when (this[KEY_THEME_SOURCE]) {
        "clock" -> ThemeSource.CLOCK
        "gps" -> ThemeSource.GPS
        "user" -> ThemeSource.USER
        else -> null
    }
    if (source != null) return source
    return if (storedThemeMode() != null) ThemeSource.USER else null
}
